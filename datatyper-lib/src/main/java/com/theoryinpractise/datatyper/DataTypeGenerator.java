package com.theoryinpractise.datatyper;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.theoryinpractise.datatyper.model.DataType;
import com.theoryinpractise.datatyper.model.Field;
import com.theoryinpractise.datatyper.model.DataTypeContainer;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import static java.util.stream.Collectors.toList;
import static com.theoryinpractise.datatyper.Support.camelCase;
import static com.theoryinpractise.datatyper.Support.classNameFor;
import static com.theoryinpractise.datatyper.Support.typeNameFor;

/** Created by amrk on 2/08/15. */
public class DataTypeGenerator {

  public static JavaFile generateJavaForTypeContainer(DataTypeContainer dataTypeContainer)
      throws IOException {

    ClassName gadtClassName =
        ClassName.get(dataTypeContainer.packageName(), dataTypeContainer.name());
    ClassName autoValue = ClassName.get("com.google.auto.value", "AutoValue");

    // top level gadt class
    TypeSpec.Builder gadtTypeBuilder =
        TypeSpec.classBuilder(dataTypeContainer.name())
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

    for (String genericType : dataTypeContainer.genericTypes()) {
      gadtTypeBuilder.addTypeVariable(TypeVariableName.get(genericType));
    }

    List<TypeVariableName> genericTypeVariableNames =
        dataTypeContainer.genericTypes().stream().map(TypeVariableName::get).collect(toList());

    TypeName gadtTypeName;
    if (genericTypeVariableNames.isEmpty()) {
      gadtTypeName = gadtClassName;
    } else {
      gadtTypeName =
          ParameterizedTypeName.get(
              ClassName.bestGuess(dataTypeContainer.name()),
              genericTypeVariableNames.toArray(new TypeVariableName[] {}));
    }

    for (String implementsClass : dataTypeContainer.implememts()) {
      gadtTypeBuilder.addSuperinterface(resolveClassNameFor(dataTypeContainer, implementsClass));
    }

    gadtTypeBuilder.addMethod(
        MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    for (DataType dataType : dataTypeContainer.dataTypes()) {

      ClassName dataTypeInstanceClassName =
          ClassName.get(
              dataTypeContainer.packageName(),
              "AutoValue_" + dataTypeContainer.name() + "_" + dataType.name());
      TypeName rawDataTypeInterfaceName = classNameFor(dataTypeContainer, dataType);
      TypeName dataTypeInterfaceName = typeNameFor(dataTypeContainer, dataType);

      // Create data-type constructor/factory method for each data type
      MethodSpec.Builder dataTypeConstuctorBuilder =
          MethodSpec.methodBuilder(camelCase(dataType.name()))
              .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
              .returns(dataTypeInterfaceName);

      // Create data type itself
      TypeSpec.Builder dataTypeBuilder =
          TypeSpec.classBuilder(dataType.name())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT, Modifier.STATIC)
              .addAnnotation(autoValue)
              .superclass(gadtTypeName);

      // For each field, if it's a generic type, add that to the declaration(s)
      Set<String> usedGenericTypes = new HashSet<>();
      for (String genericType : dataTypeContainer.genericTypes()) {
        if (!usedGenericTypes.contains(genericType)) {
          TypeVariableName fieldVariable = TypeVariableName.get(genericType);
          dataTypeConstuctorBuilder.addTypeVariable(fieldVariable);
          dataTypeBuilder.addTypeVariable(fieldVariable);
          usedGenericTypes.add(genericType);
        }
      }

      // For each field, add an argument to the constructor method, and a field to the class.
      for (Field field : dataType.fields()) {
        TypeName argType = resolveClassNameFor(dataTypeContainer, field.type());

        dataTypeConstuctorBuilder.addParameter(argType, camelCase(field.name()));

        dataTypeBuilder.addMethod(
            MethodSpec.methodBuilder(camelCase(field.name()))
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(argType)
                .build());
      }

      TypeSpec dataTypeSpec = dataTypeBuilder.build();
      gadtTypeBuilder.addType(dataTypeSpec);

      // Handle singleton?
      if (dataType.fields().isEmpty()) {
        // declare singleton
        gadtTypeBuilder.addField(
            FieldSpec.builder(
                    rawDataTypeInterfaceName,
                    dataType.name(),
                    Modifier.PRIVATE,
                    Modifier.STATIC,
                    Modifier.FINAL)
                .initializer("new $T()", dataTypeInstanceClassName)
                .build());

        // generate accessor method
        MethodSpec dataTypeConstuctor =
            dataTypeConstuctorBuilder
                .addStatement("return $L", dataType.name())
                .returns(dataTypeInterfaceName)
                .build();

        gadtTypeBuilder.addMethod(dataTypeConstuctor);

      } else {

        MethodSpec dataTypeConstuctor =
            dataTypeConstuctorBuilder
                .addStatement(
                    "return new $T($L)",
                    dataTypeInstanceClassName,
                    String.join(", ", liftFieldNames(dataType)))
                .returns(dataTypeInterfaceName)
                .build();

        gadtTypeBuilder.addMethod(dataTypeConstuctor);
      }
    }

    if (dataTypeContainer.dataTypes().size() > 1) {
      MatcherGenerator.generateMatcherInterfaceAndCall(gadtTypeBuilder, dataTypeContainer);
      MatcherGenerator.generateFluentMatcherInterfaceAndCall(gadtTypeBuilder, dataTypeContainer);
      AccepterGenerator.generateAccepterInterfaceAndCall(gadtTypeBuilder, dataTypeContainer);
      AccepterGenerator.generateFluentAcceptingInterfaceAndCall(gadtTypeBuilder, dataTypeContainer);
      generateLambdaMatchingInterfaceAndCall(gadtTypeBuilder, dataTypeContainer);
    }

    TypeSpec gadtType = gadtTypeBuilder.build();

    return JavaFile.builder(dataTypeContainer.packageName(), gadtType)
        .skipJavaLangImports(true)
        .build();
  }

  private static List<String> liftFieldNames(DataType dataType) {
    List<String> fieldNames = new ArrayList<>();
    for (Field field : dataType.fields()) {
      fieldNames.add(field.name());
    }
    return fieldNames;
  }

  private static void generateLambdaMatchingInterfaceAndCall(
      TypeSpec.Builder gadtTypeBuilder, DataTypeContainer dataTypeContainer) {
    TypeVariableName returnTypeVariable = TypeVariableName.get("Return");

    MethodSpec.Builder lambdaMethod =
        MethodSpec.methodBuilder("matching")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(returnTypeVariable)
            .returns(returnTypeVariable);

    dataTypeContainer
        .dataTypes()
        .forEach(
            dataType -> {
              TypeName dataTypeClassName = classNameFor(dataTypeContainer, dataType);
              TypeName dataTypeTypeName = typeNameFor(dataTypeContainer, dataType);
              ParameterizedTypeName function =
                  ParameterizedTypeName.get(
                      ClassName.get(Function.class), dataTypeTypeName, returnTypeVariable);
              String funtionArgName = camelCase(dataType.name());

              lambdaMethod.addParameter(function, funtionArgName);

              lambdaMethod.beginControlFlow("if (this instanceof $T)", dataTypeClassName);
              lambdaMethod.addStatement(
                  "return $L.apply(($T) this)", funtionArgName, dataTypeTypeName);
              lambdaMethod.endControlFlow();
            });

    lambdaMethod.addStatement(
        "throw new $T(\"Unexpected $L subclass encountered.\")",
        IllegalStateException.class,
        dataTypeContainer.name());

    gadtTypeBuilder.addMethod(lambdaMethod.build());
  }

  public static String expandImport(List<String> imports, String value) {
    String expanded = value;
    for (String importVal : imports) {
      String localType = importVal.substring(importVal.lastIndexOf(".") + 1);
      expanded = expanded.replaceAll(localType, importVal);
    }
    return expanded;
  }

  private static TypeName resolveClassNameFor(
      DataTypeContainer dataTypeContainer, String classReference) {

    // generic type
    if (dataTypeContainer.genericTypes().contains(classReference)) {
      return TypeVariableName.get(classReference);
    }

    // test for generics - do a half arsed attempt as wacky resolution with javapoet
    if (classReference.contains("<")) {
      String expandedClassReference = expandImport(dataTypeContainer.imports(), classReference);
      String[] types = expandedClassReference.replaceAll("[<|>|,]", " ").split(" ");
      ClassName baseClassName = ClassName.bestGuess(types[0]);
      ClassName[] typeArgs = new ClassName[types.length - 1];
      for (int i = 1; i < types.length; i++) {
        typeArgs[i - 1] = ClassName.bestGuess(types[i]);
      }
      return ParameterizedTypeName.get(baseClassName, typeArgs);
    }

    // if full class, just guess
    if (classReference.contains(".")) {
      return ClassName.bestGuess(classReference);
    }

    // else find imported class
    for (String importedClass : dataTypeContainer.imports()) {
      if (importedClass.endsWith(classReference)) {
        return ClassName.bestGuess(importedClass);
      }
    }

    // standard class?
    try {
      Class.forName("java.lang." + classReference);
      return ClassName.get("java.lang", classReference);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Undeclared class: " + classReference);
    }
  }
}
