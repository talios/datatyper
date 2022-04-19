package com.theoryinpractise.datatyper;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeVariableName;
import com.theoryinpractise.datatyper.model.DataType;
import com.theoryinpractise.datatyper.model.DataTypeContainer;

public class Support {

  // Convert to camelCase unless ALLCAPS
  public static String camelCase(String name) {
    if (name.toUpperCase().equals(name)) {
      return name;
    } else {
      return name.length() == 1
          ? name.toLowerCase()
          : name.substring(0, 1).toLowerCase() + name.substring(1);
    }
  }

  public static ClassName classNameFor(DataTypeContainer dataTypeContainer, DataType dataType) {
    return ClassName.get(
        dataTypeContainer.packageName(), dataTypeContainer.name() + "." + dataType.name());
  }

  public static TypeName typeNameFor(DataTypeContainer dataTypeContainer, DataType dataType) {

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
}
