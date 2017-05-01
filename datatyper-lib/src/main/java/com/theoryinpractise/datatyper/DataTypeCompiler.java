package com.theoryinpractise.datatyper;

import com.theoryinpractise.datatyper.model.DataSetSet;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import static com.theoryinpractise.datatyper.DataTypeGenerator.generateJavaForGadt;

/** Created by amrk on 2/08/15. */
public final class DataTypeCompiler {

  public static void compileFile(File file, File targetDirectory) {

    try {
      List<DataSetSet> dataSetSets = DatatypeParser.gadtFile().parse(new FileReader(file));

      for (DataSetSet dataSetSet : dataSetSets) {
        generateJavaForGadt(dataSetSet).writeTo(targetDirectory);
      }

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
