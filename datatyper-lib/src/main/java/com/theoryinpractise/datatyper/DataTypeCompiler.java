package com.theoryinpractise.datatyper;

import com.theoryinpractise.datatyper.model.DataTypeContainer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import static com.theoryinpractise.datatyper.DataTypeGenerator.generateJavaForTypeContainer;

public final class DataTypeCompiler {

  public static void compileFile(File file, File targetDirectory) {

    try {
      List<DataTypeContainer> dataTypeContainers =
          DatatypeParser.typerFile().parse(new FileReader(file));

      for (DataTypeContainer dataTypeContainer : dataTypeContainers) {
        generateJavaForTypeContainer(dataTypeContainer).writeTo(targetDirectory);
      }

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
