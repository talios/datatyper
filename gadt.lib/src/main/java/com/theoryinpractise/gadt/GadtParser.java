package com.theoryinpractise.gadt;

import com.theoryinpractise.gadt.model.DataType;
import com.theoryinpractise.gadt.model.Field;
import com.theoryinpractise.gadt.model.Gadt;
import com.theoryinpractise.gadt.model.ImmutableDataType;
import com.theoryinpractise.gadt.model.ImmutableField;
import com.theoryinpractise.gadt.model.ImmutableGadt;
import org.codehaus.jparsec.Parser;
import org.codehaus.jparsec.Parsers;
import org.codehaus.jparsec.Scanners;
import org.codehaus.jparsec.pattern.Patterns;

import java.util.List;

import static org.codehaus.jparsec.Parsers.between;
import static org.codehaus.jparsec.Scanners.string;

/**
 * Created by amrk on 1/08/15.
 */
public final class GadtParser {

//  private Parser<String> lineComment() {
//    return Scanners.JAVA_LINE_COMMENT.source().map(comment -> comment.substring(3));
//  }

  public static Parser<String> label() {
    return Patterns.regex("[a-z|A-Z]+").toScanner("regex").source();
  }

  public static Parser<String> className() {
    return Patterns.regex("[a-z|A-Z][a-z|A-Z|0-9|\\.]+").toScanner("regex").source();
  }

  public static Parser<Void> separator(char separator) {
    return Scanners.WHITESPACES.optional().next(Scanners.isChar(separator))
                               .next(Scanners.WHITESPACES.optional());
  }

  public static Parser<Void> typeSeparator() {
    return separator(':');
  }

  public static Parser<Void> fieldSeparator() {
    return separator(',');
  }

  public static Parser<String> packageDecl() {
    return string("package").next(Scanners.WHITESPACES).next(className()).followedBy(string(";"));
  }

  public static Parser<Field> field() {
    return Parsers.tuple(label(), typeSeparator(), className())
                  .map(field -> ImmutableField.of(field.a, field.c));
  }

  public static Parser<List<Field>> fields() {
    return between(string("("), field().sepBy(fieldSeparator()), string(")"));
  }

  public static Parser<DataType> dataType() {
    return Parsers.tuple(label(), fields())
                  .map(dataType -> ImmutableDataType.of(dataType.a, dataType.b));
  }

  public static Parser<List<DataType>> dataTypes() {
    return Scanners.WHITESPACES.optional().next(string("="))
                               .next(Scanners.WHITESPACES)
                               .next(dataType().sepBy(
                                   Parsers.sequence(Parsers.or(Scanners.isChar('\n').next(Scanners.WHITESPACES), Scanners.WHITESPACES.optional()),
                                                    string("|"), Scanners.WHITESPACES.optional())
                               ).followedBy(Scanners.isChar(';').next(Scanners.WHITESPACES.many())));

  }

  public static Parser<Gadt> gadt(Parser<String> packageName) {

    return Parsers.or(Scanners.isChar('\n'), Scanners.JAVA_LINE_COMMENT).skipMany()
                  .next(string("data").next(Scanners.WHITESPACES)
                                      .next(Parsers.tuple(label(), Scanners.isChar('\n').optional().next(dataTypes()))
                                                   .map(gadt -> ImmutableGadt.of(gadt.a, packageName.toString(), gadt.b))));

  }

  public static Parser<List<Gadt>> gadtFile() {
    Parser<String> packageName = packageDecl();
    return Parsers.or(Scanners.isChar('\n'), Scanners.JAVA_LINE_COMMENT).skipMany()
                  .next(packageName)
                  .next(gadt(packageName).many());
  }


}
