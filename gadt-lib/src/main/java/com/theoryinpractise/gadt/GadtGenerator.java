package com.theoryinpractise.gadt;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
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

    gadtTypeBuilder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    for (DataType dataType : gadt.dataTypes()) {

      ClassName dataTypeClassName = ClassName.get(gadt.packageName(), "AutoValue_" + gadt.name() + "_" + dataType.name());

      // Create data-type constructor/factory method for each data type
      MethodSpec.Builder dataTypeConstuctorBuilder = MethodSpec.methodBuilder(dataType.name())
                                                               .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                                                               .returns(gadtClassName);

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

    TypeSpec gadtType = gadtTypeBuilder.build();
    // .addMethod(main)

    return JavaFile.builder(gadt.packageName(), gadtType)
                                .build();



  }


}
