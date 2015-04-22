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
package exm.stc.common;

import java.io.IOException;
import java.util.HashSet;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import exm.stc.common.util.Pair;
import exm.stc.ui.ExitCode;

public class Logging {
  private static final String STC_LOGGER_NAME = "exm.stc";

  /**
   * Messages already emitted.
   */
  private static final HashSet<Pair<org.apache.log4j.Level, String>> emitted =
          new HashSet<Pair<org.apache.log4j.Level, String>>();

  public static Logger getSTCLogger() {
    return Logger.getLogger(STC_LOGGER_NAME);
  }


  public static Logger setupLogging(String logfile, boolean trace) {
    Logger stcLogger = getSTCLogger();
    if (logfile != null && logfile.length() > 0) {
      setupLoggingToStderr(stcLogger);
      setupLoggingToFile(stcLogger, logfile, trace);
    } else {
      setupLoggingToStderr(stcLogger);
    }

    // Even if logging is disabled, this must be valid:
    return stcLogger;
  }

  private static void setupLoggingToFile(Logger stcLogger, String logfile,
      boolean trace) {
    Layout layout = new PatternLayout("%-5p %m%n");
    boolean append = false;
    try {
      FileAppender appender = new FileAppender(layout, logfile, append);
      Level threshold;
      if (trace) {
        threshold = Level.TRACE;
      } else {
        threshold = Level.DEBUG;
      }
      appender.setThreshold(threshold);
      stcLogger.addAppender(appender);
      stcLogger.setLevel(threshold);
    } catch (IOException e) {
      System.out.println(e.getMessage());
      System.exit(ExitCode.ERROR_IO.code());
    }
  }

  /**
   * Configures Log4j to log warnings to stderr
   *
   * @param stcLogger
   */
  private static void setupLoggingToStderr(Logger stcLogger) {
    Layout layout = new PatternLayout("%-5p %m%n");
    ConsoleAppender appender = new ConsoleAppender(layout,
        ConsoleAppender.SYSTEM_ERR);
    appender.setThreshold(Level.WARN);
    stcLogger.addAppender(appender);
    stcLogger.setLevel(Level.WARN);
  }

  /**
   * @param level
   * @param msg
   * @return true if not already emitted
   */
  public static boolean addEmitted(org.apache.log4j.Level level, String msg) {
    return emitted.add(Pair.create(level, msg));
  }

  public static void uniqueWarn(String msg) {
    if (Logging.addEmitted(Level.WARN, msg)) {
      Logging.getSTCLogger().warn(msg);
    } else {
      Logging.getSTCLogger().debug("Duplicate Warning: " + msg);
    }
  }
}
