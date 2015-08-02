package com.theoryinpractise.gadt;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.theoryinpractise.gadt.model.DataType;
import com.theoryinpractise.gadt.model.Field;
import com.theoryinpractise.gadt.model.Gadt;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by amrk on 2/08/15.
 */
public class GadtGenerator {

  public static JavaFile generateJavaForGadt(Gadt gadt) throws IOException {

    ClassName gadtClassName = ClassName.get(gadt.packageName(), gadt.name());
    ClassName autoValue = ClassName.get("com.google.auto.value", "AutoValue");

    // top level gadt class
    TypeSpec.Builder gadtTypeBuilder = TypeSpec.classBuilder(gadt.name())
                                               .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);

    for (String implemntsClass : gadt.implememts()) {
      gadtTypeBuilder.addSuperinterface(ClassName.bestGuess(implemntsClass));
    }

    gadtTypeBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    for (DataType dataType : gadt.dataTypes()) {

      ClassName dataTypeClassName = ClassName.get(gadt.packageName(), "AutoValue_" + gadt.name() + "_" + dataType.name());

      // Create data-type constructor/factory method for each data type
      MethodSpec.Builder dataTypeConstuctorBuilder = MethodSpec.methodBuilder(dataType.name())
                                                               .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                                                               .returns(classNameFor(gadt, dataType));

      // Create data type itself
      TypeSpec.Builder dataTypeBuilder = TypeSpec.classBuilder(dataType.name())
                                                 .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT, Modifier.STATIC)
                                                 .addAnnotation(autoValue)
                                                 .superclass(gadtClassName);

      // StringBuilder for constructor args
      List<String> fieldNames = new ArrayList<>();

      // For each field, add an argument to the constructor method, and a field to the class.
      for (Field field : dataType.fields()) {
        ClassName argClass = ClassName.bestGuess(field.type());
        fieldNames.add(field.name());

        dataTypeConstuctorBuilder.addParameter(argClass, field.name(), Modifier.FINAL);

        dataTypeBuilder.addMethod(MethodSpec.methodBuilder(field.name())
                                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                                            .returns(argClass).build());

      }

      TypeSpec dataTypeSpec = dataTypeBuilder.build();

      MethodSpec dataTypeConstuctor = dataTypeConstuctorBuilder
          .addStatement("return new $T($L)", dataTypeClassName, String.join(", ", fieldNames))
          .build();

      gadtTypeBuilder.addMethod(dataTypeConstuctor);
      gadtTypeBuilder.addType(dataTypeSpec);

    }

    generateMatcherInterfaceAndCall(gadtTypeBuilder, gadt);


    TypeSpec gadtType = gadtTypeBuilder.build();

    return JavaFile.builder(gadt.packageName(), gadtType)
                   .build();


  }

  private static void generateMatcherInterfaceAndCall(TypeSpec.Builder gadtTypeBuilder, Gadt gadt) {
    // Generate matcher interface
    final TypeVariableName returnTypeVariable = TypeVariableName.get("Return");
    TypeSpec.Builder matcherTypeBuilder = TypeSpec.interfaceBuilder("Matcher")
                                                  .addModifiers(Modifier.PUBLIC)
                                                  .addTypeVariable(returnTypeVariable);
    ClassName matcherClassName = ClassName.get(gadt.packageName(), gadt.name() + ".Matcher");

    for (DataType dataType : gadt.dataTypes()) {
      matcherTypeBuilder.addMethod(MethodSpec.methodBuilder("match")
                                             .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                                             .addParameter(classNameFor(gadt, dataType), dataType.name())
                                             .returns(returnTypeVariable)
                                             .build());
    }

    final TypeSpec matcherType = matcherTypeBuilder.build();
    gadtTypeBuilder.addType(matcherType);

    // add Matcher match method
    ParameterizedTypeName matcherWithReturnType = ParameterizedTypeName.get(matcherClassName, returnTypeVariable);
    final MethodSpec.Builder matcherBuilder = MethodSpec.methodBuilder("match")
                                                        .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
                                                        .addTypeVariable(returnTypeVariable)
                                                        .addParameter(matcherWithReturnType, "matcher");
    for (DataType dataType : gadt.dataTypes()) {
      final ClassName className = classNameFor(gadt, dataType);
      matcherBuilder.beginControlFlow("if (this instanceof $T)", className);
      matcherBuilder.addStatement("return matcher.match(($T) this)", className);
      matcherBuilder.endControlFlow();
    }
    matcherBuilder.addStatement("throw new $T(\"Unexpected $L subclass encountered.\")", IllegalStateException.class, gadt.name());
    matcherBuilder.returns(returnTypeVariable)
                  .build();

    gadtTypeBuilder.addMethod(matcherBuilder.build());
  }

  private static ClassName classNameFor(Gadt gadt, DataType dataType) {
    return ClassName.get(gadt.packageName(), gadt.name() + "." + dataType.name());
  }


}
