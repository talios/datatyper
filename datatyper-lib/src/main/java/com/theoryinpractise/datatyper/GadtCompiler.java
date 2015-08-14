package com.theoryinpractise.datatyper;

import com.theoryinpractise.datatyper.model.Gadt;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import static com.theoryinpractise.datatyper.GadtGenerator.generateJavaForGadt;

/**
 * Created by amrk on 2/08/15.
 */
public final class GadtCompiler {

  public static void compileFile(File file, File targetDirectory) {

    try {
      List<Gadt> gadts = DataTyperParser.gadtFile().parse(new FileReader(file));

      for (Gadt gadt : gadts) {
        generateJavaForGadt(gadt).writeTo(targetDirectory);
      }

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }
}
