package com.theoryinpractise.datatyper;

import com.theoryinpractise.datatyper.model.DataType;
import com.theoryinpractise.datatyper.model.Field;
import com.theoryinpractise.datatyper.model.DataTypeContainer;
import org.jparsec.Parser;
import org.jparsec.functors.Pair;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.truth.Truth.assertThat;
import static com.theoryinpractise.datatyper.DataTypeGenerator.generateJavaForTypeContainer;
import static com.theoryinpractise.datatyper.DatatypeParser.buildImportListParser;
import java.io.StringWriter;
import java.io.Writer;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

public class DatatyperTest {

  final String string = "java.lang.String";

  @Test
  public void testLabel() {
    assertThat(DatatypeParser.label.parse("name")).isEqualTo("name");
  }

  @Test
  public void testFields() {
    assertThat(DatatypeParser.field().parse("name : java.lang.String"))
        .isEqualTo(new Field("name", string));
    assertThat(DatatypeParser.field().parse("name:java.lang.String"))
        .isEqualTo(new Field("name", string));
  }

  @Test
  public void testSingletonDataTypes() {
    assertThat(DatatypeParser.dataType().parse("DataType"))
        .isEqualTo(new DataType("DataType", Collections.emptyList()));
  }

  @Test
  public void testDataTypes() {
    assertThat(DatatypeParser.dataType().parse("DataType(name: java.lang.String)"))
        .isEqualTo(new DataType("DataType", Arrays.asList(new Field("name", string))));

    assertThat(DatatypeParser.dataType().parse("DataType   (name: java.lang.String)"))
        .isEqualTo(new DataType("DataType", Arrays.asList(new Field("name", string))));

    assertThat(
            DatatypeParser.dataType()
                .parse("DataType(name: java.lang.String, age: java.lang.Integer)"))
        .isEqualTo(
            new DataType(
                "DataType",
                Arrays.asList(new Field("name", string), new Field("age", "java.lang.Integer"))));
  }

  @Test
  public void testGadtDeclarations() {
    AtomicReference<String> testPackage = new AtomicReference<>("com.test");
    Pair<Parser<List<String>>, AtomicReference<List<String>>> testImports = buildImportListParser();

    assertThat(
            DatatypeParser.gadt(testPackage, testImports)
                .parse("data Type = DataType(name: java.lang.String);")
                .dataTypes())
        .hasSize(1);

    assertThat(
            DatatypeParser.gadt(testPackage, testImports)
                .parse("data Type = DataType(name: java.lang.String)\n | SecondDataType(age:int);")
                .dataTypes())
        .hasSize(2);

    String source =
        "import some.Object;\n\n"
            + "data Type = DataType(name: java.lang.String)\n"
            + "  | SecondDataType(age: Integer);\n\n\n";

    System.out.println(DatatypeParser.gadt(testPackage, testImports).parse(source));
  }

  @Test
  public void testGadtFiles() throws IOException {
    List<DataTypeContainer> dataTypeContainers =
        DatatypeParser.typerFile()
            .parse(new InputStreamReader(DatatyperTest.class.getResourceAsStream("/Test.typer")));

    assertThat(dataTypeContainers).hasSize(5);

    Writer writer = new StringWriter();

    for (DataTypeContainer dataTypeContainer : dataTypeContainers) {
      generateJavaForTypeContainer(dataTypeContainer).writeTo(writer);
      writer.append("\n\n");
    }

    Approvals.verify(writer.toString());
  }

  @Test
  public void testImplementsClause() {
    String source =
        ""
            + "data Type\n"
            + "  implements (foo)"
            + "  = DataType(name: java.lang.String)\n"
            + "  | SecondDataType(age: Integer);\n\n\n";

    AtomicReference<String> testPackage = new AtomicReference<>("com.test");
    Pair<Parser<List<String>>, AtomicReference<List<String>>> testImports = buildImportListParser();

    DatatypeParser.gadt(testPackage, testImports).parse(source);
  }
}
