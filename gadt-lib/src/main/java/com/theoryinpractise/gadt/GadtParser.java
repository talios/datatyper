package com.theoryinpractise.gadt;

import com.theoryinpractise.gadt.model.DataType;
import com.theoryinpractise.gadt.model.Field;
import com.theoryinpractise.gadt.model.Gadt;
import org.codehaus.jparsec.Parser;
import org.codehaus.jparsec.Parsers;
import org.codehaus.jparsec.Scanners;
import org.codehaus.jparsec.pattern.Patterns;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.codehaus.jparsec.Parsers.between;
import static org.codehaus.jparsec.Scanners.string;

/**
 * Parser for GADT data declarations.
 */
public final class GadtParser {

//  private Parser<String> lineComment() {
//    return Scanners.JAVA_LINE_COMMENT.source().map(comment -> comment.substring(3));
//  }

  static Parser<?> delim = Parsers.or(
      Scanners.WHITESPACES,
      Scanners.HASKELL_LINE_COMMENT).skipMany();

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

  public static Parser<List<String>> implementsList() {
    final Parser<List<String>> classNameList = className().sepBy(fieldSeparator())
                                                          .between(Scanners.isChar('['), Scanners.isChar(']'));

    return string("implements").next(Scanners.WHITESPACES).next(classNameList)
                               .optional(new ArrayList<>());
  }

  public static Parser<String> importDecl() {
    return string("import").next(Scanners.WHITESPACES).next(className()).followedBy(Scanners.isChar(';'));
  }

  public static Parser<Field> field() {
    return Parsers.tuple(label().followedBy(typeSeparator()), className())
                  .map(field -> new Field(field.a, field.b));
  }

  public static Parser<List<Field>> fields() {
    return between(string("("), field().sepBy(fieldSeparator()), string(")"));
  }

  public static Parser<DataType> dataType() {
    return Parsers.tuple(label(), fields())
                  .map(dataType -> new DataType(dataType.a, dataType.b));
  }

  public static Parser<List<DataType>> dataTypes() {
    return Scanners.WHITESPACES.optional().next(string("="))
                               .next(Scanners.WHITESPACES)
                               .next(dataType().sepBy1(
                                   delim.optional().next(
                                       Parsers.sequence(Parsers.or(Scanners.isChar('\n').next(Scanners.WHITESPACES),
                                                                   Scanners.WHITESPACES.optional()),
                                                        string("|"), Scanners.WHITESPACES.optional()))
                               ).followedBy(Scanners.isChar(';').next(delim)));
  }

  public static Parser<Gadt> gadt(AtomicReference<String> packageName, AtomicReference<List<String>> importListRef) {

    return delim.next(string("data").next(Scanners.WHITESPACES).next(
        Parsers.tuple(label(), Scanners.WHITESPACES.next(implementsList()),
                      delim.next(dataTypes())
                           .followedBy(delim))
               .map(gadt -> new Gadt(gadt.a, packageName.get(), gadt.c, importListRef.get(), new HashSet(gadt.b)))));

  }

  public static Parser<List<Gadt>> gadtFile() {
    AtomicReference<String> pacakgeRef = new AtomicReference<>();
    AtomicReference<List<String>> importListRef =  new AtomicReference<>();

    Parser<String> packageName = packageDecl().map(pack -> {
      pacakgeRef.set(pack);
      return pack;
    });
    
    Parser<List<String>> importList = importDecl().many().map(imports -> {
      importListRef.set(imports);
      return imports;
    });

    return delim.next(packageName)
                .next(delim.next(importList))
                .next(gadt(pacakgeRef, importListRef).many());
  }


}
