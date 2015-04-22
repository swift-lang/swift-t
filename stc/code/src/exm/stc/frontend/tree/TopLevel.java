package exm.stc.frontend.tree;

import exm.stc.ast.antlr.ExMParser;

/**
 * Helpers to classify top level AST elements
 */
public class TopLevel {

  /**
   * @param token
   * @return true if the token is a valid top level definition
   */
  public static boolean isDefinition(int token) {
    switch (token) {
      case ExMParser.IMPORT:
      case ExMParser.DEFINE_BUILTIN_FUNCTION:
      case ExMParser.DEFINE_FUNCTION:
      case ExMParser.DEFINE_APP_FUNCTION:
      case ExMParser.DEFINE_NEW_STRUCT_TYPE:
      case ExMParser.DEFINE_NEW_TYPE:
      case ExMParser.TYPEDEF:
      case ExMParser.GLOBAL_CONST:
      case ExMParser.PRAGMA:
      case ExMParser.EOF:
        return true;
      default:
        return false;
    }
  }
  /**
   * @param token a AST token type
   * @return true if token is a statement token type that is syntactically
   *        valid at top level of program but isn't yet supported
   */
  public static boolean isStatement(int token) {
    switch (token) {
      case ExMParser.BLOCK:
      case ExMParser.IF_STATEMENT:
      case ExMParser.SWITCH_STATEMENT:
      case ExMParser.DECLARATION:
      case ExMParser.ASSIGN_EXPRESSION:
      case ExMParser.EXPR_STMT:
      case ExMParser.FOREACH_LOOP:
      case ExMParser.FOR_LOOP:
      case ExMParser.ITERATE:
      case ExMParser.WAIT_STATEMENT:
      case ExMParser.WAIT_DEEP_STATEMENT:
      case ExMParser.UPDATE:
      case ExMParser.STATEMENT_CHAIN:
        return true;
      default:
        return false;
    }
  }

}
