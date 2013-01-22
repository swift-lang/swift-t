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
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StreamTokenizer;
import java.io.StringReader;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.tree.CommonTreeAdaptor;
import org.apache.log4j.Logger;

import exm.stc.ast.antlr.ExMLexer;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.util.Misc;
import exm.stc.frontend.ASTWalker;
import exm.stc.ic.STCMiddleEnd;
import exm.stc.tclbackend.TurbineGenerator;
import exm.stc.swigcbackend.SwigcGenerator;

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
     Use ANTLR to parse the input and get the Tree
   * @throws IOException 
   */
  private SwiftAST runANTLR(ANTLRInputStream input, LineMapping lineMap) {

    ExMLexer lexer = new ExMLexer(input);
    lexer.lineMap = lineMap;
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    ExMParser parser = new ExMParser(tokens);
    parser.lineMap = lineMap;
    parser.setTreeAdaptor(new SwTreeAdaptor());

    // Launch parsing
    ExMParser.program_return program = null;
    try
    {
      program = parser.program();
    }
    catch (RecognitionException e)
    {
      // This is an internal error
      e.printStackTrace();
      System.out.println("Parsing failed: internal error");
      System.exit(ExitCode.ERROR_INTERNAL.code());
    }
    
    /* NOTE: in some cases the antlr parser will actually recover from
     *    errors, print an error message and continue, generating the 
     *    parse tree that it thinks is most plausible.  This is where
     *    we detect this case.
     */
    if (parser.parserError) {
      // This is a user error
      System.err.println("Error occurred during parsing.");
      System.exit(ExitCode.ERROR_USER.code());
    }

    // Do we actually need this check? -Justin (10/26/2011)
    if (program == null)
      throw new STCRuntimeError("PARSER FAILED!");
   
    
    SwiftAST tree = (SwiftAST) program.getTree();
    
    return tree;
  }

  /**
   * Use the file and line info from c preprocessor to 
   * update SwiftAST
   * @param lexer
   * @param tree
   */
  private static LineMapping parsePreprocOutput(ANTLRInputStream input) {

    /*
     * This function is a dirty hack, but works ok
     * because the C preprocessor output has a very simple output format
     * of 
     * # linenum filename flags
     * 
     * We basically just need the linenum and filename
     * (see http://gcc.gnu.org/onlinedocs/cpp/Preprocessor-Output.html)
     */
    LineMapping posTrack = new LineMapping();
    try {
      ExMLexer lexer = new ExMLexer(input);
      /* 
       * don't emit error messages with bad line numbers:
       * we will emit lexer error messages on the second pass
       */
      lexer.quiet = true;
      Token t = lexer.nextToken();
      while (t.getType() != ExMLexer.EOF) {
        if (t.getChannel() == ExMLexer.CPP) { 
          //System.err.println("CPP token: " + t.getText());
          assert(t.getText().substring(0, 2).equals("# "));
          StreamTokenizer tok = new StreamTokenizer(
                new StringReader(t.getText().substring(2)));
          tok.slashSlashComments(false);
          tok.slashStarComments(false);
          tok.quoteChar('"');
          if (tok.nextToken() != StreamTokenizer.TT_NUMBER) {
            throw new STCRuntimeError("Confused by " +
                " preprocessor line " + t.getText());
          }
          int lineNum = (int)tok.nval;
          
          if (tok.nextToken() == '"') {
            // Quoted file name with octal escape sequences
            
            // Ignore lines from preprocessor holding information we
            // don't need (these start with "<"
            String fileName = tok.sval;
            if (!fileName.startsWith("<")) {
              posTrack.addPreprocInfo(t.getLine() + 1, 
                                    fileName, lineNum);
            }
          }
        }
        t = lexer.nextToken();
      }
    } catch (IOException e) {
      System.out.println("Error while trying to read preprocessor" +
          " output: " + e.getMessage());
      System.exit(ExitCode.ERROR_IO.code());
    }
    return posTrack;
  }

  public static class SwTreeAdaptor extends CommonTreeAdaptor {
    @Override
    public Object create(Token t) {
      return new SwiftAST(t);
    }
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
  public void compile(String inputFile, InputStream input, PrintStream output,
          PrintStream icOutput) {
    try {
      String timestamp = Misc.timestamp();
      logger.info("STC starting: " + timestamp);
      
      /* Parse the input file and build AST */
      ANTLRInputStream antlrInput = new ANTLRInputStream(input);
      LineMapping lineMapping = parsePreprocOutput(antlrInput);
      antlrInput.rewind(); antlrInput.reset();
      SwiftAST tree = runANTLR(antlrInput, lineMapping);
      
      /* 
       * Walk AST, and build intermediate representation
       * This is where type checking and other semantic analysis happens.
       */
      ASTWalker walker = new ASTWalker(inputFile, lineMapping);
      STCMiddleEnd intermediate = new STCMiddleEnd(logger, icOutput);
      walker.walk(intermediate, tree);
      
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

      boolean doILikeCrashes = false;
      if (doILikeCrashes) {
        SwigcGenerator swigCodeGen = new SwigcGenerator(logger, timestamp);
        intermediate.regenerate(swigCodeGen);
        String swigcode = swigCodeGen.code();
        output.println(swigcode);
      }

      output.close();

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
    catch (IOException e) {
      System.err.println("IO error while compiling:\n");
      e.printStackTrace();
      System.exit(ExitCode.ERROR_IO.code());
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
}
