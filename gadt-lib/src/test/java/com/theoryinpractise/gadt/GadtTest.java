package com.theoryinpractise.gadt;

import com.theoryinpractise.gadt.model.Gadt;
import com.theoryinpractise.gadt.model.ImmutableDataType;
import com.theoryinpractise.gadt.model.ImmutableField;
import org.codehaus.jparsec.Parser;
import org.codehaus.jparsec.Parsers;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.theoryinpractise.gadt.GadtGenerator.generateJavaForGadt;

public class GadtTest {

  @Test
  public void testComments() throws IOException {

    //    assertThat(lineComment().parse("// hello")).isEqualTo("hello");

    assertThat(GadtParser.label().parse("name")).isEqualTo("name");


    final String string = "java.lang.String";
    assertThat(GadtParser.field().parse("name : java.lang.String")).isEqualTo(ImmutableField.of("name", string));

    assertThat(GadtParser.field().parse("name:java.lang.String")).isEqualTo(ImmutableField.of("name", string));

    assertThat(GadtParser.dataType().parse("DataType(name: java.lang.String)")).isEqualTo(
        ImmutableDataType.of("DataType", Arrays.asList(ImmutableField.of("name", string))));

    assertThat(GadtParser.dataType().parse("DataType(name: java.lang.String, age: java.lang.Integer)")).isEqualTo(
        ImmutableDataType.of("DataType", Arrays.asList(ImmutableField.of("name", string), ImmutableField.of("age", "java.lang.Integer"))));

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

}
