package com.theoryinpractise.gadt;

import com.theoryinpractise.gadt.model.DataType;
import com.theoryinpractise.gadt.model.Field;
import com.theoryinpractise.gadt.model.Gadt;
import org.codehaus.jparsec.Parser;
import org.codehaus.jparsec.Parsers;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

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

    Parser.Reference<String> testPackage = Parser.newReference();
    testPackage.set(Parsers.constant("com.test"));

    assertThat(GadtParser.gadt(testPackage).parse("data Type = DataType(name: java.lang.String);").dataTypes()).hasSize(1);

    assertThat(GadtParser.gadt(testPackage).parse("data Type = DataType(name: java.lang.String)\n | SecondDataType(age:int);").dataTypes()).hasSize(2);

    String source = "data Type = DataType(name: java.lang.String)\n"
        + "  | SecondDataType(age: Integer);\n\n\n";

    System.out.println(GadtParser.gadt(testPackage).parse(source));

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

    Parser.Reference<String> testPackage = Parser.newReference();
    testPackage.set(Parsers.constant("com.test"));

    System.out.println(GadtParser.gadt(testPackage).parse(source));

  }



}
