package exm.stc.ast.descriptor;

import exm.stc.antlr.gen.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.frontend.Context;
import exm.stc.frontend.LogHelper;

public class Literals {

  /**
   *
   * @param context
   * @param tree
   * @return null if tree isn't a literal, the string otherwise e.g. "2312" or
   *         "-1"
   */
  public static String extractIntLit(Context context, SwiftAST tree) {
    // Literals are either represented as a plain non-negative literal,
    // or the unary negation operator applied to a literal
    if (tree.getType() == ExMParser.INT_LITERAL) {
      return tree.child(0).getText();
    } else if (tree.getType() == ExMParser.OPERATOR
        && tree.getChildCount() == 2
        && tree.child(1).getType() == ExMParser.INT_LITERAL) {
      return "-" + tree.child(1).child(0).getText();
    } else {
      return null;
    }
  }

  public static String extractBoolLit(Context context, SwiftAST tree) {
    assert(tree.getType() == ExMParser.BOOL_LITERAL);
    assert(tree.getChildCount() == 1);
    return tree.child(0).getText();
  }

  public static Double extractFloatLit(Context context, SwiftAST tree) {
    // Literals are either represented as a plain non-negative literal,
    // or the unary negation operator applied to a literal
    SwiftAST litTree;
    boolean negate;
    if (tree.getType() == ExMParser.FLOAT_LITERAL) {
      litTree = tree.child(0);
      negate = false;
    } else if (tree.getType() == ExMParser.OPERATOR
        && tree.getChildCount() == 2
        && tree.child(0).getType() == ExMParser.NEGATE
        && tree.child(1).getType() == ExMParser.FLOAT_LITERAL) {
      litTree = tree.child(1).child(0);
      negate = true;
    } else {
      return null;
    }
    double num;
    if (litTree.getType() == ExMParser.NOTANUMBER) {
      num = Double.NaN;
    } else if (litTree.getType() == ExMParser.INFINITY) {
      num = Double.POSITIVE_INFINITY;
    } else {
      assert(litTree.getType() == ExMParser.DECIMAL);
      num = Double.parseDouble(litTree.getText());
    }
    return negate ? -1.0 * num : num;
  }

  public static String extractStringLit(Context context, SwiftAST tree) throws InvalidSyntaxException {
    assert(tree.getType() == ExMParser.STRING_LITERAL);
    assert(tree.getChildCount() == 1);
    // E.g. "hello world\n" with plain escape codes and quotes
    String result = extractLiteralString(context, tree.child(0));
    LogHelper.trace(context, "Unescaped string '" + tree.child(0).getText() + 
              "', resulting in '" + result + "'");
    return result;
  }
  


  /**
   * Interpret an integer literal as a float literal, warning
   * if this would result in loss of precision
   * @param value an integer literal string
   * @return
   */
  public static double interpretIntAsFloat(Context context, String value) {
    long longval = Long.parseLong(value);
     // Casts from long to double can lost precision
     double floatval = (double)longval;
     if (longval !=  (long)(floatval)) {
       LogHelper.warn(context, 
             "Conversion of 64-bit integer constant " + longval
           + " to double precision floating point resulted in a loss of"
           + "  precision with result: " + (long)(floatval));
     }
    return floatval;
  }
  
  public static String extractLiteralString(Context context, 
                                              SwiftAST stringLiteral) 
                                          throws InvalidSyntaxException {
    assert(stringLiteral.getType() == ExMParser.STRING);
    return unescapeString(context, unquote(stringLiteral.getText()));
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
