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
package exm.stc.frontend;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.Logging;

/**
 * Helper functions to augment log messages with contextual information about
 * the current line.
 *
 */
public class LogHelper {
  static final Logger logger = Logging.getSTCLogger();
  public static void logChildren(int indent, SwiftAST tree) {
    for (SwiftAST child: tree.children()) {
      trace(indent+2, child.getText());
    }
  }

  /**
   * @param tokenNum token number from AST
   * @return descriptive string containing token name, or token number if
   *        unknown token type
   */
  public static String tokName(int tokenNum) {
    if (tokenNum < 0 || tokenNum > ExMParser.tokenNames.length - 1) {
      return "Invalid token number (" + tokenNum + ")";
    } else {
      return ExMParser.tokenNames[tokenNum];
    }
  }

  public static void info(Context context, String msg) {
    log(context.getLevel(), Level.INFO, context.getLocation(), msg);
  }

  public static void debug(Context context, String msg) {
    log(context.getLevel(), Level.DEBUG, context.getLocation(), msg);
  }

  public static void trace(Context context, String msg) {
    log(context.getLevel(), Level.TRACE, context.getLocation(), msg);
  }

  /**
     INFO-level with indentation for nice output
   */
  public static void info(int indent, String msg) {
    log(indent, Level.INFO, msg);
  }

  /**
    WARN-level with indentation for nice output
   */
  public static void warn(Context context, String msg) {
    log(context.getLevel(), Level.WARN, context.getLocation(), msg);
  }

  public static void uniqueWarn(Context context, String message) {
    Logging.uniqueWarn(logMsg(0, context.getLocation(), message));
  }

  /**
    ERROR-level with indentation for nice output
   */
  public static void error(Context context, String msg) {
    log(context.getLevel(), Level.ERROR, context.getLocation(), msg);
  }

  /**
     DEBUG-level with indentation for nice output
   */
  public static void debug(int indent, String msg) {
    log(indent, Level.DEBUG, msg);
  }

  /**
     TRACE-level with indentation for nice output
   */
  public static void trace(int indent, String msg) {
    log(indent, Level.TRACE, msg);
  }

  public static void log(int indent, Level level, String location, String msg) {
    logger.log(level, logMsg(indent, location, msg));
  }

  private static String logMsg(int indent, String location, String msg) {
    StringBuilder sb = new StringBuilder(256);
    sb.append(location);
    for (int i = 0; i < indent; i++)
      sb.append(' ');
    sb.append(msg);
    String s = sb.toString();
    return s;
  }

  public static void log(int indent, Level level, String msg) {
    logger.log(level, logMsg(indent, msg));
  }

  private static String logMsg(int indent, String msg) {
    StringBuilder sb = new StringBuilder(256);
    for (int i = 0; i < indent; i++)
      sb.append(' ');
    sb.append(msg);
    return sb.toString();
  }

  public static boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }

  public static boolean isTraceEnabled() {
    return logger.isTraceEnabled();
  }
}
