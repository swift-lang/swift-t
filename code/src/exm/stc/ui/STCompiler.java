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
import java.io.PrintStream;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.ModuleLoadException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.util.Misc;
import exm.stc.frontend.ASTWalker;
import exm.stc.frontend.SwiftFile;
import exm.stc.frontend.SwiftFile.ParsedFile;
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
   * Compile a Swift file (input stream) to TCL output (output stream) and
   * log intermediate code to icOutput.
   * 
   * This function contains the high-level logic orchestrating the different
   * passes of the compiler
   * @param inputFile
   * @param input
   * @param output
   * @param icOutput
   */
  public void compile(String inputFile, PrintStream output,
          PrintStream icOutput) {
    try {
      String timestamp = Misc.timestamp();
      logger.info("STC starting: " + timestamp);
      
      ParsedFile parsed = parseFile(inputFile);
      
      boolean profile = Settings.getBoolean(Settings.PROFILE_STC);
      
      /* 
       * Walk AST, and build intermediate representation
       * This is where type checking and other semantic analysis happens.
       */
      for (int i = 0; i < (profile ? 1000000 : 1); i++) {
        if (i > 0)
          parsed.ast.resetAnnotations();
        ASTWalker walker = new ASTWalker();
        STCMiddleEnd intermediate = new STCMiddleEnd(logger, icOutput);
        walker.walk(intermediate, parsed);
        
        /* Optimise intermediate representation by repeatedly rewriting tree
         * NOTE: currently the optimizer pass is actually required for correctness,
         * as the frontend doesn't always provide correct information about which variables
         * need to be passed into blocks.  The optimizer will fix this problem
         */
        intermediate.optimize();
      
        /* Generate output tcl code from intermediate representation */
        TurbineGenerator codeGen = new TurbineGenerator(logger, timestamp);
        intermediate.regenerate(codeGen);
        String code = codeGen.code();

        output.println(code);
        output.close();
      }
      
      if (icOutput != null) {
        icOutput.close();
      }
      logger.debug("STC done: " + Misc.timestamp());
    }
    catch (UserException e)
    {
      System.err.println("stc error:");
      System.err.println(e.getMessage());
      if (logger.isDebugEnabled())
        logger.debug(Misc.stackTrace(e));
      System.exit(ExitCode.ERROR_USER.code());
    }
    catch (AssertionError e) {
      System.err.println("STC INTERNAL ERROR");
      System.err.println("Please report this");
      e.printStackTrace();
      System.exit(ExitCode.ERROR_INTERNAL.code());
    }
    catch (Throwable e)
    {
      // Other error, possibly ParserRuntimeException
      System.err.println("STC INTERNAL ERROR");
      System.err.println("Please report this");
      e.printStackTrace();
      System.exit(ExitCode.ERROR_INTERNAL.code());
    }
  }

  private ParsedFile parseFile(String inputFile) throws ModuleLoadException {
    SwiftFile inFile = new SwiftFile(inputFile);
    ParsedFile parsed;
    try {
      parsed = inFile.parse();
      return parsed;
    } catch (IOException e) {
      throw new ModuleLoadException(inputFile, e);
    }
  }
}
