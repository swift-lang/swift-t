
package exm.stc.ui;

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Properties;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;

/**
 * Command line interface to STC compiler.  Most compiler options
 * are passed indirectly through Java properties.  See Settings.java
 * for handling of these options.
 */
public class Main
{
  public static void main(String[] args)
  {
    Args stcArgs = processArgs(args);
    
    try {
      Settings.initSTCProperties();
    } catch (InvalidOptionException ex) {
      System.err.println("Error setting up options: " + ex.getMessage());
      System.exit(1);
    }
    Logger logger = setupLogging();

    InputStream input = setupInput(stcArgs.inputFilename);
    PrintStream output = setupOutput(stcArgs.outputFilename);
    PrintStream icOutput = setupICOutput();
    
    STCompiler stc = new STCompiler(logger);
    stc.compile(stcArgs.inputFilename, input, output, icOutput);
  }


  private static Args processArgs(String[] args) {
    Args stcArgs = null;
    if (args.length == 2) {
      stcArgs = new Args(args[0], args[1]);
    } else {
      usage();
      System.exit(ExitCode.ERROR_COMMAND.code());
    }
    return stcArgs;
  }

  
  private static Logger setupLogging()
  {
    Properties properties = System.getProperties();
    String logfile = properties.getProperty("stc.logfile");
    if (logfile != null && logfile.length() > 0) {
      setupLoggingToStderr();
      setupLoggingToFile(logfile);
    } else {
      setupLoggingToStderr();
    }

    // Even if logging is disabled, this must be valid:
    return Logger.getLogger("STC");
  }

  private static void setupLoggingToFile(String logfile)
  {
    Layout layout = new PatternLayout("%-5p %m%n");
    boolean append = false;
    try
    {
      Logger root = Logger.getRootLogger();
      Appender appender = new FileAppender(layout, logfile, append);
      root.addAppender(appender);
      root.setLevel(Level.TRACE);
    }
    catch (IOException e)
    {
      System.out.println(e.getMessage());
      System.exit(ExitCode.ERROR_IO.code());
    }
  }

  /**
     Configures Log4j to log warnings to stderr
   */
  private static void setupLoggingToStderr()
  {
    Layout layout = new PatternLayout("%-5p %m%n");
    ConsoleAppender appender = new ConsoleAppender(layout,
                          ConsoleAppender.SYSTEM_ERR);
    appender.setThreshold(Level.WARN);
    Logger root = Logger.getRootLogger();
    root.addAppender(appender);
    root.setLevel(Level.WARN);
  }

  private static void usage()
  {
    System.out.println("requires arguments: <input> <output>");
    System.out.println("see wrapper script for usage");
  }

  static FileInputStream setupInput(String inputFilename)
  {
    FileInputStream input = null;
    try
    {
      input = new FileInputStream(inputFilename);
    }
    catch (IOException e)
    {
      System.out.println("Error opening input Swift file: " +
                                            e.getMessage());
      System.exit(ExitCode.ERROR_IO.code());
    }
    return input;
  }

  static PrintStream setupOutput(String outputFileName)
  {
    PrintStream output = null;
    try
    {
      @SuppressWarnings("resource")
      FileOutputStream stream = new FileOutputStream(outputFileName);
      BufferedOutputStream buffer = new BufferedOutputStream(stream);
      output = new PrintStream(buffer);
    }
    catch (IOException e)
    {
      System.out.println("Error opening output file: " +
                                          e.getMessage());
      System.exit(ExitCode.ERROR_IO.code());
    }
    return output;
  }

  static PrintStream setupICOutput()
  {
    String icFileName = Settings.get(Settings.IC_OUTPUT_FILE);
    if (icFileName == null || icFileName.equals("")) {
      return null;
    }
    PrintStream output = null;
    try
    {
      @SuppressWarnings("resource")
      FileOutputStream stream = new FileOutputStream(icFileName);
      BufferedOutputStream buffer = new BufferedOutputStream(stream);
      output = new PrintStream(buffer);
    }
    catch (IOException e)
    {
      System.out.println("Error opening IC output file " + icFileName
                      + ": " + e.getMessage());
      System.exit(ExitCode.ERROR_IO.code());
    }
    return output;
  }

  private static class Args {
    public final String inputFilename;
    public final String outputFilename;
    public Args(String inputFilename, String outputFilename) {
      super();
      this.inputFilename = inputFilename;
      this.outputFilename = outputFilename;
    }
  }
}
