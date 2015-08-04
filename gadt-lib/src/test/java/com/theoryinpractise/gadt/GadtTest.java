package com.theoryinpractise.gadt;

import com.theoryinpractise.gadt.model.DataType;
import com.theoryinpractise.gadt.model.Field;
import com.theoryinpractise.gadt.model.Gadt;
import org.codehaus.jparsec.Parser;
import org.codehaus.jparsec.functors.Pair;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.truth.Truth.assertThat;
import static com.theoryinpractise.gadt.GadtGenerator.generateJavaForGadt;
import static com.theoryinpractise.gadt.GadtParser.buildImportListParser;

public class GadtTest {

  final String string = "java.lang.String";

  @Test
  public void testLabel() {
    assertThat(GadtParser.label().parse("name")).isEqualTo("name");
  }

  @Test
  public void testFields() {
    assertThat(GadtParser.field().parse("name : java.lang.String")).isEqualTo(new Field("name", string));
    assertThat(GadtParser.field().parse("name:java.lang.String")).isEqualTo(new Field("name", string));
  }

  @Test
  public void testSingletonDataTypes() {
    assertThat(GadtParser.dataType().parse("DataType")).isEqualTo(
        new DataType("DataType", Collections.EMPTY_LIST));
  }

  @Test
  public void testDataTypes() {
    assertThat(GadtParser.dataType().parse("DataType(name: java.lang.String)")).isEqualTo(
        new DataType("DataType", Arrays.asList(new Field("name", string))));

    assertThat(GadtParser.dataType().parse("DataType   (name: java.lang.String)")).isEqualTo(
        new DataType("DataType", Arrays.asList(new Field("name", string))));

    assertThat(GadtParser.dataType().parse("DataType(name: java.lang.String, age: java.lang.Integer)")).isEqualTo(
        new DataType("DataType", Arrays.asList(new Field("name", string), new Field("age", "java.lang.Integer"))));

  }

  @Test
  public void testGadtDeclarations() {
    AtomicReference<String> testPackage = new AtomicReference<>("com.test");
    Pair<Parser<List<String>>, AtomicReference<List<String>>> testImports = buildImportListParser();

    assertThat(GadtParser.gadt(testPackage, testImports).parse("data Type = DataType(name: java.lang.String);").dataTypes()).hasSize(1);

    assertThat(GadtParser.gadt(testPackage, testImports).parse("data Type = DataType(name: java.lang.String)\n | SecondDataType(age:int);").dataTypes()).hasSize(2);

    String source = "import some.Object;\n\n"
        + "data Type = DataType(name: java.lang.String)\n"
        + "  | SecondDataType(age: Integer);\n\n\n";

    System.out.println(GadtParser.gadt(testPackage, testImports).parse(source));

  }

  @Test
  public void testGadtFiles() throws IOException {
    List<Gadt> gadts = GadtParser.gadtFile().parse(new InputStreamReader(GadtTest.class.getResourceAsStream("/Test.gadt")));

    for (Gadt gadt : gadts) {
      generateJavaForGadt(gadt).writeTo(System.out);
    }

  }

  @Test
  public void testImplementsClause() {
    String source = "data Type implements [foo] = DataType(name: java.lang.String)\n"
        + "  | SecondDataType(age: Integer);\n\n\n";

    AtomicReference<String> testPackage = new AtomicReference<>("com.test");
    Pair<Parser<List<String>>, AtomicReference<List<String>>> testImports = buildImportListParser();

    GadtParser.gadt(testPackage, testImports).parse(source);
  }

}
