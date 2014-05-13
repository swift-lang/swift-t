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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCFatal;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.util.Misc;
import exm.stc.frontend.ASTWalker;
import exm.stc.ic.STCMiddleEnd;
import exm.stc.tclbackend.TurbineGenerator;

/**
 * This is the main entry point to the compiler
 */
public class STCompiler {

  private Logger logger;
  
  
  public STCompiler(Logger logger) {
    super();
    this.logger = logger;
  }

  /**
   * Compile a Swift file (input file) to TCL output (output stream) and
   * log intermediate code to icOutput.
   * 
   * This function contains the high-level logic orchestrating the different
   * passes of the compiler
   * @param inputFile
   * @param originalInputFile
   * @param output
   * @param icOutput
   */
  public void compile(String inputFile, String originalInputFile,
                      boolean preprocessed, OutputStream output,
          PrintStream icOutput) {
    try {
      logger.info("STC starting: " + Misc.timestamp());

      boolean profile = Settings.getBoolean(Settings.PROFILE_STC);
      
      /* 
       * Walk AST, and build intermediate representation
       * This is where type checking and other semantic analysis happens.
       */
      int compileIterations = profile ? 100000 : 1;
      for (int i = 0; i < compileIterations; i++) {
        compileOnce(inputFile, originalInputFile, preprocessed, output, icOutput);
      }

      output.close();

      if (icOutput != null) {
        icOutput.close();
      }
      logger.debug("STC done: " + Misc.timestamp());
    }
    catch (STCFatal e) {
      // Rethrow
      throw e;
    }
    catch (UserException e) {
      System.err.println("stc error:");
      System.err.println(e.getMessage());
      if (logger.isDebugEnabled())
        logger.debug(Misc.stackTrace(e));
      throw new STCFatal(ExitCode.ERROR_USER.code());
    }
    catch (AssertionError e) {
      reportInternalError(e);
      throw new STCFatal(ExitCode.ERROR_INTERNAL.code());
    }
    catch (Throwable e) {
      // Other error, possibly ParserRuntimeException
      reportInternalError(e);
      throw new STCFatal(ExitCode.ERROR_INTERNAL.code());
    }
  }

  private void compileOnce(String inputFile, String originalInputFile,
      boolean preprocessed,
      OutputStream output, PrintStream icOutput) throws UserException {
    STCMiddleEnd intermediate = new STCMiddleEnd(logger, icOutput);
    ASTWalker walker = new ASTWalker(intermediate);
    walker.walk(inputFile, originalInputFile, preprocessed);
    
    /* Optimise intermediate representation by repeatedly rewriting tree
     * NOTE: currently the optimizer pass is actually required for correctness,
     * as the frontend doesn't always provide correct information about which variables
     * need to be passed into blocks.  The optimizer will fix this problem
     */
    intermediate.optimize();
   
    /* Generate output tcl code from intermediate representation */
    TurbineGenerator codeGen = new TurbineGenerator(logger, Misc.timestamp());
    intermediate.regenerate(codeGen);
    try {
      codeGen.generate(output);
    } catch (IOException e) {
      System.err.println("I/O error while writing to output");
      System.err.println(e.getMessage());
      throw new STCFatal(ExitCode.ERROR_IO.code());
    }
  }

  public static void reportInternalError(Throwable e) {
    System.err.println("STC INTERNAL ERROR");
    System.err.println("Please report this");
    e.printStackTrace();
  }
}
