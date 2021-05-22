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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import static java.util.stream.Collectors.toList;

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
      generateMatcherInterfaceAndCall(gadtTypeBuilder, dataTypeContainer);
      generateFluentMatchingInterfaceAndCall(gadtTypeBuilder, dataTypeContainer);
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

  private static void generateMatcherInterfaceAndCall(
      TypeSpec.Builder gadtTypeBuilder, DataTypeContainer dataTypeContainer) {
    // Generate matcher interface
    TypeVariableName returnTypeVariable = TypeVariableName.get("Return");
    TypeSpec.Builder matcherTypeBuilder =
        TypeSpec.interfaceBuilder("Matcher").addModifiers(Modifier.PUBLIC);

    List<TypeVariableName> genericTypeVariableNames =
        dataTypeContainer.genericTypes().stream().map(TypeVariableName::get).collect(toList());

    for (TypeVariableName genericType : genericTypeVariableNames) {
      matcherTypeBuilder.addTypeVariable(genericType);
    }
    matcherTypeBuilder.addTypeVariable(returnTypeVariable);

    ClassName matcherClassName =
        ClassName.get(dataTypeContainer.packageName(), dataTypeContainer.name() + ".Matcher");

    for (DataType dataType : dataTypeContainer.dataTypes()) {
      matcherTypeBuilder.addMethod(
          MethodSpec.methodBuilder(camelCase(dataType.name()))
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .addParameter(typeNameFor(dataTypeContainer, dataType), camelCase(dataType.name()))
              .returns(returnTypeVariable)
              .build());
    }

    TypeSpec matcherType = matcherTypeBuilder.build();
    gadtTypeBuilder.addType(matcherType);

    // add Matcher match method
    List<TypeVariableName> matcherTypes = new ArrayList<>(genericTypeVariableNames);
    matcherTypes.add(returnTypeVariable);

    ParameterizedTypeName matcherWithReturnType =
        ParameterizedTypeName.get(
            matcherClassName, matcherTypes.toArray(new TypeVariableName[] {}));

    MethodSpec.Builder matcherBuilder =
        MethodSpec.methodBuilder("match")
            .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
            .addTypeVariable(returnTypeVariable)
            .addParameter(matcherWithReturnType, "matcher");
    for (DataType dataType : dataTypeContainer.dataTypes()) {
      TypeName typeName = typeNameFor(dataTypeContainer, dataType);
      ClassName className = classNameFor(dataTypeContainer, dataType);
      matcherBuilder.beginControlFlow("if (this instanceof $T)", className);
      matcherBuilder.addStatement(
          "return matcher.$L(($T) this)", camelCase(dataType.name()), typeName);
      matcherBuilder.endControlFlow();
    }
    matcherBuilder.addStatement(
        "throw new $T(\"Unexpected $L subclass encountered.\")",
        IllegalStateException.class,
        dataTypeContainer.name());
    matcherBuilder.returns(returnTypeVariable).build();

    gadtTypeBuilder.addMethod(matcherBuilder.build());
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

  private static void generateFluentMatchingInterfaceAndCall(
      TypeSpec.Builder gadtTypeBuilder, DataTypeContainer dataTypeContainer) {
    TypeVariableName returnTypeVariable = TypeVariableName.get("Return");
    TypeSpec.Builder matchingTypeBuilder =
        TypeSpec.classBuilder("Matching")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);

    List<TypeVariableName> genericTypeVariableNames =
        dataTypeContainer.genericTypes().stream().map(TypeVariableName::get).collect(toList());

    for (TypeVariableName genericType : genericTypeVariableNames) {
      matchingTypeBuilder.addTypeVariable(genericType);
    }
    matchingTypeBuilder.addTypeVariable(returnTypeVariable);

    ClassName gadtClassName =
        ClassName.get(dataTypeContainer.packageName(), dataTypeContainer.name());
    matchingTypeBuilder.addField(gadtClassName, "value", Modifier.PRIVATE);
    matchingTypeBuilder.addField(returnTypeVariable, "returnValue", Modifier.PRIVATE);
    matchingTypeBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(gadtClassName, "value", Modifier.FINAL)
            .addStatement("this.value = value")
            .build());

    ClassName matchingClassName =
        ClassName.get(dataTypeContainer.packageName(), dataTypeContainer.name() + ".Matching");

    // TODO pulling out generic type variable names is becoming common, extract.
    List<TypeVariableName> matcherTypes = new ArrayList<>(genericTypeVariableNames);
    matcherTypes.add(returnTypeVariable);

    ParameterizedTypeName matchingWithReturnType =
        ParameterizedTypeName.get(
            matchingClassName, matcherTypes.toArray(new TypeVariableName[] {}));

    gadtTypeBuilder.addMethod(
        MethodSpec.methodBuilder("matching")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(returnTypeVariable)
            .returns(matchingWithReturnType)
            .addStatement("return new $T(this)", matchingWithReturnType)
            .build());

    for (DataType dataType : dataTypeContainer.dataTypes()) {
      TypeName dataTypeClassName = classNameFor(dataTypeContainer, dataType);
      TypeName dataTypeTypeName = typeNameFor(dataTypeContainer, dataType);
      ParameterizedTypeName function =
          ParameterizedTypeName.get(
              ClassName.get(Function.class), dataTypeTypeName, returnTypeVariable);

      matchingTypeBuilder.addMethod(
          MethodSpec.methodBuilder(dataType.name())
              .addModifiers(Modifier.PUBLIC)
              .addParameter(function, "fn", Modifier.FINAL)
              .returns(matchingWithReturnType)
              .beginControlFlow("if (this.value instanceof $T)", dataTypeClassName)
              .addStatement("this.returnValue = fn.apply(($T) this.value)", dataTypeTypeName)
              .endControlFlow()
              .addStatement("return this")
              .build());
    }

    matchingTypeBuilder.addMethod(
        MethodSpec.methodBuilder("isMatched")
            .addModifiers(Modifier.PUBLIC)
            .returns(boolean.class)
            .addStatement("return this.returnValue != null")
            .build());

    ParameterizedTypeName optionalReturnType =
        ParameterizedTypeName.get(ClassName.get(Optional.class), returnTypeVariable);

    // Add exception throwing partial matching result
    matchingTypeBuilder.addMethod(
        MethodSpec.methodBuilder("get")
            .addModifiers(Modifier.PUBLIC)
            .returns(returnTypeVariable)
            .beginControlFlow("if (this.returnValue != null)")
            .addStatement("return this.returnValue")
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement(
                "throw new $T(\"Unmatched value: \" + this.value)", IllegalStateException.class)
            .endControlFlow()
            .build());

    // Add partial matching result
    matchingTypeBuilder.addMethod(
        MethodSpec.methodBuilder("find")
            .addModifiers(Modifier.PUBLIC)
            .returns(optionalReturnType)
            .addStatement("return $T.ofNullable(this.returnValue)", Optional.class)
            .build());
    // Add total matching result
    matchingTypeBuilder.addMethod(
        MethodSpec.methodBuilder("orElse")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(returnTypeVariable, "returnValue", Modifier.FINAL)
            .returns(returnTypeVariable)
            .beginControlFlow("if (this.returnValue != null)")
            .addStatement("return this.returnValue")
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement("return returnValue")
            .endControlFlow()
            .build());

    TypeSpec matchingType = matchingTypeBuilder.build();
    gadtTypeBuilder.addType(matchingType);
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

  private static ClassName classNameFor(DataTypeContainer dataTypeContainer, DataType dataType) {
    return ClassName.get(
        dataTypeContainer.packageName(), dataTypeContainer.name() + "." + dataType.name());
  }

  private static TypeName typeNameFor(DataTypeContainer dataTypeContainer, DataType dataType) {

    ClassName className =
        ClassName.get(
            dataTypeContainer.packageName(), dataTypeContainer.name() + "." + dataType.name());

    if (dataTypeContainer.genericTypes().isEmpty()) {
      return className;

    } else {

      TypeVariableName[] typeNames = new TypeVariableName[dataTypeContainer.genericTypes().size()];
      for (int i = 0; i < dataTypeContainer.genericTypes().size(); i++) {
        typeNames[i] = TypeVariableName.get(dataTypeContainer.genericTypes().get(i));
      }
      return ParameterizedTypeName.get(className, typeNames);
    }
  }

  // Convert to camelCase unless ALLCAPS
  private static String camelCase(String name) {
    if (name.toUpperCase().equals(name)) {
      return name;
    } else {
      return name.length() == 1
          ? name.toLowerCase()
          : name.substring(0, 1).toLowerCase() + name.substring(1);
    }
  }
}
