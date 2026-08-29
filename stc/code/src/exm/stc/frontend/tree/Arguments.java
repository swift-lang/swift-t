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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.antlr.runtime.CommonToken;
import org.antlr.runtime.Token;

import exm.stc.ast.FilePosition.LineMapping;
import exm.stc.ast.SwiftAST;
import exm.stc.ast.antlr.ExMParser;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidSyntaxException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Constants;
import exm.stc.common.lang.ProgramUsage;
import exm.stc.frontend.Context;

/**
 * Expansion of the arguments() declaration, which binds positional
 * command-line arguments to typed variables:
 *
 * <pre>
 *   arguments(string username, int v = 10);
 * </pre>
 *
 * is rewritten, before any semantic pass runs, into the equivalent
 * ordinary top-level statements:
 *
 * <pre>
 *   string username = argp(1);
 *   int    v        = string2int(argp(2, "10"));
 *   argp_check(2);
 * </pre>
 *
 * Formal parameters on main() mean the same thing and are expanded the same
 * way, with the declarations spliced in ahead of the main() definition:
 *
 * <pre>
 *   main(string username, int v) { ... }
 * </pre>
 *
 * The parameters are stripped from main(), which is then an ordinary
 * no-argument main() reading top-level variables.
 *
 * Positions are 1-based to match argp(), where argp(0) is the program name.
 * argp() itself reports the case of too few arguments; argp_check() reports
 * the case of too many.
 *
 * The companion flags() declaration binds the flagged arguments instead:
 *
 * <pre>
 *   flags(boolean emphasize, int a = 0);
 * </pre>
 *
 * becomes
 *
 * <pre>
 *   boolean emphasize = argv_contains("emphasize");
 *   int     a         = string2int(argv("a", "0"));
 *   argv_accept("emphasize", "a");
 * </pre>
 *
 * A boolean flag is true when the flag is given and false when it is not,
 * so it takes neither a value nor a default.  argv() reports a required
 * flag that was not given; argv_accept() reports a flag that was given but
 * not declared.
 *
 * A file argument names a file the program writes.  It becomes the mapping
 * of an unassigned file variable, so that nothing is read from the command
 * line but the name:
 *
 * <pre>
 *   arguments(file result);
 * </pre>
 *
 * becomes
 *
 * <pre>
 *   file result &lt;argp(1)&gt;;
 * </pre>
 *
 * A file the program reads is declared input instead, which binds an
 * ordinary file read through input():
 *
 * <pre>
 *   arguments(input data);
 * </pre>
 *
 * becomes
 *
 * <pre>
 *   file data = input(argp(1));
 * </pre>
 *
 * so that a file named on the command line but not actually present is
 * reported by input() rather than by whatever first tries to read it.
 * output is accepted as a synonym for file, for symmetry with input where
 * naming the direction reads better than leaving it implied.
 *
 * Each declaration may carry a documentation string, and arguments() may
 * carry a description of the program as a whole:
 *
 * <pre>
 *   arguments(string username : "your name here",
 *             description     : "greet the user");
 * </pre>
 *
 * From these the usage message printed by -h is assembled and handed to the
 * code generator in ProgramUsage.  Documentation is optional: the usage
 * message is generated whenever a program declares any arguments at all,
 * since the declarations alone already give the shape of the command line.
 */
public class Arguments {

  /** Name of the construct, for error messages */
  public static final String CONSTRUCT = "arguments()";

  /** Runtime function that fetches a positional argument as a string */
  private static final String ARGP = "argp";

  /** Runtime function that rejects surplus positional arguments */
  private static final String ARGP_CHECK = "argp_check";

  /** The construct main(...) uses to say the same thing, for error messages */
  public static final String MAIN_CONSTRUCT = "main() parameters";

  /** Name of the flagged-argument construct, for error messages */
  public static final String FLAGS_CONSTRUCT = "flags()";

  /** Runtime function that fetches a flagged argument as a string */
  private static final String ARGV = "argv";

  /** Runtime function that tests for the presence of a flag */
  private static final String ARGV_CONTAINS = "argv_contains";

  /** Runtime function that rejects flags outside a given list */
  private static final String ARGV_ACCEPT = "argv_accept";

  /** Child indices of a DEFINE_FUNCTION node, as read by ASTWalker */
  private static final int MAIN_OUTPUTS = 2;
  private static final int MAIN_INPUTS = 3;

  /** The help flag, which every program with declared arguments accepts */
  private static final String HELP_LABEL = "-h";
  private static final String HELP_DOC = "help";

  /** Program name used in the usage message if the input file is unknown */
  private static final String UNKNOWN_PROGRAM = "program.swift";

  /**
   * One declared argument, as it appears in the usage message.
   */
  private static class ArgDoc {
    /** Variable name, which for a flag is also the flag name */
    final String name;
    /** The type as the user declared it, shown in the positional list */
    final String type;
    /** A flag rather than a positional argument */
    final boolean flagged;
    /** A boolean flag, which is given without a value */
    final boolean bool;
    /** Documentation string, empty if the user gave none */
    final String doc;
    /** Default value as text, null if the argument is required */
    final String defaultVal;

    ArgDoc(String name, String type, boolean flagged, boolean bool,
           String doc, String defaultVal) {
      this.name = name;
      this.type = type;
      this.flagged = flagged;
      this.bool = bool;
      this.doc = (doc == null) ? "" : doc;
      this.defaultVal = defaultVal;
    }

    /** A boolean flag is never required: absent simply means false */
    boolean required() {
      return defaultVal == null && !bool;
    }

    /** How this argument is written on the command line */
    String label() {
      if (!flagged) {
        return name;
      }
      return bool ? "--" + name : "-" + name + "=" + name.toUpperCase();
    }

    /**
     * How this argument is named in the list below the synopsis: its
     * declared type, padded out to typeWidth, and then the way it is written
     * on the command line -- bare for a positional argument, as the flag
     * itself for a flag.
     */
    String listLabel(int typeWidth) {
      return pad(type, typeWidth) + (flagged ? label() : name + ":");
    }

    /** Documentation with the default value appended, as the list shows it */
    String listText() {
      String d = bool ? "false" : defaultVal;
      if (d == null) {
        return doc;
      }
      return doc.isEmpty() ? "default=" + d : doc + ": default=" + d;
    }
  }

  /**
   * The usage message under construction, filled in as the declarations are
   * expanded.  Positional arguments and flags are kept apart because they
   * are listed separately; within each list, expansion visits the
   * declarations in the order they were written.
   */
  private static class Usage {
    final List<ArgDoc> positional = new ArrayList<ArgDoc>();
    final List<ArgDoc> flagged = new ArrayList<ArgDoc>();
    /** Description of the program as a whole, null if not given */
    String description = null;
    /** Whether the program declared any command line arguments at all */
    boolean any = false;

    void add(ArgDoc arg) {
      (arg.flagged ? flagged : positional).add(arg);
    }
  }

  /**
   * The Swift type of the variable the declaration binds, which is not
   * always the word the user wrote: input and output are argument types
   * rather than Swift types, and both bind an ordinary file.
   */
  private static String swiftType(String typeName) {
    if (typeName.equals("input") || typeName.equals("output")) {
      return "file";
    }
    return typeName;
  }

  /**
   * Whether the argument names a file for the program to write rather than
   * a value for it to read.  Such an argument becomes the mapping of an
   * unassigned file variable: nothing is read from the command line but the
   * name, and the program itself supplies the contents.
   */
  private static boolean mappedType(String typeName) {
    return typeName.equals("file") || typeName.equals("output");
  }

  /**
   * The builtin that converts the string returned by argp() or argv() to
   * the declared type.  A null conversion means the argument is used as-is.
   * Not consulted for a mapped type, which reads no value at all.
   */
  private static String conversionFunction(String typeName) {
    if (typeName.equals("string")) {
      return null;
    } else if (typeName.equals("int")) {
      return "string2int";
    } else if (typeName.equals("float")) {
      return "string2float";
    } else if (typeName.equals("boolean")) {
      return "string2bool";
    } else if (typeName.equals("input")) {
      return "input";
    } else if (typeName.equals("url")) {
      return "input_url";
    } else {
      return null;
    }
  }

  private static boolean supportedType(String typeName) {
    return typeName.equals("string") || typeName.equals("int") ||
           typeName.equals("float") || typeName.equals("boolean") ||
           typeName.equals("input") || typeName.equals("url") ||
           mappedType(typeName);
  }

  private static final String SUPPORTED_TYPES =
      "string, int, float, boolean, file (or output), input, url";

  /**
   * Replace the module's arguments() declaration, or the parameters of its
   * main(), with the declarations they stand for.  Must run before any pass
   * walks the tree.
   *
   * @param context used for error messages
   * @param programTree the PROGRAM node of a parsed module
   * @param moduleName logical name of the module, for error positions
   * @param lineMapping line mapping of the module, for error positions
   * @param mainModule whether this is the main program rather than an import
   */
  public static void expandTopLevel(Context context, SwiftAST programTree,
      String moduleName, LineMapping lineMapping, boolean mainModule)
          throws UserException {
    assert(programTree.getType() == ExMParser.PROGRAM);

    // Forward scan first, so that a duplicate is reported at the position of
    // the second declaration rather than the first
    int argsIdx = -1;
    int flagsIdx = -1;
    int mainIdx = -1;
    for (int i = 0; i < programTree.getChildCount(); i++) {
      SwiftAST child = (SwiftAST) programTree.getChild(i);
      if (child.getType() == ExMParser.ARGUMENTS ||
          child.getType() == ExMParser.FLAGS) {
        boolean isFlags = child.getType() == ExMParser.FLAGS;
        String construct = isFlags ? FLAGS_CONSTRUCT : CONSTRUCT;
        context.syncFilePos(child, moduleName, lineMapping);
        if (!mainModule) {
          throw new UserException(context, construct + " declarations are" +
              " only allowed in the main program, not in an imported module");
        }
        if (isFlags ? flagsIdx >= 0 : argsIdx >= 0) {
          throw new UserException(context, "Only one " + construct +
              " declaration is allowed per program");
        }
        if (isFlags) {
          flagsIdx = i;
        } else {
          argsIdx = i;
        }
      } else if (mainModule && parameterizedMain(child)) {
        // Only one main() can be defined, so no duplicate check is needed:
        // a second definition is reported later as a double definition
        mainIdx = i;
      }
    }

    if (argsIdx >= 0 && mainIdx >= 0) {
      SwiftAST main = (SwiftAST) programTree.getChild(mainIdx);
      context.syncFilePos(main, moduleName, lineMapping);
      throw new UserException(context, "Cannot use both " + CONSTRUCT +
          " and " + MAIN_CONSTRUCT + " in the same program: they bind the" +
          " same command line arguments");
    }

    // Expand from the back, so that an expansion does not shift the index
    // of one still to be done.  Each construct is nonetheless visited
    // front-to-back internally, so the usage message lists the arguments in
    // the order they were declared.
    Usage usage = new Usage();
    int[] todo = sortDescending(argsIdx, flagsIdx, mainIdx);
    for (int idx: todo) {
      SwiftAST tree = (SwiftAST) programTree.getChild(idx);
      context.syncFilePos(tree, moduleName, lineMapping);
      SwiftAST expansion;
      if (idx == flagsIdx) {
        expansion = expandFlags(context, tree, usage);
      } else if (idx == argsIdx) {
        expansion = expandArguments(context, tree, usage);
      } else {
        expansion = expandMain(context, tree, usage);
      }
      programTree.replaceChildren(idx, idx, expansion);
    }

    // Guard against clobbering the main program's usage message from an
    // imported module, which cannot declare arguments of its own
    if (usage.any) {
      ProgramUsage.set(render(usage));
    }

    // Every documentation string the grammar allows should have been
    // consumed above; anything left is on a function that cannot use it
    checkNoStrayDocs(context, programTree, moduleName, lineMapping);
  }

  /**
   * Reject a documentation string anywhere it has no meaning.  The grammar
   * accepts one on any formal parameter, because arg_decl is shared by every
   * function definition, so the restriction to arguments(), flags() and
   * main() is enforced here instead.
   */
  private static void checkNoStrayDocs(Context context, SwiftAST tree,
      String moduleName, LineMapping lineMapping) throws UserException {
    if (tree.getType() == ExMParser.ARG_DOC) {
      context.syncFilePos(tree, moduleName, lineMapping);
      throw new UserException(context, "Argument documentation (: \"...\")" +
          " is only allowed in " + CONSTRUCT + ", " + FLAGS_CONSTRUCT +
          " and " + MAIN_CONSTRUCT);
    }
    for (int i = 0; i < tree.getChildCount(); i++) {
      checkNoStrayDocs(context, (SwiftAST) tree.getChild(i), moduleName,
                       lineMapping);
    }
  }

  /**
   * @return the given indices that are set, largest first
   */
  private static int[] sortDescending(int... indices) {
    int[] set = new int[indices.length];
    int n = 0;
    for (int idx: indices) {
      if (idx >= 0) {
        set[n++] = idx;
      }
    }
    int[] result = new int[n];
    System.arraycopy(set, 0, result, 0, n);
    // At most three elements, so an insertion sort is plenty
    for (int i = 1; i < n; i++) {
      int v = result[i];
      int j = i - 1;
      for (; j >= 0 && result[j] < v; j--) {
        result[j + 1] = result[j];
      }
      result[j + 1] = v;
    }
    return result;
  }

  /**
   * @return a nil-rooted tree whose children replace the FLAGS node
   */
  private static SwiftAST expandFlags(Context context, SwiftAST tree,
      Usage usage) throws UserException {
    assert(tree.getType() == ExMParser.FLAGS);
    Token pos = tree.getToken();
    usage.any = true;

    SwiftAST result = nil();
    int count = tree.getChildCount();
    SwiftAST[] names = new SwiftAST[count];

    for (int i = 0; i < count; i++) {
      SwiftAST decl = (SwiftAST) tree.getChild(i);
      if (decl.getType() == ExMParser.ARG_DESCRIPTION) {
        throw new UserException(context, FLAGS_CONSTRUCT + ": the program" +
            " description belongs in " + CONSTRUCT + ", which describes the" +
            " program as a whole");
      }
      result.addChild(expandOne(context, FLAGS_CONSTRUCT, decl, 0, true, pos,
                                usage));
      names[i] = stringLit(pos, decl.getChild(1).getChild(0).getText());
    }

    // Reject any flag that was not declared, mirroring argp_check()
    result.addChild(stmt(pos, call(pos, ARGV_ACCEPT, names)));

    return result;
  }

  /**
   * @return true if tree defines main() with input parameters, and so needs
   *         expanding.  A main() with outputs is left alone so that the
   *         existing "main() is not allowed to have ... arguments" error is
   *         what the user sees.
   */
  private static boolean parameterizedMain(SwiftAST tree) {
    if (tree.getType() != ExMParser.DEFINE_FUNCTION) {
      return false;
    }
    assert(tree.getChildCount() >= 5);
    if (!tree.getChild(0).getText().equals(Constants.MAIN_FUNCTION)) {
      return false;
    }
    return tree.getChild(MAIN_OUTPUTS).getChildCount() == 0 &&
           tree.getChild(MAIN_INPUTS).getChildCount() > 0;
  }

  /**
   * Strip the parameters from a main() definition and return a nil-rooted
   * tree holding the declarations they stand for, followed by main() itself.
   */
  private static SwiftAST expandMain(Context context, SwiftAST main,
      Usage usage) throws UserException {
    Token pos = ((SwiftAST) main.getChild(0)).getToken();
    SwiftAST inputs = (SwiftAST) main.getChild(MAIN_INPUTS);
    usage.any = true;

    SwiftAST result = nil();
    int count = inputs.getChildCount();
    for (int i = 0; i < count; i++) {
      result.addChild(expandOne(context, MAIN_CONSTRUCT,
                                (SwiftAST) inputs.getChild(i), i + 1, false,
                                pos, usage));
    }
    result.addChild(stmt(pos, call(pos, ARGP_CHECK, intLit(pos, count))));

    // main() is now an ordinary no-argument main() reading top-level vars
    while (inputs.getChildCount() > 0) {
      inputs.deleteChild(0);
    }
    result.addChild(main);

    return result;
  }

  /**
   * @return a nil-rooted tree whose children replace the ARGUMENTS node
   */
  private static SwiftAST expandArguments(Context context, SwiftAST tree,
      Usage usage) throws UserException {
    assert(tree.getType() == ExMParser.ARGUMENTS);
    Token pos = tree.getToken();
    usage.any = true;

    SwiftAST result = nil();
    int count = tree.getChildCount();
    // The description is not an argument, so it takes no position and is
    // not counted towards the arity check
    int position = 0;

    for (int i = 0; i < count; i++) {
      SwiftAST decl = (SwiftAST) tree.getChild(i);
      if (decl.getType() == ExMParser.ARG_DESCRIPTION) {
        if (usage.description != null) {
          throw new UserException(context, CONSTRUCT + ": only one" +
              " description is allowed per program");
        }
        usage.description = docText(context, (SwiftAST) decl.getChild(0));
        continue;
      }
      // Position 0 is the program name
      result.addChild(expandOne(context, CONSTRUCT, decl, ++position, false,
                                pos, usage));
    }

    // Reject surplus arguments.  Deliberately only the upper bound: too few
    // is already reported by argp() itself, and checking both would race.
    result.addChild(stmt(pos, call(pos, ARGP_CHECK, intLit(pos, position))));

    return result;
  }

  /**
   * @param argDoc an ARG_DOC node
   * @return the documentation string it holds
   */
  private static String docText(Context context, SwiftAST argDoc)
      throws UserException {
    assert(argDoc.getType() == ExMParser.ARG_DOC);
    try {
      return Literals.extractStringLit(context, (SwiftAST) argDoc.getChild(0));
    } catch (InvalidSyntaxException e) {
      throw new UserException(context, e.getMessage());
    }
  }

  /**
   * Expand one declared argument.  Handles both AST shapes that reach here:
   * arguments_decl, whose type child is a plain type_prefix, and arg_decl
   * from main()'s parameter list, whose type child is a MULTI_TYPE and which
   * may additionally carry array markers or a VARARGS marker.
   *
   * @param construct name of the construct, for error messages
   * @param decl a DECLARATION node
   * @param position 1-based command-line position; ignored when flagged
   * @param flagged true for flags(), where the variable name is the flag
   *                name and the position is irrelevant
   * @param usage collects what this argument contributes to the usage message
   * @return DECLARATION node with an initializer that reads the argument
   */
  private static SwiftAST expandOne(Context context, String construct,
      SwiftAST decl, int position, boolean flagged, Token pos, Usage usage)
          throws UserException {
    assert(decl.getType() == ExMParser.DECLARATION);
    assert(decl.getChildCount() >= 2);

    SwiftAST typeT = (SwiftAST) decl.getChild(0);
    SwiftAST restT = (SwiftAST) decl.getChild(1);
    assert(restT.getType() == ExMParser.DECLARE_VARIABLE_REST);
    String varName = restT.getChild(0).getText();

    if (typeT.getType() == ExMParser.MULTI_TYPE) {
      // A union type makes no sense for a command line argument
      if (typeT.getChildCount() != 1) {
        throw badType(context, construct, varName);
      }
      typeT = (SwiftAST) typeT.getChild(0);
    }
    // An array marker, e.g. the [] of "int A[]"
    if (restT.getChildCount() != 1) {
      throw badType(context, construct, varName);
    }
    if (typeT.getType() != ExMParser.ID) {
      // e.g. a parameterized type such as set<int>
      throw badType(context, construct, varName);
    }
    String typeName = typeT.getText();
    if (!supportedType(typeName)) {
      throw new UserException(context, construct + ": unsupported type '" +
          typeT.getText() + "' for argument '" + varName + "'." +
          "  Supported types are: " + SUPPORTED_TYPES);
    }
    // Emit the bound type rather than the argument type, so that "input"
    // and "output" become "file"
    typeT = node(pos, ExMParser.ID, swiftType(typeName));

    // Any remaining child is a VARARGS marker, a documentation string, or
    // a default value
    SwiftAST defaultT = null;
    String doc = null;
    for (int i = 2; i < decl.getChildCount(); i++) {
      SwiftAST extra = (SwiftAST) decl.getChild(i);
      if (extra.getType() == ExMParser.VARARGS) {
        throw new UserException(context, construct + ": argument '" + varName +
            "' cannot be variable-length: each argument takes one position");
      }
      if (extra.getType() == ExMParser.ARG_DOC) {
        doc = docText(context, extra);
        continue;
      }
      defaultT = extra;
    }

    if (flagged && (varName.equals("h") || varName.equals("help"))) {
      throw new UserException(context, construct + ": flag '" + varName +
          "' conflicts with the " + HELP_LABEL + "/--help flag, which every" +
          " program that declares arguments accepts");
    }

    // A boolean flag is true when present and false when absent, so a
    // default value would have nothing left to say
    boolean boolFlag = flagged && typeName.equals("boolean");
    if (boolFlag && defaultT != null) {
      throw new UserException(context, construct + ": boolean flag '" +
          varName + "' cannot have a default value: it is true when the" +
          " flag is given and false when it is not");
    }
    String defaultVal = (defaultT == null) ? null :
        renderDefault(context, construct, typeName, varName, defaultT);

    usage.add(new ArgDoc(varName, typeName, flagged, boolFlag, doc,
                         defaultVal));

    // argp(position) / argv("name"), with the default as a second argument.
    // A boolean flag has no such call: it is read by its presence alone
    SwiftAST fetch = null;
    if (!boolFlag) {
      SwiftAST key = flagged ? stringLit(pos, varName) : intLit(pos, position);
      String getter = flagged ? ARGV : ARGP;
      if (defaultVal != null) {
        fetch = call(pos, getter, key, stringLit(pos, defaultVal));
      } else {
        fetch = call(pos, getter, key);
      }
    }

    SwiftAST result = node(pos, ExMParser.DECLARATION, "DECLARATION");
    result.addChild(typeT);

    if (mappedType(typeName)) {
      // ^( DECLARATION type ^( DECLARE_VARIABLE_REST v ^( MAPPING e ) ) ),
      // an unassigned mapped file: the command line names it, the program
      // writes it
      restT.addChild(mapping(pos, fetch));
      result.addChild(restT);
      return result;
    }

    SwiftAST init;
    if (boolFlag) {
      init = call(pos, ARGV_CONTAINS, stringLit(pos, varName));
    } else {
      // Convert from string to the declared type, if needed
      String conv = conversionFunction(typeName);
      init = (conv == null) ? fetch : call(pos, conv, fetch);
    }

    // ^( DECLARATION type ^( DECLARE_ASSIGN ^( DECLARE_VARIABLE_REST v ) e ) )
    SwiftAST assign = node(pos, ExMParser.DECLARE_ASSIGN, "DECLARE_ASSIGN");
    assign.addChild(restT);
    assign.addChild(init);
    result.addChild(assign);
    return result;
  }

  private static UserException badType(Context context, String construct,
      String varName) {
    return new UserException(context, construct + ": unsupported type for" +
        " argument '" + varName + "'.  Supported types are: " +
        SUPPORTED_TYPES);
  }

  /**
   * argp() takes its default value as a string, so a typed literal default
   * has to be rendered back to text.  Extracting it by declared type also
   * catches a default of the wrong type here, rather than as a confusing
   * type error on a statement the user did not write.
   */
  private static String renderDefault(Context context, String construct,
      String typeName, String varName, SwiftAST tree) throws UserException {
    String bad = construct + ": default value for argument '" + varName +
                 "' must be a literal " + typeName;
    try {
      if (typeName.equals("int")) {
        Long v = Literals.extractIntLit(context, tree);
        if (v == null) {
          throw new UserException(context, bad);
        }
        return Long.toString(v);
      } else if (typeName.equals("float")) {
        Double v = Literals.extractFloatLit(context, tree);
        if (v == null) {
          // An int literal is an acceptable float default
          Long i = Literals.extractIntLit(context, tree);
          if (i == null) {
            throw new UserException(context, bad);
          }
          return Double.toString(i.doubleValue());
        }
        return Double.toString(v);
      } else if (typeName.equals("boolean")) {
        if (tree.getType() != ExMParser.BOOL_LITERAL) {
          throw new UserException(context, bad);
        }
        return Literals.extractBoolLit(context, tree);
      } else {
        // string, file, output, input and url all take a plain string
        String v = Literals.extractStringLit(context, tree);
        if (v == null) {
          throw new UserException(context, construct + ": default value for" +
              " argument '" + varName + "' must be a literal string");
        }
        return v;
      }
    } catch (InvalidSyntaxException e) {
      throw new UserException(context, bad + ": " + e.getMessage());
    }
  }

  /**
   * Render the usage message that -h prints, for example
   *
   * <pre>
   * workflow usage:
   * swift-t a4.swift [-h] [--emphasize] [-a=A] username v
   *
   * increment the value for the user
   *
   * positional arguments:
   *   string  username:   your name here
   *   int     v:          the value to increment
   *
   * flagged arguments:
   *           -h          help
   *   boolean --emphasize be enthusiastic: default=false
   *   int     -a=A        the addend: default=0
   * </pre>
   *
   * The synopsis names the flags before the positional arguments, since a
   * flag may be written anywhere on the command line, and each list is in
   * declaration order.  Each argument is prefixed with its declared type,
   * and the two lists share both the type column and the one after it, so
   * that the whole message reads as three columns.  Only -h has a blank
   * type, having never been declared.
   */
  private static String render(Usage usage) {
    StringBuilder sb = new StringBuilder();
    sb.append("workflow usage:\n");

    sb.append("swift-t ").append(programName()).append(' ')
      .append(optional(HELP_LABEL));
    for (ArgDoc arg: usage.flagged) {
      sb.append(' ').append(arg.required() ? arg.label()
                                           : optional(arg.label()));
    }
    for (ArgDoc arg: usage.positional) {
      sb.append(' ').append(arg.required() ? arg.label()
                                           : optional(arg.label()));
    }
    sb.append('\n');

    if (usage.description != null) {
      sb.append('\n').append(usage.description).append('\n');
    }

    int typeWidth = typeWidth(usage);
    int width = labelWidth(usage, typeWidth);
    if (!usage.positional.isEmpty()) {
      sb.append("\npositional arguments:\n");
      for (ArgDoc arg: usage.positional) {
        entry(sb, arg.listLabel(typeWidth), arg.listText(), width);
      }
    }

    // Always present: every program with declared arguments accepts -h
    sb.append("\nflagged arguments:\n");
    entry(sb, helpLabel(typeWidth), HELP_DOC, width);
    for (ArgDoc arg: usage.flagged) {
      entry(sb, arg.listLabel(typeWidth), arg.listText(), width);
    }

    return sb.toString();
  }

  /**
   * @return the name to show in the synopsis.  The basename, so that the
   *         line reads the same whether the file was named by a relative or
   *         an absolute path.
   */
  private static String programName() {
    String path = Settings.get(Settings.INPUT_FILENAME);
    if (path == null || path.isEmpty()) {
      return UNKNOWN_PROGRAM;
    }
    return new File(path).getName();
  }

  private static String optional(String label) {
    return "[" + label + "]";
  }

  /** Left-justify s in a column of the given width */
  private static String pad(String s, int width) {
    StringBuilder sb = new StringBuilder(s);
    for (int i = s.length(); i < width; i++) {
      sb.append(' ');
    }
    return sb.toString();
  }

  /**
   * Width of the type column, shared by both lists.  Zero if no argument was
   * declared at all, so that a program whose only flag is -h -- which has no
   * declared type -- is not indented past an empty column.
   */
  private static int typeWidth(Usage usage) {
    int width = 0;
    for (ArgDoc arg: usage.positional) {
      width = Math.max(width, arg.type.length());
    }
    for (ArgDoc arg: usage.flagged) {
      width = Math.max(width, arg.type.length());
    }
    // At least one space between the widest type and the name it qualifies
    return (width == 0) ? 0 : width + 1;
  }

  /**
   * How -h is named in the flag list: it has no declared type, so its type
   * column is blank
   */
  private static String helpLabel(int typeWidth) {
    return pad("", typeWidth) + HELP_LABEL;
  }

  /** Width of the label column, shared by both lists so that they align */
  private static int labelWidth(Usage usage, int typeWidth) {
    int width = helpLabel(typeWidth).length();
    for (ArgDoc arg: usage.positional) {
      width = Math.max(width, arg.listLabel(typeWidth).length());
    }
    for (ArgDoc arg: usage.flagged) {
      width = Math.max(width, arg.listLabel(typeWidth).length());
    }
    // At least one space between the widest label and its documentation
    return width + 1;
  }

  /** One line of a list, or a bare label if it has nothing to say */
  private static void entry(StringBuilder sb, String label, String text,
      int width) {
    sb.append("  ").append(label);
    if (!text.isEmpty()) {
      for (int i = label.length(); i < width; i++) {
        sb.append(' ');
      }
      sb.append(text);
    }
    sb.append('\n');
  }

  /*
   * AST construction helpers.  Every synthesized node carries the line and
   * column of the arguments() keyword so that errors and syncFilePos() point
   * somewhere the user can see.
   */

  private static SwiftAST node(Token pos, int type, String text) {
    CommonToken tok = new CommonToken(type, text);
    tok.setLine(pos.getLine());
    tok.setCharPositionInLine(pos.getCharPositionInLine());
    return new SwiftAST(tok);
  }

  /** Nil-rooted list node: ANTLR splices its children into the parent */
  private static SwiftAST nil() {
    return new SwiftAST(null);
  }

  /** ^( CALL_FUNCTION ID[name] ^( ARGUMENT_LIST args... ) ) */
  private static SwiftAST call(Token pos, String name, SwiftAST... args) {
    SwiftAST argList = node(pos, ExMParser.ARGUMENT_LIST, "ARGUMENT_LIST");
    for (SwiftAST arg: args) {
      argList.addChild(arg);
    }
    SwiftAST result = node(pos, ExMParser.CALL_FUNCTION, "CALL_FUNCTION");
    result.addChild(node(pos, ExMParser.ID, name));
    result.addChild(argList);
    return result;
  }

  /** ^( MAPPING expr ): the filename a file variable is mapped to */
  private static SwiftAST mapping(Token pos, SwiftAST expr) {
    SwiftAST result = node(pos, ExMParser.MAPPING, "MAPPING");
    result.addChild(expr);
    return result;
  }

  /** ^( EXPR_STMT expr ) */
  private static SwiftAST stmt(Token pos, SwiftAST expr) {
    SwiftAST result = node(pos, ExMParser.EXPR_STMT, "EXPR_STMT");
    result.addChild(expr);
    return result;
  }

  /** ^( INT_LITERAL DECIMAL_INT[n] ) */
  private static SwiftAST intLit(Token pos, long n) {
    SwiftAST result = node(pos, ExMParser.INT_LITERAL, "INT_LITERAL");
    result.addChild(node(pos, ExMParser.DECIMAL_INT, Long.toString(n)));
    return result;
  }

  /** ^( STRING_LITERAL STRING["\"...\""] ) */
  private static SwiftAST stringLit(Token pos, String s) {
    SwiftAST result = node(pos, ExMParser.STRING_LITERAL, "STRING_LITERAL");
    result.addChild(node(pos, ExMParser.STRING, quote(s)));
    return result;
  }

  /**
   * Re-escape a string so that Literals.unescapeString() recovers it.
   */
  private static String quote(String s) {
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

  private Arguments() {
    throw new STCRuntimeError("Not instantiable");
  }
}
