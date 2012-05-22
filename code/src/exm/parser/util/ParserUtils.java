
package exm.parser.util;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.antlr.runtime.tree.Tree;

import exm.ast.Context;

/**
 * Some parser and code generator utility functions
 * */
public class ParserUtils
{
  public static String printTree(Tree tree)
  {
    StringWriter sw = new StringWriter();
    PrintWriter writer = new PrintWriter(sw);
    writer.println("printTree:");
    printTree(writer, 0, tree);
    return sw.toString();
  }

  private static void printTree(PrintWriter writer,
                                int indent, Tree tree)
  {
    indent(writer, indent);
    writer.println(tree.getText());
    for (int i = 0; i < tree.getChildCount(); i++)
      printTree(writer, indent+2, tree.getChild(i));
  }

  public static void indent(PrintWriter writer, int indent)
  {
    for (int i = 0; i < indent; i++)
      writer.print(' ');
  }

  public static String unquote(String s)
  {
    if (s.charAt(0) != '"' ||
        s.charAt(s.length()-1) != '"')
      throw new STCRuntimeError("String not quoted: " + s);
    return s.substring(1, s.length()-1);
  }
  
  /** 
   * Take a string with c-style escape sequences and unescape it
   * @param context
   * @param escapedString
   * @return
   * @throws InvalidSyntaxException
   */
  public static String unescapeString(Context context, String escapedString)
      throws InvalidSyntaxException {
    StringBuilder realString = new StringBuilder();

    // leave quotation marks out of range
    for (int i = 0; i < escapedString.length(); i++) {
      char c = escapedString.charAt(i);
      if (c == '\\') {
        // Escape code!
        // We use the same escape codes as C
        i++; c = escapedString.charAt(i);
        if (Character.isDigit(c)) {
          // Octal escape code e.g. \7 \23 \03 \123
          String oct = String.valueOf(c);
          int digits = 1;
          while (digits < 3 && Character.isDigit(escapedString.charAt(i+1))) {
            i++;
            digits++;
            oct += escapedString.charAt(i);
          }
          realString.append((char)Integer.parseInt(oct,8));

        } else if (c == 'x') {
          // Hex escape code e.g. \x7 \xf \xf2
          String hex = "";
          int digits = 0;
          c = escapedString.charAt(i+1);
          while (digits < 2 && (Character.isDigit(c) ||
              Character.toUpperCase(c) >= 'A' &&
              Character.toUpperCase(c) <= 'F')) {
            i++;
            digits++;
            hex += Character.isDigit(escapedString.charAt(i));
            c = escapedString.charAt(i+1);
          }
          if (digits == 0) {
            throw new InvalidSyntaxException(context, "Hex escape code \\x was not "
                    + "followed by hex digit, instead " + c);
          }
          realString.append((char)Integer.parseInt(hex,16));
        } else {
          switch (c) {
          case 'a':
            realString.append('\007');
            break;
          case 'b':
            realString.append('\b');
            break;
          case 'f':
            realString.append('\f');
            break;
          case 'n':
            realString.append('\n');
            break;
          case 'r':
            realString.append('\r');
            break;
          case 't':
            realString.append('\t');
            break;
          case 'v':
            realString.append('\013');
            break;
          case '\\':
          case '"':
          case '?':
            realString.append(c);
            break;
          default:
              throw new InvalidSyntaxException(context, "Don't recognise escape code \\"
                  + c);
          }
        }
      } else {
        realString.append(c);
      }
    }

    String result = realString.toString();
    return result;
  }

}
