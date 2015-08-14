package com.theoryinpractise.gadt;

import com.theoryinpractise.gadt.model.DataType;
import com.theoryinpractise.gadt.model.Field;
import com.theoryinpractise.gadt.model.Gadt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.codehaus.jparsec.Parser;
import org.codehaus.jparsec.Parsers;
import static org.codehaus.jparsec.Parsers.between;
import static org.codehaus.jparsec.Parsers.or;
import org.codehaus.jparsec.Scanners;
import static org.codehaus.jparsec.Scanners.WHITESPACES;
import static org.codehaus.jparsec.Scanners.isChar;
import static org.codehaus.jparsec.Scanners.string;
import org.codehaus.jparsec.functors.Pair;
import org.codehaus.jparsec.pattern.Patterns;

/**
 * Parser for GADT data declarations.
 */
public final class GadtParser {

//  private Parser<String> lineComment() {
//    return Scanners.JAVA_LINE_COMMENT.source().map(comment -> comment.substring(3));
//  }

  static Parser<?> comments = or(
      WHITESPACES,
      Scanners.HASKELL_LINE_COMMENT).skipMany();

  public static Parser<String> label = Patterns.regex("[a-z|A-Z]+").toScanner("regex").source();
  
  public static Parser<String> className = Patterns.regex("[a-z|A-Z][a-z|A-Z|0-9|\\.]+").toScanner("regex").source();
  
  public static Parser<Void> separator(char separator) {
    return WHITESPACES.optional().next(isChar(separator))
                               .next(WHITESPACES.optional());
  }

  public static Parser<Void> typeSeparator = separator(':');
 
  public static Parser<Void> fieldSeparator = separator(',');

  public static Parser<String> packageDecl() {
    return string("package").next(WHITESPACES).next(className.followedBy(string(";")));
  }

  public static Parser<String> importDecl() {
    return string("import").next(WHITESPACES).next(className).followedBy(isChar(';'));
  }

  public static Parser<Field> field() {
    return Parsers.tuple(label.followedBy(typeSeparator), className)
                  .map(field -> new Field(field.a, field.b));
  }

  public static Parser<List<Field>> fields() {
    return between(string("("), field().sepBy(fieldSeparator), string(")"));
  }

  public static Parser<DataType> dataType() {
    final Parser<List<Field>> fields = fields().optional(new ArrayList<>());
    return Parsers.tuple(label.followedBy(WHITESPACES.optional()), fields).map(dataType -> new DataType(dataType.a, dataType.b));
  }

  public static Parser<List<DataType>> dataTypes() {
    return dataType().sepBy1(
        comments.next(
                Parsers.sequence(comments, string("|"), comments))
        ).followedBy(or(wsString("implements"), isChar(';')).peek());
  }

  private static <T> Parser<T> parenthesize(Parser<T> parser) {
    return parser.between(isChar('('), isChar(')'));
  }

  private static Parser<Void> wsString(String string) {
    return WHITESPACES.optional().next(string(string)).next(WHITESPACES.optional());
  }

  public static Parser<Gadt> gadt(AtomicReference<String> packageName, Pair<Parser<List<String>>, AtomicReference<List<String>>> importList) {

    final Parser<List<String>> classNameList = className.followedBy(fieldSeparator.optional()).many();

    final Parser<List<String>> implementsClause = wsString("implements").next(parenthesize(classNameList))
                                                                        .followedBy(wsString("="));

    final Parser<List<String>> optionalImplementsClause = or(wsString("=").map(aVoid -> new ArrayList<>()),
                                                                     implementsClause);

    return comments.next(importList.a).next(comments).next(string("data").next(WHITESPACES).next(
        Parsers.tuple(label, optionalImplementsClause, dataTypes()).followedBy(comments)
               .map(gadt -> new Gadt(gadt.a, packageName.get(), gadt.c, copyOfList(importList.b.get()), new HashSet<>(gadt.b)))))
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

  public static Parser<List<Gadt>> gadtFile() {
    AtomicReference<String> pacakgeRef = new AtomicReference<>();

    Parser<String> packageName = packageDecl().map(pack -> {
      pacakgeRef.set(pack);
      return pack;
    });

    Pair<Parser<List<String>>, AtomicReference<List<String>>> importList = buildImportListParser();

    return comments.next(packageName)
                   .next(comments.next(importList.a))
                   .next(gadt(pacakgeRef, importList).many());
  }

  public static Pair<Parser<List<String>>, AtomicReference<List<String>>> buildImportListParser() {
    AtomicReference<List<String>> importListRef = new AtomicReference<>();

    final Parser<List<String>> parser = importDecl().many().map(imports -> {
      importListRef.getAndUpdate(existingImports -> copyOfList(imports, existingImports));
      return imports;
    });

    return new Pair<>(parser, importListRef);
  }


}
