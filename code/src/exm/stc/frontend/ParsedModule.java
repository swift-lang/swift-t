package exm.stc.frontend;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.tree.CommonTreeAdaptor;

import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMLexer;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.exceptions.STCFatal;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.ui.ExitCode;

/**
 * Represents an input Swift source file
 */
public class ParsedModule {

  public ParsedModule(String moduleName, String filePath, SwiftAST ast,
                      LineMapping lineMapping) {
    this.moduleName = moduleName;
    this.inputFilePath = filePath;
    this.ast = ast;
    this.lineMapping = lineMapping;
  }

  /** Canonical name for module */
  public final String moduleName;
  public final String inputFilePath;
  public final SwiftAST ast;
  public final LineMapping lineMapping;

  /**
   * Parse the specified file and create a ParsedModule object
   * @param path
   * @param preprocessed
   * @return
   * @throws IOException
   */
  public static ParsedModule parse(String moduleName, String path,
                                   boolean preprocessed) throws IOException {
    FileInputStream inputStream = setupInput(path);
    /* Parse the input file and build AST */
    ANTLRInputStream antlrInput = new ANTLRInputStream(inputStream);
    LineMapping lineMapping;
    if (preprocessed) {
      int startMark = antlrInput.mark();
      lineMapping = parsePreprocOutput(antlrInput);
      antlrInput.rewind(startMark);
    } else {
      // Treat # lines as comments.  All input from same file
      lineMapping = LineMapping.makeSimple(path);
    }
    SwiftAST tree = runANTLR(antlrInput, lineMapping);

    return new ParsedModule(moduleName, path, tree, lineMapping);
  }
  /**
   * @param filePath
   * @return
   * @throws IOException
   */
  public static String getCanonicalFilePath(String filePath) throws IOException {
    return new File(filePath).getCanonicalPath();
  }


  public String moduleName() {
    return this.moduleName;
  }

  private static FileInputStream setupInput(String inputFilename) {
    FileInputStream input = null;
    try {
      input = new FileInputStream(inputFilename);
    } catch (IOException e) {
      System.out.println("Error opening input Swift file: " +
                                            e.getMessage());
      throw new STCFatal(ExitCode.ERROR_IO.code());
    }
    return input;
  }

  /**
     Use ANTLR to parse the input and get the Tree
   * @throws IOException
   */
  private static SwiftAST runANTLR(ANTLRInputStream input, LineMapping lineMap) {

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
      throw new STCFatal(ExitCode.ERROR_INTERNAL.code());
    }

    /* NOTE: in some cases the antlr parser will actually recover from
     *    errors, print an error message and continue, generating the
     *    parse tree that it thinks is most plausible.  This is where
     *    we detect this case.
     */
    if (parser.parserError) {
      // This is a user error
      System.err.println("Error occurred during parsing.");
      throw new STCFatal(ExitCode.ERROR_USER.code());
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
          assert(t.getText().substring(0, 2).equals("# ")): t.getText();
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
      throw new STCFatal(1);
    }
    return posTrack;
  }

  public static class SwTreeAdaptor extends CommonTreeAdaptor {
    @Override
    public Object create(Token t) {
      return new SwiftAST(t);
    }
  }
}
