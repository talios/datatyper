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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static com.theoryinpractise.datatyper.Support.camelCase;
import static com.theoryinpractise.datatyper.Support.classNameFor;
import static com.theoryinpractise.datatyper.Support.typeNameFor;

public class MatcherGenerator {

  public static void generateMatcherInterfaceAndCall(
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

  public static void generateFluentMatcherInterfaceAndCall(
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
}
