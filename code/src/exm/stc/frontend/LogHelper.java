package exm.stc.frontend;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import exm.stc.antlr.gen.ExMParser;
import exm.stc.ast.SwiftAST;

/**
 * Helper functions to augment log messages with contextual information about
 * the current line.
 *
 */
public class LogHelper {
  static final Logger logger = Logger.getLogger("");
  public static void logChildren(int indent, SwiftAST tree) {
    for (int i = 0; i < tree.getChildCount(); i++) {
      trace(indent+2, tree.child(i).getText());
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
    StringBuilder sb = new StringBuilder(256);
    sb.append(location);
    for (int i = 0; i < indent; i++)
      sb.append(' ');
    sb.append(msg);
    logger.log(level, sb.toString());
  }
  
  public static void log(int indent, Level level, String msg) {
    StringBuilder sb = new StringBuilder(256);
    for (int i = 0; i < indent; i++)
      sb.append(' ');
    sb.append(msg);
    logger.log(level, sb);
  }

  public static boolean isDebugEnabled() {
    return logger.isDebugEnabled();
  }
}
