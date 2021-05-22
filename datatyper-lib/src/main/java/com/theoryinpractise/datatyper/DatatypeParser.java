package com.theoryinpractise.datatyper;

import com.theoryinpractise.datatyper.model.DataType;
import com.theoryinpractise.datatyper.model.Field;
import com.theoryinpractise.datatyper.model.DataTypeContainer;
import org.jparsec.Parser;
import org.jparsec.Parsers;
import org.jparsec.Scanners;
import org.jparsec.functors.Pair;
import org.jparsec.pattern.Patterns;

import java.util.ArrayList;
import java.util.Collections;
import static java.util.Collections.emptyList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.jparsec.Parsers.between;
import static org.jparsec.Parsers.or;
import static org.jparsec.Scanners.WHITESPACES;
import static org.jparsec.Scanners.isChar;
import static org.jparsec.Scanners.string;

/** Parser for GADT data declarations. */
public final class DatatypeParser {

  static Parser<?> comments = or(WHITESPACES, Scanners.HASKELL_LINE_COMMENT).skipMany();

  public static Parser<String> label = Patterns.regex("[a-z|A-Z]+").toScanner("regex").source();

  public static Parser<String> genericType =
      Patterns.regex("[a-z|A-Z][a-z|A-Z]*").toScanner("generic-type-regex").source();

  public static Parser<String> className =
      Patterns.regex("[a-z|A-Z][a-z|A-Z|0-9|\\.]+").toScanner("class-regex").source();

  public static Parser<String> typeName =
      Patterns.regex("[a-z|A-Z][<|>|a-z|A-Z|0-9|\\.]*").toScanner("type-regex").source();

  public static Parser<Void> separator(char separator) {
    return WHITESPACES.optional(null).next(isChar(separator)).next(WHITESPACES.optional(null));
  }

  public static Parser<Void> typeSeparator = separator(':');

  public static Parser<Void> fieldSeparator = separator(',');

  public static Parser<String> packageDecl() {
    return string("package").next(WHITESPACES).next(className.followedBy(string(";")));
  }

  public static Parser<String> importDecl() {
    return WHITESPACES
        .optional(null)
        .next(string("import"))
        .next(WHITESPACES)
        .next(className)
        .followedBy(isChar(';'));
  }

  public static Parser<Field> field() {
    return Parsers.tuple(label.followedBy(typeSeparator), typeName)
        .map(field -> new Field(field.a, field.b));
  }

  public static Parser<List<Field>> fields() {
    return between(string("("), field().sepBy(fieldSeparator), string(")"));
  }

  public static Parser<DataType> dataType() {
    Parser<List<Field>> fields = fields().optional(new ArrayList<>());
    return Parsers.tuple(label.followedBy(WHITESPACES.optional(null)), fields)
        .map(dataType -> new DataType(dataType.a, dataType.b));
  }

  public static Parser<List<DataType>> dataTypes() {
    return dataType()
        .sepBy1(comments.next(Parsers.sequence(comments, string("|"), comments)))
        .followedBy(or(wsString("implements"), isChar(';')).peek());
  }

  private static <T> Parser<T> parenthesize(Parser<T> parser) {
    return parser.between(isChar('('), isChar(')'));
  }

  private static <T> Parser<T> angleParenthesize(Parser<T> parser) {
    return parser.between(isChar('<'), isChar('>'));
  }

  private static Parser<Void> wsString(String string) {
    return WHITESPACES.optional(null).next(string(string)).next(WHITESPACES.optional(null));
  }

  public static Parser<DataTypeContainer> gadt(
      AtomicReference<String> packageName,
      Pair<Parser<List<String>>, AtomicReference<List<String>>> importList) {

    Parser<List<String>> classNameList = className.followedBy(fieldSeparator.optional(null)).many();

    Parser<List<String>> genericTypeList =
        genericType.followedBy(fieldSeparator.optional(null)).many();

    Parser<List<String>> genericTypesClause = angleParenthesize(genericTypeList);

    Parser<List<String>> optionalGenericTypes = genericTypesClause.optional(emptyList());

    Parser<List<String>> implementsClause =
        wsString("implements").next(parenthesize(classNameList)).followedBy(wsString("="));

    Parser<List<String>> optionalImplementsClause =
        or(wsString("=").map(aVoid -> emptyList()), implementsClause);

    return comments
        .next(importList.a)
        .next(comments)
        .next(
            string("data")
                .next(WHITESPACES)
                .next(
                    Parsers.tuple(
                            label, optionalGenericTypes, optionalImplementsClause, dataTypes())
                        .followedBy(comments)
                        .map(
                            gadt ->
                                new DataTypeContainer(
                                    gadt.a,
                                    packageName.get(),
                                    gadt.b,
                                    gadt.d,
                                    copyOfList(importList.b.get()),
                                    new HashSet<>(gadt.c)))))
        .followedBy(isChar(';').next(comments));
  }

  private static List<String> copyOfList(List<String>... originalLists) {
    if (originalLists != null && originalLists.length != 0) {
      List<String> copiedList = new ArrayList<>();
      for (List<String> originalList : originalLists) {
        if (originalList != null && !originalList.isEmpty()) {
          copiedList.addAll(originalList);
        }
      }
      return copiedList;
    } else {
      return Collections.emptyList();
    }
  }

  public static Parser<List<DataTypeContainer>> typerFile() {
    AtomicReference<String> pacakgeRef = new AtomicReference<>();

    Parser<String> packageName =
        packageDecl()
            .map(
                pack -> {
                  pacakgeRef.set(pack);
                  return pack;
                });

    Pair<Parser<List<String>>, AtomicReference<List<String>>> importList = buildImportListParser();

    return comments
        .next(packageName)
        .next(comments.next(importList.a))
        .next(gadt(pacakgeRef, importList).many());
  }

  public static Pair<Parser<List<String>>, AtomicReference<List<String>>> buildImportListParser() {
    AtomicReference<List<String>> importListRef = new AtomicReference<>();

    Parser<List<String>> parser =
        importDecl()
            .many()
            .map(
                imports -> {
                  importListRef.getAndUpdate(
                      existingImports -> copyOfList(imports, existingImports));
                  return imports;
                });

    return new Pair<>(parser, importListRef);
  }
}
