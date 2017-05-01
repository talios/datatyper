package com.theoryinpractise.datatyper;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.theoryinpractise.datatyper.model.DataType;
import com.theoryinpractise.datatyper.model.Field;
import com.theoryinpractise.datatyper.model.DataSetSet;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Created by amrk on 2/08/15. */
public class DataTypeGenerator {

  public static JavaFile generateJavaForGadt(DataSetSet dataSetSet) throws IOException {

    ClassName gadtClassName = ClassName.get(dataSetSet.packageName(), dataSetSet.name());
    ClassName autoValue = ClassName.get("com.google.auto.value", "AutoValue");

    // top level gadt class
    TypeSpec.Builder gadtTypeBuilder =
        TypeSpec.classBuilder(dataSetSet.name()).addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

    for (String implementsClass : dataSetSet.implememts()) {
      gadtTypeBuilder.addSuperinterface(resolveClassNameFor(dataSetSet, implementsClass));
    }

    gadtTypeBuilder.addMethod(
        MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    for (DataType dataType : dataSetSet.dataTypes()) {

      final ClassName dataTypeInstanceClassName =
          ClassName.get(
              dataSetSet.packageName(), "AutoValue_" + dataSetSet.name() + "_" + dataType.name());
      final ClassName dataTypeInterfaceName = classNameFor(dataSetSet, dataType);

      // Create data-type constructor/factory method for each data type
      MethodSpec.Builder dataTypeConstuctorBuilder =
          MethodSpec.methodBuilder(dataType.name())
              .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
              .returns(dataTypeInterfaceName);

      // Create data type itself
      TypeSpec.Builder dataTypeBuilder =
          TypeSpec.classBuilder(dataType.name())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT, Modifier.STATIC)
              .addAnnotation(autoValue)
              .superclass(gadtClassName);

      // For each field, add an argument to the constructor method, and a field to the class.
      for (Field field : dataType.fields()) {
        ClassName argClass = resolveClassNameFor(dataSetSet, field.type());

        dataTypeConstuctorBuilder.addParameter(argClass, field.name(), Modifier.FINAL);

        dataTypeBuilder.addMethod(
            MethodSpec.methodBuilder(field.name())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .returns(argClass)
                .build());
      }

      TypeSpec dataTypeSpec = dataTypeBuilder.build();
      gadtTypeBuilder.addType(dataTypeSpec);

      // Handle singleton?
      if (dataType.fields().isEmpty()) {
        // declare singleton
        gadtTypeBuilder.addField(
            FieldSpec.builder(
                    dataTypeInterfaceName,
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

    generateMatcherInterfaceAndCall(gadtTypeBuilder, dataSetSet);
    generateFluentMatchingInterfaceAndCall(gadtTypeBuilder, dataSetSet);

    TypeSpec gadtType = gadtTypeBuilder.build();

    return JavaFile.builder(dataSetSet.packageName(), gadtType).build();
  }

  private static List<String> liftFieldNames(DataType dataType) {
    List<String> fieldNames = new ArrayList<>();
    for (Field field : dataType.fields()) {
      fieldNames.add(field.name());
    }
    return fieldNames;
  }

  private static void generateMatcherInterfaceAndCall(
      TypeSpec.Builder gadtTypeBuilder, DataSetSet dataSetSet) {
    // Generate matcher interface
    final TypeVariableName returnTypeVariable = TypeVariableName.get("Return");
    TypeSpec.Builder matcherTypeBuilder =
        TypeSpec.interfaceBuilder("Matcher")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(returnTypeVariable);
    ClassName matcherClassName =
        ClassName.get(dataSetSet.packageName(), dataSetSet.name() + ".Matcher");

    for (DataType dataType : dataSetSet.dataTypes()) {
      matcherTypeBuilder.addMethod(
          MethodSpec.methodBuilder(dataType.name())
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .addParameter(classNameFor(dataSetSet, dataType), dataType.name())
              .returns(returnTypeVariable)
              .build());
    }

    final TypeSpec matcherType = matcherTypeBuilder.build();
    gadtTypeBuilder.addType(matcherType);

    // add Matcher match method
    ParameterizedTypeName matcherWithReturnType =
        ParameterizedTypeName.get(matcherClassName, returnTypeVariable);
    final MethodSpec.Builder matcherBuilder =
        MethodSpec.methodBuilder("match")
            .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
            .addTypeVariable(returnTypeVariable)
            .addParameter(matcherWithReturnType, "matcher");
    for (DataType dataType : dataSetSet.dataTypes()) {
      final ClassName className = classNameFor(dataSetSet, dataType);
      matcherBuilder.beginControlFlow("if (this instanceof $T)", className);
      matcherBuilder.addStatement("return matcher.$L(($T) this)", dataType.name(), className);
      matcherBuilder.endControlFlow();
    }
    matcherBuilder.addStatement(
        "throw new $T(\"Unexpected $L subclass encountered.\")",
        IllegalStateException.class,
        dataSetSet.name());
    matcherBuilder.returns(returnTypeVariable).build();

    gadtTypeBuilder.addMethod(matcherBuilder.build());
  }

  private static void generateFluentMatchingInterfaceAndCall(
      TypeSpec.Builder gadtTypeBuilder, DataSetSet dataSetSet) {
    final TypeVariableName returnTypeVariable = TypeVariableName.get("Return");
    TypeSpec.Builder matchingTypeBuilder =
        TypeSpec.classBuilder("Matching")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
            .addTypeVariable(returnTypeVariable);

    ClassName gadtClassName = ClassName.get(dataSetSet.packageName(), dataSetSet.name());
    matchingTypeBuilder.addField(gadtClassName, "value", Modifier.PRIVATE);
    matchingTypeBuilder.addField(returnTypeVariable, "returnValue", Modifier.PRIVATE);
    matchingTypeBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(gadtClassName, "value", Modifier.FINAL)
            .addStatement("this.value = value")
            .build());

    ClassName matchingClassName =
        ClassName.get(dataSetSet.packageName(), dataSetSet.name() + ".Matching");

    ParameterizedTypeName matchingWithReturnType =
        ParameterizedTypeName.get(matchingClassName, returnTypeVariable);

    gadtTypeBuilder.addMethod(
        MethodSpec.methodBuilder("matching")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(returnTypeVariable)
            .returns(matchingWithReturnType)
            .addStatement("return new $T(this)", matchingWithReturnType)
            .build());

    for (DataType dataType : dataSetSet.dataTypes()) {
      final ClassName dataTypeClassName = classNameFor(dataSetSet, dataType);
      ParameterizedTypeName function =
          ParameterizedTypeName.get(
              ClassName.get(Function.class), dataTypeClassName, returnTypeVariable);

      matchingTypeBuilder.addMethod(
          MethodSpec.methodBuilder(dataType.name())
              .addModifiers(Modifier.PUBLIC)
              .addParameter(function, "fn", Modifier.FINAL)
              .returns(matchingWithReturnType)
              .beginControlFlow("if (this.value instanceof $T)", dataTypeClassName)
              .addStatement("this.returnValue = fn.apply(($T) this.value)", dataTypeClassName)
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

    final TypeSpec matchingType = matchingTypeBuilder.build();
    gadtTypeBuilder.addType(matchingType);
  }

  private static ClassName resolveClassNameFor(DataSetSet dataSetSet, String classReference) {

    // if full class, just guess
    if (classReference.contains(".")) {
      return ClassName.bestGuess(classReference);
    }

    // else find imported class
    for (String importedClass : dataSetSet.imports()) {
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

  private static ClassName classNameFor(DataSetSet dataSetSet, DataType dataType) {
    return ClassName.get(dataSetSet.packageName(), dataSetSet.name() + "." + dataType.name());
  }
}
