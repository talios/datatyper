package com.theoryinpractise.gadt;

import com.theoryinpractise.gadt.model.DataType;
import com.theoryinpractise.gadt.model.Field;
import com.theoryinpractise.gadt.model.Gadt;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.truth.Truth.assertThat;
import static com.theoryinpractise.gadt.GadtGenerator.generateJavaForGadt;
import static com.theoryinpractise.gadt.GadtParser.implementsList;

public class GadtTest {

  @Test
  public void testComments() throws IOException {

    //    assertThat(lineComment().parse("// hello")).isEqualTo("hello");

    assertThat(GadtParser.label().parse("name")).isEqualTo("name");


    final String string = "java.lang.String";
    assertThat(GadtParser.field().parse("name : java.lang.String")).isEqualTo(new Field("name", string));

    assertThat(GadtParser.field().parse("name:java.lang.String")).isEqualTo(new Field("name", string));

    assertThat(GadtParser.dataType().parse("DataType(name: java.lang.String)")).isEqualTo(
        new DataType("DataType", Arrays.asList(new Field("name", string))));

    assertThat(GadtParser.dataType().parse("DataType(name: java.lang.String, age: java.lang.Integer)")).isEqualTo(
        new DataType("DataType", Arrays.asList(new Field("name", string), new Field("age", "java.lang.Integer"))));

    AtomicReference<String> testPackage = new AtomicReference<>("com.test");
    AtomicReference<List<String>> testImports = new AtomicReference<>(new ArrayList());

    assertThat(GadtParser.gadt(testPackage, testImports).parse("data Type = DataType(name: java.lang.String);").dataTypes()).hasSize(1);

    assertThat(GadtParser.gadt(testPackage, testImports).parse("data Type = DataType(name: java.lang.String)\n | SecondDataType(age:int);").dataTypes()).hasSize(2);

    String source = "data Type = DataType(name: java.lang.String)\n"
        + "  | SecondDataType(age: Integer);\n\n\n";

    System.out.println(GadtParser.gadt(testPackage, testImports).parse(source));

    List<Gadt> gadts = GadtParser.gadtFile().parse(new InputStreamReader(GadtTest.class.getResourceAsStream("/Test.gadt")));

    for (Gadt gadt : gadts) {
      generateJavaForGadt(gadt).writeTo(System.out);
    }

  }

  @Test
  public void testImplementsClause() {
    assertThat(implementsList().parse("implements [java.lang.Runnable]")).containsExactly("java.lang.Runnable");
    assertThat(implementsList().parse("implements [java.lang.Runnable, java.test.Test]")).containsExactly("java.lang.Runnable", "java.test.Test");
    assertThat(implementsList().parse("")).isEmpty();

    String source = "data Type implements [foo] = DataType(name: java.lang.String)\n"
        + "  | SecondDataType(age: Integer);\n\n\n";

    AtomicReference<String> testPackage = new AtomicReference<>("com.test");
    AtomicReference<List<String>> testImports = new AtomicReference<>(new ArrayList());

    System.out.println(GadtParser.gadt(testPackage, testImports).parse(source));

  }

}
