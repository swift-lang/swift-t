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

package exm.stc.ui;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCFatal;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.CompileTimeArgs;

/**
 * Command line interface to STC compiler.  Some compiler options
 * are passed indirectly through Java properties.  See Settings.java
 * for handling of these options.
 */
public class Main {
  private static final String SWIFT_PROG_ARG_FLAG = "A";
  private static final String PREPROC_MACRO_FLAG = "D";
  private static final String INCLUDE_FLAG = "I";
  private static final List<File> temporaries = new ArrayList<File>();
  
  
  public static void main(String[] args) {
    
    Args stcArgs = processArgs(args);
    
    try {
      Settings.initSTCProperties();
    } catch (InvalidOptionException ex) {
      System.err.println("Error setting up options: " + ex.getMessage());
      System.exit(1);
    }
    Logger logger = null;
    try {
      logger = setupLogging();
    } catch (InvalidOptionException ex) {
      System.err.println("Error setting up logging: " + ex.getMessage());
      System.exit(1);
    }


    boolean preprocess = preprocessEnabled();
    File inputFile = setupInputFile(logger, preprocess, stcArgs);
    PrintStream icOutput = setupICOutput();
    PrintStream output = setupOutput(stcArgs);

    try {
      if (skipCompile()) {
        copyToOutput(inputFile, output);
      } else {
        STCompiler stc = new STCompiler(logger);
        stc.compile(inputFile.getPath(), preprocess, output, icOutput);
      }

      cleanupFiles(true, stcArgs);
    } catch (STCFatal ex) {
      // Cleanup output file if present
      cleanupFiles(false, stcArgs);
      System.exit(ex.exitCode);
    }
  }


  private static Options initOptions() {
    Options opts = new Options();
    
    Option module = new Option(INCLUDE_FLAG, "include", true, 
                                    "Add to import search path");
    opts.addOption(module);
    
    Option arg = new Option(SWIFT_PROG_ARG_FLAG, "arg", true,
        "Compile-time argument");
    arg.setArgs(2);
    arg.setValueSeparator('=');
    opts.addOption(arg);
    
    Option preprocArg = new Option(PREPROC_MACRO_FLAG, true,
                                    "Preprocessor definition");
    opts.addOption(preprocArg);
    return opts;
  }


  private static Args processArgs(String[] args) {
    Options opts = initOptions();
    
    CommandLine cmd = null;
    try {
      CommandLineParser parser = new GnuParser();
      cmd = parser.parse(opts, args);
    } catch (ParseException ex) {
      // Use Apache CLI-provided messages
      System.err.println(ex.getMessage());
      usage(opts);
      System.exit(1);
      return null; 
    }
    
    if (cmd.hasOption(INCLUDE_FLAG)) {
      for (String dir: cmd.getOptionValues(INCLUDE_FLAG)) {
        Settings.addModulePath(dir);
      }
    }

    Properties swiftProgramArgs = cmd.getOptionProperties(SWIFT_PROG_ARG_FLAG);
    
    String preprocMacros[]; 
    if (cmd.hasOption(PREPROC_MACRO_FLAG)) {
      preprocMacros = cmd.getOptionValues(PREPROC_MACRO_FLAG);
    } else {
      preprocMacros = new String[0];
    }
    
    String[] remainingArgs = cmd.getArgs();
    if (remainingArgs.length < 1 || remainingArgs.length > 2) {
      System.out.println("Expected input file and optional output file, but got "
              + remainingArgs.length + " arguments");
      usage(opts);
      System.exit(ExitCode.ERROR_COMMAND.code());
    }
    
    String input = remainingArgs[0];
    String output = null;
    if (remainingArgs.length == 2) {
      output = remainingArgs[1];
    }
    Args result = new Args(input, output, swiftProgramArgs,
                           Arrays.asList(preprocMacros));
    recordArgValues(result);
    return result;
  }

  private static boolean skipCompile() {
    boolean skipCompile = false;
    try {
      if (Settings.getBoolean(Settings.PREPROCESS_ONLY)) {
        skipCompile = true;
      }
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.toString());
    }
    return skipCompile;
  }
  
  private static boolean preprocessEnabled() {
    try {
      if (Settings.getBoolean(Settings.USE_C_PREPROCESSOR)) {
        return true;
      }
    } catch (InvalidOptionException e) {
      STCompiler.reportInternalError(e);
      System.exit(1);
    }
    return false;
  }

  /**
   * Store in properties for later logging
   * @param args
   */
  private static void recordArgValues(Args args) {
    Settings.set(Settings.INPUT_FILENAME, args.inputFilename);
    if (args.outputFilename != null) {
      Settings.set(Settings.OUTPUT_FILENAME, args.outputFilename);
    }
    
    for (String macro: args.preprocessorMacros) {
      Settings.addMetadata("Macro", macro);
    }
    
    for (Object key: args.swiftProgramArgs.keySet()) {
      String keyS = (String) key;
      String val = args.swiftProgramArgs.getProperty(keyS);
      Settings.addMetadata("Arg " + keyS, val);
      CompileTimeArgs.addCompileTimeArg(keyS, val);
    }
  }


  private static Logger setupLogging() throws InvalidOptionException {
    String logfile = Settings.get(Settings.LOG_FILE);
    Logger stcLogger = Logging.getSTCLogger();
    boolean trace = Settings.getBoolean(Settings.LOG_TRACE);
    if (logfile != null && logfile.length() > 0) {
      setupLoggingToStderr(stcLogger);
      setupLoggingToFile(stcLogger, logfile, trace);
    } else {
      setupLoggingToStderr(stcLogger);
    }

    // Even if logging is disabled, this must be valid:
    return stcLogger;
  }

  private static void setupLoggingToFile(Logger stcLogger, String logfile, boolean trace)
  {
    Layout layout = new PatternLayout("%-5p %m%n");
    boolean append = false;
    try
    {
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
    }
    catch (IOException e)
    {
      System.out.println(e.getMessage());
      System.exit(ExitCode.ERROR_IO.code());
    }
  }

  /**
     Configures Log4j to log warnings to stderr
   * @param stcLogger 
   */
  private static void setupLoggingToStderr(Logger stcLogger)
  {
    Layout layout = new PatternLayout("%-5p %m%n");
    ConsoleAppender appender = new ConsoleAppender(layout,
                          ConsoleAppender.SYSTEM_ERR);
    appender.setThreshold(Level.WARN);
    stcLogger.addAppender(appender);
    stcLogger.setLevel(Level.WARN);
  }

  private static void usage(Options opts) {
    HelpFormatter fmt = new HelpFormatter();
    fmt.printHelp("stc", opts, true);
    System.out.println("requires arguments: <input> <output>");
    System.out.println("see wrapper script for usage");
  }

  /**
   * Setup input file.  If necessary, run through CPP
   * @param logger 
   * @param preprocess 
   * @param args
   * @return
   */
  private static File setupInputFile(Logger logger, boolean preprocess, Args args) {
    File result;
    try {
      if (preprocess) {
        File input = new File(args.inputFilename);
        if (!input.isFile() || !input.canRead()) {
          System.out.println("Input file \"" + input + "\" is not readable");
          System.exit(1);
        }
        
        result = File.createTempFile("stc-preproc", ".swift");
        temporaries.add(result);
        runPreprocessor(logger, args.inputFilename, result.getPath(),
                        args.preprocessorMacros);
      } else {
        result = new File(args.inputFilename);
      }
      if (!result.isFile() || !result.canRead()) {
        System.out.println("Input file \"" + result + "\" is not readable");
        System.exit(1);
      }
      return result;
    } catch (IOException ex) {
      System.out.println("Error while setting up input file: " +
              ex.toString());
      System.exit(1);
    } catch (Throwable t) {
      STCompiler.reportInternalError(t);
      System.exit(1);
    }
    return null;
  }
  
  private static void runPreprocessor(Logger logger, String input, String output,
                                      List<String> preprocArgs) {
    List<String> cmd = new ArrayList<String>();
    if (useGCCProcessor()) {
      // We use gcc -E because cpp is broken on Mac GCC 4.2.1
      //    Cf. http://stackoverflow.com/questions/4137923
      cmd.addAll(Arrays.asList("gcc", "-E", "-x", "c", input, output));
    } else {
      cmd.addAll(Arrays.asList("cpp", input, output));
    }
   
    for (String dir: Settings.getModulePath()) {
      cmd.add("-I");
      cmd.add(dir);
    }
    
    for (String macro: preprocArgs) {
      cmd.add("-D");
      cmd.add(macro);
    }
    
    try {
      logger.debug("Running cpp: " + cmd);
      Process cpp = Runtime.getRuntime().exec(cmd.toArray(new String[]{}));
      int cppExitCode = -1;
      boolean done = false;
      do {
        try { 
          cppExitCode = cpp.waitFor();
          done = true;
        } catch (InterruptedException ex) {
          // Continue on after spurious interrupt
        }
      } while (!done);
      
      if (cppExitCode != 0) {
        System.out.println("Previous failure in cpp preprocessor invoked as: " +
        		cmd + ".  Exit code was " + cppExitCode + ". Aborting.");
        System.exit(1);
      }
    } catch (IOException e) {
      System.out.println("Error while launching preprocessor with command line:" +
                      cmd + ": " + e.getMessage());
      System.exit(1);
    }
  }


  public static boolean useGCCProcessor() {
    try {
      if ((SystemUtils.IS_OS_MAC_OSX && 
          !Settings.getBoolean(Settings.PREPROCESSOR_FORCE_CPP))) {
        return true;
      } else if (Settings.getBoolean(Settings.PREPROCESSOR_FORCE_GCC)) {
        return true;
      } else {
        return false;
      }
    } catch (InvalidOptionException e) {
      System.out.println("Internal error with settings: " + e.getMessage());
      System.exit(ExitCode.ERROR_INTERNAL.code());
      return false;
    }
  }


  private static void copyToOutput(File inputFile, PrintStream output) {
    // Copy to output
    try {
      FileUtils.copyFile(inputFile, output);
    } catch (IOException e) {
      System.out.println("Error copying " + inputFile);
      e.printStackTrace();
      throw new STCFatal(1);
    }
  }


  private static PrintStream setupOutput(Args args) {
    if (args.outputFilename == null) {
      return System.out;
    }
    
    PrintStream output = null;
    try {
      Logging.getSTCLogger().debug("Writing output to " + args.outputFilename);
      FileOutputStream stream = new FileOutputStream(args.outputFilename);
      BufferedOutputStream buffer = new BufferedOutputStream(stream);
      output = new PrintStream(buffer);
    } catch (IOException e) {
      System.out.println("Error opening output file: " + e.getMessage());
      System.exit(ExitCode.ERROR_IO.code());
    }
    return output;
  }

  private static PrintStream setupICOutput() {
    String icFileName = Settings.get(Settings.IC_OUTPUT_FILE);
    if (icFileName == null || icFileName.equals("")) {
      return null;
    }
    PrintStream output = null;
    try
    {
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

  private static void cleanupFiles(boolean success, Args stcArgs) {
    if (!success && stcArgs.outputFilename != null) {
      File outFile = new File(stcArgs.outputFilename);
      if (outFile.exists()) {
        outFile.delete();
      }
    }
    for (File temp: temporaries) {
      if (temp.exists()) {
        temp.delete();
      }
    }
  }

  private static class Args {
    public final String inputFilename;
    public final String outputFilename;
    public final Properties swiftProgramArgs;
    public final List<String> preprocessorMacros;
    
    public Args(String inputFilename, String outputFilename,
                Properties swiftProgramArgs, List<String> preprocessorArgs) {
      super();
      this.inputFilename = inputFilename;
      this.outputFilename = outputFilename;
      this.swiftProgramArgs = swiftProgramArgs;
      this.preprocessorMacros = preprocessorArgs;
    }
  }
}
