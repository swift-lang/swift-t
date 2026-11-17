/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package exm.stc.frontend.tree;

import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.STCRuntimeError;

/**
 * Helpers for building AST nodes when a construct is rewritten into
 * other statements before analysis (arguments(), route).  Every node
 * carries the line and column of a source token, so that errors and
 * syncFilePos() point somewhere the user can see.
 */
final class AstBuild {

  static SwiftAST node(Token pos, int type, String text) {
    CommonToken tok = new CommonToken(type, text);
    tok.setLine(pos.getLine());
    tok.setCharPositionInLine(pos.getCharPositionInLine());
    return new SwiftAST(tok);
  }

  /** Node with the given children */
  static SwiftAST node(Token pos, int type, String text,
                       SwiftAST... children) {
    SwiftAST result = node(pos, type, text);
    for (SwiftAST child: children) {
      result.addChild(child);
    }
    return result;
  }

  /** Nil-rooted list node: ANTLR splices its children into the parent */
  static SwiftAST nil() {
    return new SwiftAST(null);
  }

  /** ^( CALL_FUNCTION ID[name] ^( ARGUMENT_LIST args... ) ) */
  static SwiftAST call(Token pos, String name, SwiftAST... args) {
    SwiftAST argList = node(pos, ExMParser.ARGUMENT_LIST, "ARGUMENT_LIST",
                            args);
    return node(pos, ExMParser.CALL_FUNCTION, "CALL_FUNCTION",
                node(pos, ExMParser.ID, name), argList);
  }

  /** ^( EXPR_STMT expr ) */
  static SwiftAST stmt(Token pos, SwiftAST expr) {
    return node(pos, ExMParser.EXPR_STMT, "EXPR_STMT", expr);
  }

  /** ^( VARIABLE ID[name] ) */
  static SwiftAST variable(Token pos, String name) {
    return node(pos, ExMParser.VARIABLE, "VARIABLE",
                node(pos, ExMParser.ID, name));
  }

  /** ^( INT_LITERAL DECIMAL_INT[n] ) */
  static SwiftAST intLit(Token pos, long n) {
    return node(pos, ExMParser.INT_LITERAL, "INT_LITERAL",
                node(pos, ExMParser.DECIMAL_INT, Long.toString(n)));
  }

  /** ^( STRING_LITERAL STRING["\"...\""] ) */
  static SwiftAST stringLit(Token pos, String s) {
    return node(pos, ExMParser.STRING_LITERAL, "STRING_LITERAL",
                node(pos, ExMParser.STRING, quote(s)));
  }

  /**
   * ^( DECLARATION ID[type] ^( DECLARE_ASSIGN
   *                           ^( DECLARE_VARIABLE_REST ID[name] ) init ) )
   */
  static SwiftAST declareAssign(Token pos, String type, String name,
                                SwiftAST init) {
    SwiftAST rest = node(pos, ExMParser.DECLARE_VARIABLE_REST,
                         "DECLARE_VARIABLE_REST",
                         node(pos, ExMParser.ID, name));
    SwiftAST assign = node(pos, ExMParser.DECLARE_ASSIGN, "DECLARE_ASSIGN",
                           rest, init);
    return node(pos, ExMParser.DECLARATION, "DECLARATION",
                node(pos, ExMParser.ID, type), assign);
  }

  /**
   * Re-escape a string so that Literals.unescapeString() recovers it.
   */
  static String quote(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 2);
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':  sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\n': sb.append("\\n");  break;
        case '\r': sb.append("\\r");  break;
        case '\t': sb.append("\\t");  break;
        default:   sb.append(c);      break;
      }
    }
    sb.append('"');
    return sb.toString();
  }

  private AstBuild() {
    throw new STCRuntimeError("Not instantiable");
  }
}
