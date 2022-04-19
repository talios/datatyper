package com.theoryinpractise.datatyper;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.theoryinpractise.datatyper.model.DataType;
import com.theoryinpractise.datatyper.model.DataTypeContainer;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static com.theoryinpractise.datatyper.Support.camelCase;
import static com.theoryinpractise.datatyper.Support.classNameFor;
import static com.theoryinpractise.datatyper.Support.typeNameFor;

public class AccepterGenerator {

  public static void generateAccepterInterfaceAndCall(
      TypeSpec.Builder gadtTypeBuilder, DataTypeContainer dataTypeContainer) {
    // Generate matcher interface
    TypeSpec.Builder accepterTypeBuilder =
        TypeSpec.interfaceBuilder("Accepter").addModifiers(Modifier.PUBLIC);

    List<TypeVariableName> genericTypeVariableNames =
        dataTypeContainer.genericTypes().stream().map(TypeVariableName::get).collect(toList());

    for (TypeVariableName genericType : genericTypeVariableNames) {
      accepterTypeBuilder.addTypeVariable(genericType);
    }

    ClassName accepterClassName =
        ClassName.get(dataTypeContainer.packageName(), dataTypeContainer.name() + ".Accepter");

    for (DataType dataType : dataTypeContainer.dataTypes()) {
      accepterTypeBuilder.addMethod(
          MethodSpec.methodBuilder(camelCase(dataType.name()))
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .addParameter(typeNameFor(dataTypeContainer, dataType), camelCase(dataType.name()))
              .build());
    }

    TypeSpec accepterType = accepterTypeBuilder.build();
    gadtTypeBuilder.addType(accepterType);

    MethodSpec.Builder accepterBuilder =
        MethodSpec.methodBuilder("accept")
            .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
            .addParameter(accepterClassName, "accepter");
    for (DataType dataType : dataTypeContainer.dataTypes()) {
      TypeName typeName = typeNameFor(dataTypeContainer, dataType);
      ClassName className = classNameFor(dataTypeContainer, dataType);
      accepterBuilder.beginControlFlow("if (this instanceof $T)", className);
      accepterBuilder.addStatement("accepter.$L(($T) this)", camelCase(dataType.name()), typeName);
      accepterBuilder.endControlFlow();
    }
    accepterBuilder.addStatement(
        "throw new $T(\"Unexpected $L subclass encountered.\")",
        IllegalStateException.class,
        dataTypeContainer.name());

    gadtTypeBuilder.addMethod(accepterBuilder.build());
  }

  public static void generateFluentAcceptingInterfaceAndCall(
      TypeSpec.Builder gadtTypeBuilder, DataTypeContainer dataTypeContainer) {
    TypeSpec.Builder acceptingTypeBuilder =
        TypeSpec.classBuilder("Accepting")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);

    List<TypeVariableName> genericTypeVariableNames =
        dataTypeContainer.genericTypes().stream().map(TypeVariableName::get).collect(toList());

    for (TypeVariableName genericType : genericTypeVariableNames) {
      acceptingTypeBuilder.addTypeVariable(genericType);
    }

    ClassName gadtClassName =
        ClassName.get(dataTypeContainer.packageName(), dataTypeContainer.name());
    acceptingTypeBuilder.addField(boolean.class, "done", Modifier.PRIVATE);
    acceptingTypeBuilder.addField(gadtClassName, "value", Modifier.PRIVATE);
    acceptingTypeBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(gadtClassName, "value", Modifier.FINAL)
            .addStatement("this.value = value")
            .build());

    ClassName acceptingClassName =
        ClassName.get(dataTypeContainer.packageName(), dataTypeContainer.name() + ".Accepting");

    gadtTypeBuilder.addMethod(
        MethodSpec.methodBuilder("accepting")
            .addModifiers(Modifier.PUBLIC)
            .returns(acceptingClassName)
            .addStatement("return new $T(this)", acceptingClassName)
            .build());

    for (DataType dataType : dataTypeContainer.dataTypes()) {
      TypeName dataTypeClassName = classNameFor(dataTypeContainer, dataType);
      TypeName dataTypeTypeName = typeNameFor(dataTypeContainer, dataType);
      ParameterizedTypeName function =
          ParameterizedTypeName.get(ClassName.get(Consumer.class), dataTypeTypeName);

      acceptingTypeBuilder.addMethod(
          MethodSpec.methodBuilder(dataType.name())
              .addModifiers(Modifier.PUBLIC)
              .addParameter(function, "fn", Modifier.FINAL)
              .returns(acceptingClassName)
              .beginControlFlow("if (this.value instanceof $T)", dataTypeClassName)
              .addStatement("fn.accept(($T) this.value)", dataTypeTypeName)
              .addStatement("this.done = true", dataTypeTypeName)
              .endControlFlow()
              .addStatement("return this")
              .build());
    }

    acceptingTypeBuilder.addMethod(
        MethodSpec.methodBuilder("wasAccepted")
            .addModifiers(Modifier.PUBLIC)
            .returns(boolean.class)
            .addStatement("return this.done")
            .build());

    // Add exception throwing partial matching result
    acceptingTypeBuilder.addMethod(
        MethodSpec.methodBuilder("accept")
            .addModifiers(Modifier.PUBLIC)
            .beginControlFlow("if (!this.done)")
            .addStatement(
                "throw new $T(\"Unmatched value: \" + this.value)", IllegalStateException.class)
            .endControlFlow()
            .build());

    ParameterizedTypeName function =
        ParameterizedTypeName.get(ClassName.get(Consumer.class), gadtClassName);

    acceptingTypeBuilder.addMethod(
        MethodSpec.methodBuilder("orElse")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(function, "fn", Modifier.FINAL)
            .beginControlFlow("if (!this.done)")
            .addStatement("fn.accept(this.value)")
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement(
                "throw new $T(\"Value already matched: \" + this.value)",
                IllegalStateException.class)
            .endControlFlow()
            .build());

    TypeSpec matchingType = acceptingTypeBuilder.build();
    gadtTypeBuilder.addType(matchingType);
  }
}
