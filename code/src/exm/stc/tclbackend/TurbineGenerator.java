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
/**
 * This module handles the higher-level logic of generating Turbine
 * code. More mechanical aspects of code generation are handled in
 * the classes in the exm.tclbackend.tree module
 */
package exm.stc.tclbackend;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.collect.ListMultimap;

import exm.stc.common.CompilerBackend;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCFatal;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.AsyncExecutor;
import exm.stc.common.lang.CompileTimeArgs;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ExecContext.WorkContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.LocalForeignFunction;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Operators.UpdateMode;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.RequiredPackage;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FileKind;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.NestedContainerInfo;
import exm.stc.common.lang.Types.PrimType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Unimplemented;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarCount;
import exm.stc.common.lang.WrappedForeignFunction;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.tree.TurbineOp.RefCountOp.RCDir;
import exm.stc.tclbackend.TclTemplateProcessor.TemplateArg;
import exm.stc.tclbackend.Turbine.CacheMode;
import exm.stc.tclbackend.Turbine.RuleProps;
import exm.stc.tclbackend.Turbine.TypeName;
import exm.stc.tclbackend.Turbine.XptPersist;
import exm.stc.tclbackend.tree.Command;
import exm.stc.tclbackend.tree.Comment;
import exm.stc.tclbackend.tree.Dict;
import exm.stc.tclbackend.tree.DictFor;
import exm.stc.tclbackend.tree.Expand;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.Expression.ExprContext;
import exm.stc.tclbackend.tree.ForEach;
import exm.stc.tclbackend.tree.ForLoop;
import exm.stc.tclbackend.tree.If;
import exm.stc.tclbackend.tree.LiteralFloat;
import exm.stc.tclbackend.tree.LiteralInt;
import exm.stc.tclbackend.tree.PackageRequire;
import exm.stc.tclbackend.tree.Proc;
import exm.stc.tclbackend.tree.Sequence;
import exm.stc.tclbackend.tree.SetVariable;
import exm.stc.tclbackend.tree.Switch;
import exm.stc.tclbackend.tree.TclExpr;
import exm.stc.tclbackend.tree.TclList;
import exm.stc.tclbackend.tree.TclString;
import exm.stc.tclbackend.tree.TclTarget;
import exm.stc.tclbackend.tree.TclTree;
import exm.stc.tclbackend.tree.Text;
import exm.stc.tclbackend.tree.Token;
import exm.stc.tclbackend.tree.Value;
import exm.stc.tclbackend.tree.WhileLoop;
import exm.stc.ui.ExitCode;

public class TurbineGenerator implements CompilerBackend {

  /**
   * Stored options
   */
  @SuppressWarnings("unused")
  private CodeGenOptions options = null;

  private ForeignFunctions foreignFuncs = null;

  /**
     This prevents duplicate "lappend auto_path" statements
     We use a List because these should stay in order
   */
  private final List<String> autoPaths = new ArrayList<String>();

  private static final String TCLTMP_SPLITLEN = "tcltmp:splitlen";
  private static final String TCLTMP_SPLITEND = "tcltmp:splitend";
  private static final String TCLTMP_CONTAINER_SIZE = "tcltmp:container_sz";
  private static final String TCLTMP_ARRAY_CONTENTS = "tcltmp:contents";
  private static final String TCLTMP_RETRIEVED = "tcltmp:retrieved";
  private static final String TCLTMP_RANGE_LO = "tcltmp:lo";
  private static final Value TCLTMP_RANGE_LO_V = new Value(TCLTMP_RANGE_LO);
  private static final String TCLTMP_RANGE_HI = "tcltmp:hi";
  private static final Value TCLTMP_RANGE_HI_V = new Value(TCLTMP_RANGE_HI);
  private static final String TCLTMP_RANGE_INC = "tcltmp:inc";
  private static final Value TCLTMP_RANGE_INC_V = new Value(TCLTMP_RANGE_INC);
  private static final String TCLTMP_ITERSLEFT = "tcltmp:itersleft";
  private static final String TCLTMP_ITERSTOTAL = "tcltmp:iterstotal";
  private static final String TCLTMP_ITERS = "tcltmp:iters";
  private static final String TCLTMP_FLOAT_RANGE_ITER = "tcltmp:float_range_iter";
  private static final String TCLTMP_FLOAT_RANGE_ITERMAX = "tcltmp:float_range_itermax";
  private static final String TCLTMP_INIT_REFCOUNT = "tcltmp:init_rc";
  private static final String TCLTMP_SPLIT_START = "tcltmp:splitstart";
  private static final String TCLTMP_SKIP = "tcltmp:skip";
  private static final String TCLTMP_IGNORE = "tcltmp:ignore";

  private static final String ENTRY_FUNCTION_NAME = "swift:main";
  private static final String CONSTINIT_FUNCTION_NAME = "swift:constants";

  private final String timestamp;
  private final Logger logger;

  /**
     Our output Tcl
     Convenience reference to bottom of pointStack
   */
  private final Sequence tree = new Sequence();


  /**
   * For function that initializes globals
   */
  private final Sequence globInit = new Sequence();

  /**
     Stack for previous values of point.
     First entry is the sequence
     Second entry is a list of things to add at end of sequence
   */
  private final StackLite<Pair<Sequence, Sequence>> pointStack =
      new StackLite<Pair<Sequence, Sequence>>();

  /**
   * Shortcut for current sequence in pointStack
   * @return
   */
  private Sequence point() {
    return pointStack.peek().val1;
  }

  private void pointPush(Sequence point) {
    pointStack.push(Pair.create(point, new Sequence()));
  }

  /**
   * Shortcut to add to current point in TclTree
   */
  private void pointAdd(TclTree cmd) {
    point().add(cmd);
  }

  /**
   * cmd will be added to point end upon popping
   * @param cmd
   */
  private void pointAddEnd(TclTree cmd) {
    pointStack.peek().val2.add(cmd);
  }

  /**
   * Remove current sequence after adding any deferred commands
   * @return
   */
  private Sequence pointPop() {
    Pair<Sequence, Sequence> p = pointStack.pop();
    p.val1.append(p.val2); // Add in things destined for end of point
    return p.val1;
  }

  /**
   * Stack for (name, execImmediate) of loop functions
   */
  private final StackLite<EnclosingLoop> loopStack = new StackLite<EnclosingLoop>();

  /**
   * Stack for function ids
   */
  private final StackLite<FnID> functionStack = new StackLite<FnID>();

  /**
   * Stack for what context we're in.
   */
  private final StackLite<ExecContext> execContextStack = new StackLite<ExecContext>();

  private final String turbineVersion = Settings.get(Settings.TURBINE_VERSION);

  private final HashSet<String> usedTclFunctionNames = new HashSet<String>();

  /**
   * Track work contexts this program may execute things in.
   */
  private final Set<ExecContext> usedExecContexts = new HashSet<ExecContext>();
  {
    // Always use control
    usedExecContexts.add(ExecContext.control());
  }

  private final TurbineStructs structTypes = new TurbineStructs();

  private final List<WorkContext> customWorkTypes = new ArrayList<WorkContext>();

  /**
   * Tcl symbol names for builtins
   * Swift function name -> (Tcl proc name, Tcl op template)
   */
  private final HashMap<FnID, Pair<TclOpTemplate, TclFunRef>> tclFuncSymbols
          = new HashMap<FnID, Pair<TclOpTemplate, TclFunRef>>();


  /**
   * First valid debug symbol is 1 (0 is reserved to represent no debug symbol)
   */
  private static final int FIRST_DEBUG_SYMBOL = 1;
  /**
    Assign debug symbol numbers.
   */
  private int nextDebugSymbol = FIRST_DEBUG_SYMBOL;

  /**
   * Map (function, variable) to debug symbol.
   * Function is "" if not inside scope of function.
   */
  private final HashMap<Pair<FnID, Var>, Integer> debugSymbolIndex =
                 new HashMap<Pair<FnID, Var>, Integer>();

  private static class DebugSymbolData {
    public DebugSymbolData(String name, String context) {
      this.name = name;
      this.context = context;
    }

    public final String name;
    public final String context;
  }

  /**
   * List of all debug symbols created
   */
  private final List<Pair<Integer, DebugSymbolData>> debugSymbols
              = new ArrayList<Pair<Integer, DebugSymbolData>>();

  private final List<VarDecl> globalVars = new ArrayList<VarDecl>();

  public TurbineGenerator(Logger logger, String timestamp)
  {
    this.logger = logger;
    this.timestamp = timestamp;
    pointPush(tree);

    execContextStack.push(ExecContext.control());
  }

  @Override
  public void initialize(CodeGenOptions options, ForeignFunctions foreignFuncs) {
    this.options = options;
    this.foreignFuncs = foreignFuncs;

    //String[] rpaths = Settings.getRpaths();
    File input_file   = new File(Settings.get(Settings.INPUT_FILENAME));
    File output_file  = new File(Settings.get(Settings.OUTPUT_FILENAME));
    tree.add(new Text(""));
    tree.add(new Comment("Generated by stc version " + Settings.get(Settings.STC_VERSION)));
    tree.add(new Comment("date                    : " + timestamp));
    tree.add(new Comment("Turbine version         : " + this.turbineVersion));
    tree.add(new Comment("Input filename          : " + input_file.getAbsolutePath() ));
    tree.add(new Comment("Output filename         : " + output_file.getAbsolutePath() ));
    tree.add(new Comment("STC home                : " + Settings.get(Settings.STC_HOME)) );
    tree.add(new Comment("Turbine home            : " + Settings.get(Settings.TURBINE_HOME)) );
    tree.add(new Comment("Compiler settings:"));
    for (String key: Settings.getKeys()) {
      tree.add(new Comment(String.format("%-30s: %s", key, Settings.get(key))));
    }
    tree.add(new Text(""));

    tree.add(new Comment("Metadata:"));
    for (Pair<String, String> kv: Settings.getMetadata()) {
      tree.add(new Comment(String.format("%-30s: %s", kv.val1, kv.val2)));
    }

    tree.add(new Text(""));

    addAutoPaths();

    tree.add(new Command("package require turbine", turbineVersion));
    tree.add(new Command("namespace import turbine::*"));
    tree.add(new Text(""));

    Proc globInitProc = new Proc(CONSTINIT_FUNCTION_NAME, usedTclFunctionNames,
                              new ArrayList<String>(), globInit);
    globInit.add(Turbine.turbineLog("function:"+CONSTINIT_FUNCTION_NAME));
    tree.add(globInitProc);
  }

  private void addAutoPaths() {
    String[] rpaths = Settings.getRpaths();
    // Uniquify:
    for (String rpath : rpaths)
      if (rpath.length() > 0)
        if (! autoPaths.contains(rpath))
          autoPaths.add(rpath);
    if (autoPaths.size() > 0)
      tree.add(new Comment("rpath entries"));
    // Add Tcl, put path in quotes
    for (String p : autoPaths)
      tree.add(new Command("lappend auto_path \"" + p + "\""));
  }

  private void turbineStartup() {
    // TODO: don't need defaults anymore with newer Turbine engines,
    //       remove once we move to new version.
    tree.add(new Command("turbine::defaults"));
    tree.add(initCustomWorkTypes());
    tree.add(new Command("turbine::init $servers \"Swift\""));
    tree.add(checkWorkTypes());

    if (Settings.getBooleanUnchecked(Settings.ENABLE_REFCOUNTING)) {
      tree.add(Turbine.enableReferenceCounting());
    }

    // Initialize struct types
    tree.append(structTypeDeclarations());

    // Insert code to check versions
    tree.add(Turbine.checkConstants());

    tree.append(compileTimeArgs());

    tree.add(initGlobalVars());

    // Global vars need to be allocated debug symbols
    tree.append(debugSymbolInit());

    tree.add(new Command("turbine::start " + ENTRY_FUNCTION_NAME +
                                        " " + CONSTINIT_FUNCTION_NAME));
    tree.add(new Command("turbine::finalize"));
  }

  /**
   * Define any additional custom work types for runtime
   * @return
   */
  private Sequence initCustomWorkTypes() {
    List<Expression> args = new ArrayList<Expression>();
    for (WorkContext wt: customWorkTypes) {
      args.add(Turbine.nonDefaultWorkTypeName(wt));
    }
    if (args.isEmpty()) {
      return new Sequence();
    } else {
      return new Sequence(Turbine.declareCustomWorkTypes(args));
    }
  }

  private TclTree checkWorkTypes() {
    List<Expression> checkExprs = new ArrayList<Expression>();
    for (ExecContext worker: usedExecContexts) {
      if (worker.isControlContext() ||
          worker.isDefaultWorkContext() ||
          worker.isWildcardContext()) {
        // Don't need to check
      } else {
        checkExprs.add(Turbine.nonDefaultWorkTypeName(worker.workContext()));
      }
    }

    if (checkExprs.size() > 0) {
      return new Command("turbine::check_can_execute", checkExprs);
    } else {
      return new Sequence();
    }
  }

  private Sequence compileTimeArgs() {
    Map<String, String> args = CompileTimeArgs.getCompileTimeArgs();
    Sequence seq = new Sequence();
    if (!args.isEmpty()) {
      for (String key: args.keySet()) {
        TclString argName = new TclString(key, true);
        TclString argVal = new TclString(args.get(key), true);
        seq.add(Turbine.addConstantArg(argName, argVal));
      }
    }
    return seq;
  }

  /**
   * Check that we finished code generation in a valid state
   */
  @Override
  public void finalize() {
    pointPop();
    assert(pointStack.isEmpty());

    // Generate startup code at bottom of file
    turbineStartup();
  }

  /**
     Generate and output Tcl from  our internal TclTree
   * @throws IOException
   */
  @Override
  public void generate(OutputStream output) throws IOException {
    StringBuilder sb = new StringBuilder(10*1024);
    try
    {
      tree.appendTo(sb);
    }
    catch (Exception e)
    {
      System.out.println("CODE GENERATOR INTERNAL ERROR");
      System.out.println(e.getMessage());
      e.printStackTrace();
      System.out.println("code generated before error:");
      System.out.println(sb);
      System.out.println("exiting");
      throw new STCFatal(ExitCode.ERROR_INTERNAL.code());
    }
    OutputStreamWriter w = new OutputStreamWriter(output);
    w.write(sb.toString());
    // Check everything is flushed to underlying stream
    w.flush();
  }


  @Override
  public void declareStructType(StructType st) {
    structTypes.newType(st);
  }

  @Override
  public void declareWorkType(WorkContext workType) {
    customWorkTypes.add(workType);
  }

  private Sequence structTypeDeclarations() {
    Sequence result = new Sequence();

    // TODO: sort struct types in order in case one type appears
    // inside another. I think for now the frontend will guarantee
    // this order, so we'll just check that it's true.
    // Note that we can't have recursive struct types.
    Set<StructType> declared = new HashSet<StructType>();

    for (Pair<Integer, StructType> type: structTypes.getTypeList()) {
      int typeId = type.val1;
      StructType st = type.val2;

      // Tcl expression list describing fields;
      List<Expression> fieldInfo = new ArrayList<Expression>();
      for (StructField field: st.fields()) {
        // Field name and type
        fieldInfo.add(new TclString(field.name(), true));
        fieldInfo.addAll(TurbineTypes.dataDeclFullType(field.type()));
        if (Types.isStruct(field.type())) {
          assert(declared.contains(field.type())) :
            field.type() + " struct type was not initialized";
        }
      }

      Command decl = Turbine.declareStructType(new LiteralInt(typeId),
          TurbineTypes.structTypeName(st), new TclList(fieldInfo));
      result.add(decl);
      declared.add(st);
    }
    return result;
  }

  private Sequence debugSymbolInit() {
    Sequence seq = new Sequence();
    for (Pair<Integer, DebugSymbolData> e: debugSymbols) {
      Integer symbol = e.val1;
      DebugSymbolData data = e.val2;

      seq.add(Turbine.addDebugSymbol(symbol, data.name, data.context));
    }
    return seq;
  }

  private int nextDebugSymbol(Var var) {
    int symbol = nextDebugSymbol++;

    DebugSymbolData data = debugSymbolData(var);
    debugSymbols.add(Pair.create(symbol, data));

    FnID function;
    if (functionStack.isEmpty()) {
      function = null;
    } else {
      function = functionStack.peek();
    }
    debugSymbolIndex.put(Pair.create(function, var), symbol);

    return symbol;
  }

  /**
   * @param var
   * @return a string suitable for describing debug symbol
   */
  private DebugSymbolData debugSymbolData(Var var) {
    return new DebugSymbolData(var.name(), var.provenance().conciseFormat());
  }

  @Override
  public void declare(List<VarDecl> decls) {

    List<TclList> batchedArgs = new ArrayList<TclList>();
    List<String> batchedVarNames = new ArrayList<String>();

    List<TclList> batchedFileArgs = new ArrayList<TclList>();
    List<String> batchedFileVarNames = new ArrayList<String>();
    List<Boolean> batchedFileIsMappeds = new ArrayList<Boolean>();

    for (VarDecl decl: decls) {
      Var var = decl.var;
      Arg initReaders = decl.initReaders;
      Arg initWriters = decl.initWriters;

      assert(!var.mappedDecl() || Types.isMappable(var.type()));
      if (var.storage() == Alloc.ALIAS) {
        assert(initReaders == null && initWriters == null);
        continue;
      }
      // For now, just add provenance info as a comment
      pointAdd(new Comment("Var: " + var.type().typeName() + " " +
                prefixVar(var.name()) + " " + var.provenance().logFormat()));

      if (var.storage().isGlobal()) {
        // If global, it should already be in TCL global scope, just need to
        // make sure that we've imported it
        pointAdd(Turbine.makeTCLGlobal(prefixVar(var)));
        continue;
      }

      if (!Settings.getBooleanUnchecked(Settings.ENABLE_REFCOUNTING)) {
        // Have initial* set to regular amount to avoid bugs with reference counting
        initReaders = Arg.ONE;
      }

      if (Types.isFile(var)) {
        batchedFileArgs.add(createArgs(var, initReaders, initWriters));
        batchedFileVarNames.add(prefixVar(var));
        batchedFileIsMappeds.add(var.mappedDecl());
      } else if (Types.isPrimFuture(var) || Types.isPrimUpdateable(var) ||
          Types.isArray(var) || Types.isRef(var) || Types.isBag(var) ||
          Types.isStruct(var)) {
        batchedArgs.add(createArgs(var, initReaders, initWriters));
        batchedVarNames.add(prefixVar(var));
      } else if (Types.isPrimValue(var) || Types.isContainerLocal(var) ||
                  Types.isStructLocal(var)) {
        assert(var.storage() == Alloc.LOCAL);
        // don't need to do anything
      } else {
        throw new STCRuntimeError("Code generation not supported for declaration " +
        		"of type: " + var.type().typeName());
      }
    }

    if (!batchedArgs.isEmpty()) {
      pointAdd(Turbine.batchDeclare(batchedVarNames, batchedArgs));

      pointAdd(logVariableCreation(batchedVarNames));
    }

    if (!batchedFileArgs.isEmpty()) {
      pointAdd(Turbine.batchDeclareFiles(
          batchedFileVarNames, batchedFileArgs, batchedFileIsMappeds));
    }

  private TclList createArgs(Var var, Arg initReaders, Arg initWriters) {
    List<Expression> createArgs = new ArrayList<Expression>();
    createArgs.addAll(TurbineTypes.dataDeclFullType(var.type()));
    createArgs.add(argToExpr(initReaders));
    createArgs.add(argToExpr(initWriters));
    createArgs.add(new LiteralInt(nextDebugSymbol(var)));
    TclList createArgsL = new TclList(createArgs);
    return createArgsL;
  }

  private Sequence logVariableCreation(List<String> varNames) {
    // Log in small batches to avoid turbine log limitations
    // and overly long lines
    // TODO: deprecate in favour of debug symbols
    Sequence result = new Sequence();
    final int logBatch = 5;
    for (int start = 0; start < varNames.size(); start += logBatch) {
      List<Expression> logExprs = new ArrayList<Expression>();
      logExprs.add(new Token("allocated"));
      int end = Math.min(varNames.size(), start + logBatch);
      for (String tclVarName: varNames.subList(start, end)) {
        logExprs.add(new Token(" " + tclVarName + "=<"));
        logExprs.add(new Value(tclVarName));
        logExprs.add(new Token(">"));
      }
      TclString msg = new TclString(logExprs, ExprContext.VALUE_STRING);
      result.add(Turbine.log(msg));
    }
    return result;
  }

  @Override
  public void modifyRefCounts(List<DirRefCount> refcounts) {
    // TODO: could combine read/write refcounts for same variable
    for (DirRefCount refcount: refcounts) {
      modifyRefCount(refcount.var, refcount.type,
                     refcount.dir, refcount.amount);
    }
  }

  private void modifyRefCount(Var var, RefCountType rcType, RCDir dir,
                             Arg amount) {
    assert(amount.isImmInt());
    if (rcType == RefCountType.READERS) {
      if (RefCounting.trackReadRefCount(var)) {
        if (dir == RCDir.INCR) {
          incrementReaders(Arrays.asList(var), argToExpr(amount));
        } else {
          assert(dir == RCDir.DECR);
          decrementReaders(Arrays.asList(var), argToExpr(amount));
        }
      }
    } else {
      assert(rcType == RefCountType.WRITERS);
      if (RefCounting.trackWriteRefCount(var)) {
        if (dir == RCDir.INCR) {
          incrementWriters(Arrays.asList(var), argToExpr(amount));
        } else {
          assert(dir == RCDir.DECR);
          // Close array by removing the slot we created at startup
          decrementWriters(Arrays.asList(var), argToExpr(amount));
        }
      }
    }
  }

  /**
   * Set target=addressof(src)
   */
  @Override
  public void assignReference(Var target, Var src,
                     long readRefs, long writeRefs) {
    assert(Types.isRef(target));
    assert(target.type().memberType().equals(src.type()));
    if (Types.isFileRef(target)) {
    	pointAdd(Turbine.fileRefSet(
    	          varToExpr(target), varToExpr(src),
    	          new LiteralInt(readRefs), new LiteralInt(writeRefs)));
    } else {
      pointAdd(Turbine.refSet(
          varToExpr(target), varToExpr(src),
          new LiteralInt(readRefs), new LiteralInt(writeRefs)));
    }
  }


  @Override
  public void retrieveReference(Var dst, Var src, Arg acquireRead,
                          Arg acquireWrite, Arg decr) {
    assert(Types.isRef(src));
    assert(acquireRead.isInt());
    assert(acquireWrite.isInt());
    if (acquireWrite.isVar() || acquireWrite.getInt() > 0) {
      assert(Types.isAssignableRefTo(src, dst, true));
    } else {
      assert(Types.isAssignableRefTo(src, dst));
    }

    assert(decr.isImmInt());

    Expression acquireReadExpr = argToExpr(acquireRead);
    Expression acquireWriteExpr = argToExpr(acquireWrite);

    TypeName refType = TurbineTypes.refReprType(dst);
    TclTree deref;
    if (acquireWrite.equals(Arg.ZERO)) {
      deref = Turbine.readRefGet(prefixVar(dst), varToExpr(src),
            refType, acquireReadExpr, argToExpr(decr));
    } else {
      deref = Turbine.readWriteRefGet(prefixVar(dst), varToExpr(src),
            refType, acquireReadExpr, acquireWriteExpr, argToExpr(decr));
    }
    pointAdd(deref);
  }

  @Override
  public void makeAlias(Var dst, Var src) {
    assert(src.type().equals(dst.type()));
    assert(dst.storage() == Alloc.ALIAS);
    pointAdd(new SetVariable(prefixVar(dst),
        varToExpr(src)));
  }

  @Override
  public void assignScalar(Var dst, Arg src) {
    assert(Types.isScalarFuture(dst));
    assert(Types.isScalarValue(src));
    assert(src.type().assignableTo(Types.retrievedType(dst)));

    PrimType primType = src.type().getImplType().primType();
    switch (primType) {
      case BLOB:
        pointAdd(Turbine.blobSet(varToExpr(dst), argToExpr(src)));
        break;
      case FLOAT:
        pointAdd(Turbine.floatSet(varToExpr(dst), argToExpr(src)));
        break;
      case BOOL:
      case INT:
        // Bool and int are represented internally as integers
        pointAdd(Turbine.integerSet(varToExpr(dst), argToExpr(src)));
        break;
      case STRING:
        pointAdd(Turbine.stringSet(varToExpr(dst), argToExpr(src)));
        break;
      case VOID:
        // Don't need to provide input value to void
        pointAdd(Turbine.voidSet(varToExpr(dst)));
        break;
      default:
        throw new STCRuntimeError("Unknown or non-scalar prim type "
                                   + primType);
    }
  }

  @Override
  public void retrieveScalar(Var dst, Var src, Arg decr) {
    assert(Types.isScalarValue(dst));
    assert(Types.isScalarFuture(src));
    assert(Types.retrievedType(src).assignableTo(dst.type()));
    assert(decr.isImmInt());

    PrimType primType = dst.type().getImplType().primType();
    boolean hasDecrement = !decr.equals(Arg.ZERO);
    switch (primType) {
      case BLOB:
        if (hasDecrement) {
          pointAdd(Turbine.blobDecrGet(prefixVar(dst), varToExpr(src),
                                      argToExpr(decr)));
        } else {
          pointAdd(Turbine.blobGet(prefixVar(dst), varToExpr(src)));
        }
        break;
      case FLOAT:
        if (hasDecrement) {
          pointAdd(Turbine.floatDecrGet(prefixVar(dst), varToExpr(src),
                                      argToExpr(decr)));
        } else {
          pointAdd(Turbine.floatGet(prefixVar(dst), varToExpr(src)));
        }
        break;
      case BOOL:
      case INT:
        // Bool and int are represented internally as integers
        if (hasDecrement) {
          pointAdd(Turbine.integerDecrGet(prefixVar(dst), varToExpr(src),
                                      argToExpr(decr)));
        } else {
          pointAdd(Turbine.integerGet(prefixVar(dst), varToExpr(src)));
        }
        break;
      case STRING:
        if (hasDecrement) {
          pointAdd(Turbine.stringDecrGet(prefixVar(dst), varToExpr(src),
              argToExpr(decr)));
        } else {
          pointAdd(Turbine.stringGet(prefixVar(dst), varToExpr(src)));
        }
        break;
      case VOID:
        // Don't actually need to retrieve value as it has no contents
        pointAdd(new SetVariable(prefixVar(dst), Turbine.VOID_DUMMY_VAL));

        if (hasDecrement) {
          decrementReaders(src.asList(), argToExpr(decr));
        }
        break;
      default:
        throw new STCRuntimeError("Unknown or non-scalar prim type "
                                   + primType);
    }
  }

  @Override
  public void dereferenceScalar(Var dst, Var src) {
    assert(Types.isScalarFuture(dst));
    assert(Types.isRef(src));
    assert(src.type().memberType().assignableTo(dst.type()));

    PrimType primType = dst.type().getImplType().primType();
    switch (primType) {
      case BLOB:
        pointAdd(Turbine.dereferenceBlob(varToExpr(dst), varToExpr(src)));
        break;
      case FLOAT:
        pointAdd(Turbine.dereferenceFloat(varToExpr(dst), varToExpr(src)));
        break;
      case BOOL:
      case INT:
        // Bool and int are represented by int
        pointAdd(Turbine.dereferenceInteger(varToExpr(dst), varToExpr(src)));
        break;
      case STRING:
        pointAdd(Turbine.dereferenceString(varToExpr(dst), varToExpr(src)));
        break;
      case VOID:
        pointAdd(Turbine.dereferenceVoid(varToExpr(dst), varToExpr(src)));
        break;
      default:
        throw new STCRuntimeError("Unknown or non-scalar prim type "
                                   + primType);
    }
  }

  @Override
  public void freeBlob(Var blobVal) {
    assert(Types.isBlobVal(blobVal));
    pointAdd(Turbine.freeLocalBlob(varToExpr(blobVal)));
  }

  @Override
  public void assignFile(Var dst, Arg src, Arg setFilename) {
    assert(Types.isFile(dst));
    assert(Types.isFileVal(src));
    // Sanity check that we're not setting mapped file
    assert(setFilename.isImmBool());
    if (setFilename.isBool() && setFilename.getBool()) {
      // Sanity check that we're not setting mapped file
      assert(dst.isMapped() != Ternary.TRUE) : dst;
    }

    pointAdd(Turbine.fileSet(varToExpr(dst),
              prefixVar(src.getVar()), argToExpr(setFilename)));
  }

  @Override
  public void retrieveFile(Var target, Var src, Arg decr) {
    assert(Types.isFile(src));
    assert(Types.isFileVal(target));
    assert(decr.isImmInt());
    if (decr.equals(Arg.ZERO)) {
      pointAdd(Turbine.fileGet(prefixVar(target), varToExpr(src)));
    } else {
      pointAdd(Turbine.fileDecrGet(prefixVar(target),
          varToExpr(src), argToExpr(decr)));
    }
  }

  @Override
  public void dereferenceFile(Var target, Var src) {
    assert(Types.isFile(target));
    assert(Types.isFileRef(src));
    Command deref = Turbine.dereferenceFile(varToExpr(target),
                                            varToExpr(src));
    pointAdd(deref);
  }

  @Override
  public void assignArray(Var target, Arg src) {
    assert(Types.isArray(target));
    assert(Types.isArrayLocal(src));
    assert(Types.containerElemType(src).assignableTo(
              Types.containerElemValType(target)));
    assert(Types.arrayKeyType(src).assignableTo(
            Types.arrayKeyType(target)));

    pointAdd(arrayBuild(target, argToExpr(src)));
  }

  @Override
  public void retrieveArray(Var target, Var src, Arg decr) {
    assert(Types.isArray(src));
    assert(Types.isArrayLocal(target));
    assert(Types.containerElemValType(src).assignableTo(
                    Types.containerElemType(target)));

    assert(Types.arrayKeyType(src).assignableTo(
            Types.arrayKeyType(target)));
    assert(decr.isImmInt());

    pointAdd(Turbine.enumerateAll(prefixVar(target), varToExpr(src), true,
            argToExpr(decr)));
  }

  @Override
  public void assignBag(Var target, Arg src) {
    assert(Types.isBag(target));
    assert(Types.isBagLocal(src));
    assert(Types.containerElemType(src).assignableTo(
              Types.containerElemValType(target)));

    TypeName elemType = TurbineTypes.reprType(Types.containerElemType(target));
    pointAdd(Turbine.multisetBuild(varToExpr(target), argToExpr(src), LiteralInt.ONE,
                                   Collections.singletonList(elemType)));
  }

  @Override
  public void retrieveBag(Var target, Var src, Arg decr) {
    assert(Types.isBag(src));
    assert(Types.isBagLocal(target));
    assert(decr.isImmInt());
    assert(Types.containerElemValType(src).assignableTo(
                    Types.containerElemType(target)));

    pointAdd(Turbine.enumerateAll(prefixVar(target), varToExpr(src), false,
            argToExpr(decr)));
  }

  private Dict localStructDict(Var struct, List<List<String>> fieldPaths,
      List<Arg> fieldVals) {
    // Restructure into pairs
    List<Pair<List<String>, Arg>> fields =
              new ArrayList<Pair<List<String>,Arg>>(fieldPaths.size());
    for (int i = 0; i < fieldPaths.size(); i++) {
      List<String> fieldPath = fieldPaths.get(i);
      Arg fieldVal = fieldVals.get(i);
      assert(Types.isStructFieldVal(struct, fieldPath, fieldVal));
      fields.add(Pair.create(fieldPath, fieldVal));
    }

    // Build dict containing only fields to initialise
    Dict dict = TurbineStructs.buildNestedDict(fields);
    return dict;
  }

  @Override
  public void buildStructLocal(Var struct, List<List<String>> fieldPaths,
                                List<Arg> fieldVals) {
    assert(Types.isStructLocal(struct));
    Dict dictExpr = localStructDict(struct, fieldPaths, fieldVals);

    pointAdd(new SetVariable(prefixVar(struct), dictExpr));
  }

  @Override
  public void assignStruct(Var target, Arg src) {
    assert(Types.isStruct(target));
    assert(Types.isStructLocal(src));
    assert(StructType.sharedStruct((StructType)src.type().getImplType())
            .assignableTo(target.type()));

    /*
     * Must decrement any refcounts not explicitly tracked since we're
     * assigning the struct in whole.
     */
    long writeDecr = RefCounting.baseStructWriteRefCount(target.type(),
                          target.defType(), false, true);

    TypeName structType = TurbineTypes.reprType(target);
    pointAdd(Turbine.structSet(varToExpr(target), argToExpr(src),
                          structType, new LiteralInt(writeDecr)));
  }

  @Override
  public void retrieveStruct(Var target, Var src, Arg decr) {
    assert(Types.isStruct(src));
    assert(Types.isStructLocal(target));
    assert(decr.isImmInt());

    assert(StructType.sharedStruct((StructType)target.type().getImplType())
            .assignableTo(src.type()));

    pointAdd(Turbine.structDecrGet(prefixVar(target), varToExpr(src),
                                   argToExpr(decr)));
  }

  @Override
  public void assignArrayRecursive(Var dst, Arg src) {
    assert(Types.isArray(dst));
    assert(Types.isArrayLocal(src));
    assignRecursive(dst, src);
  }

  @Override
  public void assignStructRecursive(Var dst, Arg src) {
    assert(Types.isStruct(dst));
    assert(Types.isStructLocal(src));
    assignRecursive(dst, src);
  }

  @Override
  public void assignBagRecursive(Var dst, Arg src) {
    assert(Types.isBag(dst));
    assert(Types.isBagLocal(src));
    assignRecursive(dst, src);
  }

  private void assignRecursive(Var dst, Arg src) {
    assert(src.type().assignableTo(Types.unpackedType(dst)));
    List<Expression> typeList = TurbineTypes.buildRecTypeInfo(dst);

    // Decrements all refcounts
    long writeDecr = RefCounting.baseWriteRefCount(dst, true, true);
    pointAdd(
        Turbine.buildRec(typeList, varToExpr(dst), argToExpr(src), writeDecr));
  }

  @Override
  public void retrieveArrayRecursive(Var dst, Var src, Arg decr) {
    assert(Types.isArray(src));
    assert(Types.isArrayLocal(dst));
    retrieveRecursive(dst, src, decr);
  }

  @Override
  public void retrieveStructRecursive(Var dst, Var src, Arg decr) {
    assert(Types.isStruct(src));
    assert(Types.isStructLocal(dst));
    retrieveRecursive(dst, src, decr);
  }

  @Override
  public void retrieveBagRecursive(Var dst, Var src, Arg decr) {
    assert(Types.isBag(src));
    assert(Types.isBagLocal(dst));
    retrieveRecursive(dst, src, decr);
  }

  private void retrieveRecursive(Var dst, Var src, Arg decr) {
    assert(Types.unpackedType(src).assignableTo(dst.type()));

    List<Expression> typeList = TurbineTypes.enumRecTypeInfo(src);

    pointAdd(Turbine.enumerateRec(prefixVar(dst), typeList,
              varToExpr(src), argToExpr(decr)));
  }

  @Override
  public void decrLocalFileRefCount(Var localFile) {
    assert(Types.isFileVal(localFile));
    pointAdd(Turbine.decrLocalFileRef(prefixVar(localFile)));
  }

  @Override
  public void getFileNameAlias(Var filename, Var file) {
    assert(Types.isString(filename));
    assert(filename.storage() == Alloc.ALIAS);
    assert(Types.isFile(file));

    SetVariable cmd = new SetVariable(prefixVar(filename),
                          Turbine.getFileName(varToExpr(file)));
    pointAdd(cmd);
  }

  /**
   * Copy filename from future to file
   */
  @Override
  public void copyInFilename(Var file, Var filename) {
    assert(Types.isString(filename));
    assert(Types.isFile(file));
    pointAdd(Turbine.copyInFilename(varToExpr(file), varToExpr(filename)));
  }

  @Override
  public void getLocalFileName(Var filename, Var file) {
    assert(Types.isStringVal(filename));
    assert(Types.isFileVal(file));
    pointAdd(new SetVariable(prefixVar(filename),
                        Turbine.localFilePath(varToExpr(file))));
  }

  @Override
  public void isMapped(Var isMapped, Var file) {
    assert(Types.isFile(file));
    assert(Types.isBoolVal(isMapped));
    pointAdd(Turbine.isMapped(prefixVar(isMapped),
                                           varToExpr(file)));
  }

  @Override
  public void getFilenameVal(Var filenameVal, Var file) {
    assert(Types.isFile(file));
    assert(Types.isStringVal(filenameVal));
    pointAdd(new SetVariable(prefixVar(filenameVal),
            Turbine.getFilenameVal(varToExpr(file))));
  }

  @Override
  public void setFilenameVal(Var file, Arg filenameVal) {
    assert(Types.isFile(file));
    assert(filenameVal.isImmString());
    pointAdd(Turbine.setFilenameVal(varToExpr(file),
              argToExpr(filenameVal)));
  }

  @Override
  public void chooseTmpFilename(Var filenameVal) {
    assert(Types.isStringVal(filenameVal));
    pointAdd(Turbine.mkTemp(prefixVar(filenameVal)));
  }

  @Override
  public void initLocalOutputFile(Var localFile, Arg filenameVal, Arg isMapped) {
    assert(Types.isFileVal(localFile));
    assert(filenameVal.isImmString());
    assert(isMapped.isImmBool());

    // Initialize refcount to 1 if unmapped, or 2 if mapped so that the file
    // isn't deleted upon the block finishing
    Sequence ifMapped = new Sequence(), ifUnmapped = new Sequence();
    ifMapped.add(new SetVariable(TCLTMP_INIT_REFCOUNT, LiteralInt.TWO));
    ifUnmapped.add(new SetVariable(TCLTMP_INIT_REFCOUNT, LiteralInt.ONE));

    if (isMapped.isBool()) {
      if (isMapped.getBool()) {
        point().append(ifMapped);
      } else {
        point().append(ifUnmapped);
      }
    } else {
      pointAdd(new If(argToExpr(isMapped), ifMapped, ifUnmapped));
    }
    pointAdd(Turbine.createLocalFile(prefixVar(localFile),
             argToExpr(filenameVal), new Value(TCLTMP_INIT_REFCOUNT),
             argToExpr(isMapped)));
  }

  @Override
  public void copyFileContents(Var dst, Var src) {
    assert(Types.isFileVal(dst));
    assert(Types.isFileVal(src));
    FileKind dstKind = dst.type().fileKind();
    assert(dstKind.supportsPhysicalCopy());
    FileKind srcKind = src.type().fileKind();
    assert(srcKind.supportsPhysicalCopy());
    if (dstKind == FileKind.LOCAL_FS &&
        srcKind == FileKind.LOCAL_FS) {
      pointAdd(Turbine.copyFileContents(varToExpr(dst),
                                                     varToExpr(src)));
    } else {
      throw new STCRuntimeError("Don't know how to copy " + srcKind + " -> "
                                                                  + dstKind);
    }
  }

  @Override
  public void localOp(BuiltinOpcode op, Var out, List<Arg> in) {
    ArrayList<Expression> argExpr = new ArrayList<Expression>(in.size());
    for (Arg a: in) {
      argExpr.add(argToExpr(a));
    }

    pointAdd(BuiltinOps.genLocalOpTcl(op, out, in, argExpr));
  }

  @Override
  public void asyncOp(BuiltinOpcode op, Var out, List<Arg> in,
                      TaskProps props) {
    assert(props != null);
    props.assertInternalTypesValid();

    // Generate in same way as built-in function
    TclFunRef fn = BuiltinOps.getBuiltinOpImpl(op);
    if (fn == null) {
      List<FnID> impls = foreignFuncs.findOpImpl(op);

      // It should be impossible for there to be no implementation for a function
      // like this
      assert(impls != null) : op;
      assert(impls.size() > 0) : op;

      if (impls.size() > 1) {
        Logging.getSTCLogger().debug("Multiple implementations for operation " +
            op + ": " + impls.toString());
      }
      fn = tclFuncSymbols.get(impls.get(0)).val2;
    }

    List<Var> outL = (out == null) ?
          Arrays.<Var>asList() : Arrays.asList(out);

    callTclFunction("operator: " + op.toString(), fn,
                        in, outL, props);
  }

  @Override
  public void arrayCreateNestedFuture(Var result,
      Var array, Var ix) {
    assert(Types.isArray(array));
    assert(Types.isNonLocalRef(result, true));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(result.storage() != Alloc.ALIAS);

    List<TypeName> fullType =
        TurbineTypes.dataDeclFullType(Types.retrievedType(result));

    pointAdd(Turbine.arrayCreateNested(
        varToExpr(result), varToExpr(array),
        varToExpr(ix), fullType));
  }

  @Override
  public void arrayRefCreateNestedFuture(Var result, Var arrayRefVar,
                                         Var ix) {
    assert(Types.isArrayRef(arrayRefVar));
    assert(Types.isNonLocalRef(result, true));
    assert(result.storage() != Alloc.ALIAS);
    assert(Types.isArrayKeyFuture(arrayRefVar, ix));

    List<TypeName> fullType =
        TurbineTypes.dataDeclFullType(Types.retrievedType(result));

    pointAdd(Turbine.arrayRefCreateNested(
        varToExpr(result), varToExpr(arrayRefVar), varToExpr(ix),
        fullType));
  }


  @Override
  public void arrayCreateNestedImm(Var result, Var array, Arg ix,
        Arg callerReadRefs, Arg callerWriteRefs,
        Arg readDecr, Arg writeDecr) {
    assert(Types.isArray(array));
    assert(Types.isNonLocal(result));
    assert(result.storage() == Alloc.ALIAS);
    assert(Types.isArrayKeyVal(array, ix));
    assert(callerReadRefs.isImmInt());
    assert(callerWriteRefs.isImmInt());
    assert(readDecr.isImmInt());
    assert(writeDecr.isImmInt());

    pointAdd(Turbine.arrayCreateNested(
        prefixVar(result), varToExpr(array), argToExpr(ix),
        TurbineTypes.dataDeclFullType(result),
        argToExpr(callerReadRefs), argToExpr(callerWriteRefs),
        argToExpr(readDecr), argToExpr(writeDecr)));
  }

  @Override
  public void arrayRefCreateNestedImm(Var result, Var array, Arg ix) {
    assert(Types.isArrayRef(array));
    assert(Types.isNonLocalRef(result, true));
    assert(result.storage() != Alloc.ALIAS);
    assert(Types.isArrayKeyVal(array, ix));


    List<TypeName> fullType =
        TurbineTypes.dataDeclFullType(Types.retrievedType(result));

    pointAdd(Turbine.arrayRefCreateNestedImmIx(
        varToExpr(result), varToExpr(array), argToExpr(ix),
        fullType));
  }

  @Override
  public void callForeignFunctionWrapped(FnID function,
          List<Var> outputs, List<Arg> inputs, TaskProps props) {
    assert(props != null);
    props.assertInternalTypesValid();
    logger.debug("call builtin: " + function);
    TclFunRef tclf = tclFuncSymbols.get(function).val2;
    assert tclf != null : "Builtin " + function + "not found";
    foreignFuncs.getTaskMode(function).checkCanRunIn(execContextStack.peek());

    callTclFunction(function.uniqueName(), tclf, inputs, outputs, props);
  }

  private void callTclFunction(String functionName, TclFunRef tclf,
      List<Arg> inputs, List<Var> outputs, TaskProps props) {
    assert(props != null);
    props.assertInternalTypesValid();

    TclList iList = TclUtil.tclListOfArgs(inputs);
    TclList oList = TclUtil.tclListOfVariables(outputs);

    if (tclf == null) {
      //should have all builtins in symbols
      throw new STCRuntimeError("call to undefined builtin function "
                                + functionName);
    }

    // Properties can be null
    RuleProps ruleProps = buildRuleProps(props);

    setPriority(ruleProps.priority);

    Token tclFunction = new Token(tclf.pkg + "::" + tclf.symbol);
    List<Expression> funcArgs = new ArrayList<Expression>();
    funcArgs.add(oList);
    funcArgs.add(iList);
    funcArgs.addAll(Turbine.ruleKeywordArgs(ruleProps.targetRank,
        ruleProps.targetStrictness, ruleProps.targetAccuracy,
        ruleProps.parallelism));
    Command c = new Command(tclFunction, funcArgs);
    pointAdd(c);

    clearPriority(ruleProps.priority);
  }

  @Override
  public void callForeignFunctionLocal(FnID function,
          List<Var> outputs, List<Arg> inputs) {
    Pair<TclOpTemplate, TclFunRef> impls = tclFuncSymbols.get(function);
    assert(impls != null) : "No foreign function impls for " + function;
    TclOpTemplate template = impls.val1;
    assert(template != null);

    List<TemplateArg> outputArgs = new ArrayList<TemplateArg>();
    for (Var output: outputs) {
      outputArgs.add(TemplateArg.fromOutputVar(output));
    }

    List<TemplateArg> inputArgs = new ArrayList<TemplateArg>();
    for (Arg input: inputs) {
      inputArgs.add(TemplateArg.fromInputArg(input));
    }

    List<TclTree> result = TclTemplateProcessor.processTemplate(
                function.originalName(), template, inputArgs, outputArgs);

    pointAdd(new Command(result));
  }

  @Override
  public void functionCall(FnID function,
              List<Var> outputs, List<Arg> inputs,
              List<Boolean> blocking, ExecTarget mode, TaskProps props)  {
    props.assertInternalTypesValid();

    List<Var> blockinInputVars = new ArrayList<Var>();
    for (int i = 0; i < inputs.size(); i++) {
      Arg input = inputs.get(i);
      if (input.isVar() && blocking.get(i)) {
        blockinInputVars.add(input.getVar());
      }
    }
    List<Expression> blockOn = getTurbineWaitIDs(blockinInputVars);

    String swiftFuncName = TclNamer.swiftFuncName(function);
    if (mode.isAsync()) {
      List<Expression> args = new ArrayList<Expression>();
      args.addAll(TclUtil.varsToExpr(outputs));
      args.addAll(TclUtil.argsToExpr(inputs));
      List<Expression> action = buildAction(swiftFuncName, args);

      Sequence rule = Turbine.rule(function.uniqueName(), blockOn, action, mode,
                       execContextStack.peek(), buildRuleProps(props));
      point().append(rule);

    } else {
      // Calling synchronously, can't guarantee anything blocks
      assert blockOn.size() == 0 : function + ": " + blockOn;

      List<Expression> inVars = TclUtil.argsToExpr(inputs);
      List<Expression> outVars = TclUtil.varsToExpr(outputs);

      pointAdd(Turbine.callFunctionSync(
          swiftFuncName, outVars, inVars));
    }
  }

  private RuleProps buildRuleProps(TaskProps props) {
    Expression priority = argToExpr(props.get(TaskPropKey.PRIORITY), true);

    TclTarget rank = TclTarget.fromArg(props.getWithDefault(TaskPropKey.LOC_RANK));

    Expression strictness = argToExpr(
        props.getWithDefault(TaskPropKey.LOC_STRICTNESS));

    Expression accuracy = argToExpr(
        props.getWithDefault(TaskPropKey.LOC_ACCURACY));

    Expression parallelism = argToExpr(props.get(TaskPropKey.PARALLELISM), true);

    return new RuleProps(rank, strictness, accuracy, parallelism, priority);
  }

  @Override
  public void runExternal(Arg cmd, List<Arg> args,
          List<Var> outFiles, List<Arg> inFiles,
          Redirects<Arg> redirects,
          boolean hasSideEffects, boolean deterministic) {
    for (Arg inFile: inFiles) {
      assert(inFile.isVar());
      assert(Types.isFileVal(inFile));
    }

    List<Expression> tclArgs = new ArrayList<Expression>(args.size());
    List<Expression> logMsg = new ArrayList<Expression>();
    logMsg.add(new Token("exec: " + cmd));

    for (int argNum = 0; argNum < args.size(); argNum++) {
      Arg arg = args.get(argNum);
      // Should only accept local arguments
      assert(arg.isConst() || arg.getVar().storage() == Alloc.LOCAL);
      Expression argExpr = cmdLineArgExpr(arg);
      tclArgs.add(argExpr);
      logMsg.add(argExpr);
    }


    Expression stdinFilename = TclUtil.argToExpr(redirects.stdin, true);
    Expression stdoutFilename = TclUtil.argToExpr(redirects.stdout, true);
    Expression stderrFilename = TclUtil.argToExpr(redirects.stderr, true);
    logMsg.add(Turbine.execKeywordOpts(stdinFilename, stdoutFilename,
                                       stderrFilename));

    pointAdd(Turbine.turbineLog(logMsg));
    pointAdd(Turbine.exec(argToExpr(cmd), stdinFilename,
                stdoutFilename, stderrFilename, tclArgs));

    // Handle closing of outputs
    for (int i = 0; i < outFiles.size(); i++) {
      Var o = outFiles.get(i);
      if (Types.isFileVal(o)) {
        // Do nothing, filename was set when initialized earlier
      } else if (Types.isVoidVal(o)) {
        // Do nothing, void value is just a bookkeeping trick
      } else {
        throw new STCRuntimeError("Invalid app output type: " + o);
      }
    }
  }

  /**
   *
   * @param arg
   * @return Expression appropriate for app command line (e.g. expanding arrays)
   */
  private Expression cmdLineArgExpr(Arg arg) {
    if (Types.isContainerLocal(arg)) {
      // Expand list
      return new Expand(argToExpr(arg));
    } else {
      // Plain argument
      return argToExpr(arg);
    }
  }

  private void clearPriority(Expression priority) {
    if (priority != null) {
      pointAdd(Turbine.resetPriority());
    }
  }

  private void setPriority(Expression priority) {
    if (priority != null) {
      logger.trace("priority: " + priority);
      pointAdd(Turbine.setPriority(priority));
    }
  }

  /**
   * Construct subscript expression for struct
   * @param struct
   * @param fields
   * @return
   */
  public static Expression structSubscript(Var struct,
                                           List<String> fields) {
    assert(Types.isStruct(struct) || Types.isStructRef(struct));

    Type curr = struct.type().getImplType();
    if (Types.isStructRef(curr)) {
      curr = curr.memberType();
    }

    int[] indices = structFieldIndices(curr, fields);
    return Turbine.structSubscript(indices);
  }

  private static int[] structFieldIndices(Typed typed, List<String> fields) {
    Type type = typed.type();

    // use struct type info to construct index list
    int indices[] = new int[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      assert(type instanceof StructType);
      String field = fields.get(i);
      int fieldIx = ((StructType)type).fieldIndexByName(field);
      assert(fieldIx >= 0) : field + " " + type;
      indices[i] = fieldIx;
      // Get inner type
      type = ((StructType)type).fields().get(fieldIx).type();
    }
    return indices;
  }

  @Override
  public void structStore(Var struct, List<String> fields,
      Arg fieldContents) {
    assert(Types.isStruct(struct));
    assert(Types.isStructFieldVal(struct, fields, fieldContents));

    int[] indices = structFieldIndices(struct, fields);

    // Work out write refcounts for field (might be > 1 if struct)
    Type fieldType = structFieldType(struct, fields);
    long writeDecr = structFieldWriteDecr(fieldType);

    pointAdd(Turbine.structInsert(varToExpr(struct),
        Turbine.structSubscript(indices), argToExpr(fieldContents),
        TurbineTypes.fullReprType(fieldContents, true),
        new LiteralInt(writeDecr)));
  }

  private Type structFieldType(Var struct, List<String> fields) {
    Type fieldType;
    try {
      fieldType = Types.structFieldType(struct, fields);
    } catch (TypeMismatchException e) {
      throw new STCRuntimeError(e.getMessage());
    }
    return fieldType;
  }

  private long structFieldWriteDecr(Type fieldType) {
    return RefCounting.baseRefCount(fieldType, DefType.LOCAL_COMPILER,
                                    RefCountType.WRITERS, true, true);
  }

  @Override
  public void structCopyIn(Var struct, List<String> fields,
                           Var fieldContents) {
    assert(Types.isStruct(struct));
    assert(Types.isStructField(struct, fields, fieldContents));
    Expression subscript = structSubscript(struct, fields);

    Type fieldType = structFieldType(struct, fields);
    long writeDecr = structFieldWriteDecr(fieldType);

    pointAdd(Turbine.structCopyIn(varToExpr(struct), subscript,
        varToExpr(fieldContents),
        TurbineTypes.fullReprType(fieldContents, false),
        new LiteralInt(writeDecr), LiteralInt.ZERO));
  }

  @Override
  public void structRefCopyIn(Var structRef, List<String> fields,
                           Var fieldContents) {
    assert(Types.isStructRef(structRef));
    assert(Types.isStructField(structRef, fields, fieldContents));
    Expression subscript = structSubscript(structRef, fields);

    Type fieldType = structFieldType(structRef, fields);
    long writeDecr = structFieldWriteDecr(fieldType);

    pointAdd(Turbine.structRefCopyIn(varToExpr(structRef), subscript,
        varToExpr(fieldContents),
        TurbineTypes.fullReprType(fieldContents, false),
        new LiteralInt(writeDecr)));
  }

  @Override
  public void structRefStoreSub(Var structRef, List<String> fields,
      Arg fieldContents) {
    assert(Types.isStructRef(structRef));
    assert(Types.isStructField(structRef, fields, fieldContents));
    Expression subscript = structSubscript(structRef, fields);

    Type fieldType = structFieldType(structRef, fields);
    long writeDecr = structFieldWriteDecr(fieldType);

    pointAdd(Turbine.structRefInsert(varToExpr(structRef), subscript,
        argToExpr(fieldContents),
        TurbineTypes.fullReprType(fieldContents, false),
        new LiteralInt(writeDecr)));
  }

  /**
   * Create alias for a struct field
   * @param structVar
   * @param structField
   * @param alias
   */
  @Override
  public void structCreateAlias(Var alias, Var struct,
                           List<String> fields) {
    assert(alias.storage() == Alloc.ALIAS) : alias;
    assert(Types.isStruct(struct));
    assert(Types.isStructField(struct, fields, alias));

    int[] fieldIndices = structFieldIndices(struct.type(), fields);
    Expression aliasExpr;

    // Simple create alias as handle
    if (TurbineTypes.standardRefRepr(alias)) {
      aliasExpr = Turbine.structAlias(varToExpr(struct), fieldIndices);
    } else if (Types.isFile(alias)) {
      aliasExpr = Turbine.structFileAlias(varToExpr(struct), fieldIndices);
    } else {
      throw new STCRuntimeError("Unexpected type " + alias.type());
    }

    pointAdd(new SetVariable(prefixVar(alias), aliasExpr));
  }


  @Override
  public void structRetrieveSub(Var output, Var struct, List<String> fields,
      Arg readDecr) {
    assert(Types.isStruct(struct));
    assert(Types.isStructFieldVal(struct, fields, output));

    Expression subscript = structSubscript(struct, fields);
    Expression readAcquire = LiteralInt.ONE;

    Expression expr = Turbine.lookupStruct(varToExpr(struct),
                subscript, argToExpr(readDecr), readAcquire,
                null, null);
    pointAdd(new SetVariable(prefixVar(output), expr));
  }

  @Override
  public void structCopyOut(Var output, Var struct,
      List<String> fields) {
    assert(Types.isStruct(struct)) : struct;
    assert(Types.isStructField(struct, fields, output));
    Expression subscript = structSubscript(struct, fields);

    long writeDecr = RefCounting.baseRefCount(output.type(), DefType.LOCAL_COMPILER,
                                          RefCountType.WRITERS, false, true);

    pointAdd(Turbine.copyStructSubscript(varToExpr(output), varToExpr(struct),
              subscript, TurbineTypes.reprType(output.type()), writeDecr));
  }

  @Override
  public void structRefCopyOut(Var output, Var structRef,
                              List<String> fields) {
    assert(Types.isStructRef(structRef)) : structRef;
    assert(Types.isStructField(structRef, fields, output)) :
      structRef.name() + ":" + structRef.type() + " "
      + fields + " " + output;

    Expression subscript = structSubscript(structRef, fields);

    long writeDecr = RefCounting.baseRefCount(output.type(), DefType.LOCAL_COMPILER,
                                          RefCountType.WRITERS, false, true);

    pointAdd(Turbine.copyStructRefSubscript(varToExpr(output),
        varToExpr(structRef), subscript, TurbineTypes.reprType(output),
        writeDecr));
  }

  @Override
  public void structCreateNested(Var result, Var struct,
      List<String> fields, Arg callerReadRefs,
      Arg callerWriteRefs, Arg readDecr, Arg writeDecr) {
    assert(Types.isNonLocal(result));
    assert(result.storage() == Alloc.ALIAS);

    assert(Types.isStructFieldVal(struct, fields, result));
    assert(callerReadRefs.isImmInt());
    assert(callerWriteRefs.isImmInt());
    assert(readDecr.isImmInt());
    assert(writeDecr.isImmInt());

    Expression subscript = structSubscript(struct, fields);

    TclTree t = Turbine.structCreateNested(
            prefixVar(result), varToExpr(struct), subscript,
            TurbineTypes.dataDeclFullType(result),
            argToExpr(callerReadRefs), argToExpr(callerWriteRefs),
            argToExpr(readDecr), argToExpr(writeDecr));
    pointAdd(t);
  }

  @Override
  public void arrayRetrieve(Var dst, Var array, Arg key, Arg decr,
                            Arg acquire) {
    assert(Types.isArrayKeyVal(array, key));
    assert(Types.isElemValType(array, dst));
    pointAdd(Turbine.arrayLookupImm(prefixVar(dst), varToExpr(array),
             argToExpr(key), argToExpr(decr), argToExpr(acquire)));
  }

  @Override
  public void arrayCreateAlias(Var alias, Var arrayVar, Arg arrIx) {
    assert(alias.storage() == Alloc.ALIAS) : alias;
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(Types.isArray(arrayVar)) : arrayVar;
    assert(Types.isElemType(arrayVar, alias));

    // Check that we can generate valid code for it
    assert(Unimplemented.subscriptAliasSupported(arrayVar));

    // Simple create alias as handle
    Expression aliasExpr;


    if (TurbineTypes.standardRefRepr(alias)) {
      aliasExpr = Turbine.arrayAlias(varToExpr(arrayVar),
                                                argToExpr(arrIx));
    } else if (Types.isFile(alias)) {
      aliasExpr = Turbine.arrayFileAlias(varToExpr(arrayVar),
                                                argToExpr(arrIx));
    } else {
      throw new STCRuntimeError("Unexpected type " + alias.type());
    }

    pointAdd(new SetVariable(prefixVar(alias), aliasExpr));
  }


  @Override
  public void arrayCopyOutImm(Var oVar, Var arrayVar, Arg arrIx) {
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(Types.isArray(arrayVar)) : arrayVar;
    assert(Types.isElemType(arrayVar, oVar));

    Command getRef = Turbine.arrayLookupImmIx(
          varToExpr(oVar),
          TurbineTypes.arrayValueType(arrayVar, false),
          varToExpr(arrayVar),
          argToExpr(arrIx), false);

    pointAdd(getRef);
  }

  @Override
  public void arrayCopyOutFuture(Var oVar, Var arrayVar, Var indexVar) {
    assert(Types.isArrayKeyFuture(arrayVar, indexVar));
    assert(Types.isArray(arrayVar));
    assert(Types.isElemType(arrayVar, oVar));
    // Nested arrays - oVar should be a reference type
    Command getRef = Turbine.arrayLookupComputed(varToExpr(oVar),
        TurbineTypes.reprType(oVar),
        varToExpr(arrayVar), varToExpr(indexVar), false);
    pointAdd(getRef);
  }

  @Override
  public void arrayRefCopyOutImm(Var oVar, Var arrayVar, Arg arrIx) {
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(Types.isArrayRef(arrayVar));
    assert(Types.isElemType(arrayVar, oVar));

    Command getRef = Turbine.arrayLookupImmIx(
          varToExpr(oVar),
          TurbineTypes.arrayValueType(arrayVar, false),
          varToExpr(arrayVar),
          argToExpr(arrIx), true);

    pointAdd(getRef);
  }

  @Override
  public void arrayRefCopyOutFuture(Var oVar, Var arrayVar, Var indexVar) {
    assert(Types.isArrayRef(arrayVar));
    assert(Types.isElemType(arrayVar, oVar));
    assert(Types.isArrayKeyFuture(arrayVar, indexVar));

    // Nested arrays - oVar should be a reference type
    Command getRef = Turbine.arrayLookupComputed(varToExpr(oVar),
        TurbineTypes.reprType(oVar),
        varToExpr(arrayVar), varToExpr(indexVar), true);
    pointAdd(getRef);
  }

  @Override
  public void arrayContains(Var out, Var arr, Arg index) {
    assert(Types.isBoolVal(out));
    assert(Types.isArray(arr));
    assert(Types.isArrayKeyVal(arr, index));
    pointAdd(new SetVariable(prefixVar(out),
         Turbine.arrayContains(varToExpr(arr), argToExpr(index))));
  }

  @Override
  public void containerSize(Var out, Var cont) {
    assert(Types.isIntVal(out));
    assert(Types.isContainer(cont));
    pointAdd(Turbine.containerSize(prefixVar(out), varToExpr(cont)));
  }

  @Override
  public void arrayLocalContains(Var out, Var arr, Arg index) {
    assert(Types.isBoolVal(out));
    assert(Types.isArrayLocal(arr));
    assert(Types.isArrayKeyVal(arr, index));
    pointAdd(new SetVariable(prefixVar(out),
        Turbine.dictExists(varToExpr(arr), argToExpr(index))));
  }

  @Override
  public void containerLocalSize(Var out, Var cont) {
    assert(Types.isIntVal(out));
    assert(Types.isContainerLocal(cont));
    Expression sizeExpr;
    if (Types.isArrayLocal(cont)) {
      sizeExpr = Turbine.dictSize(varToExpr(cont));
    } else {
      assert(Types.isBagLocal(cont));
      sizeExpr = Turbine.listLength(varToExpr(cont));
    }
    pointAdd(new SetVariable(prefixVar(out),
             sizeExpr));
  }

  @Override
  public void arrayStore(Var array, Arg arrIx, Arg member, Arg writersDecr) {
    assert(Types.isArray(array));
    assert(Types.isArrayKeyVal(array, arrIx));
    assert(writersDecr.isImmInt());
    assert(Types.isElemValType(array, member));

    Command r = Turbine.arrayStoreImmediate(
        argToExpr(member), varToExpr(array),
        argToExpr(arrIx), argToExpr(writersDecr),
        TurbineTypes.arrayValueType(array, false));
    pointAdd(r);
  }

  @Override
  public void arrayStoreFuture(Var array, Var ix, Arg member,
                                Arg writersDecr) {
    assert(Types.isArray(array));
    assert(Types.isElemValType(array, member));
    assert(writersDecr.isImmInt());
    assert(Types.isArrayKeyFuture(array, ix));

    Command r = Turbine.arrayStoreComputed(
        argToExpr(member), varToExpr(array),
        varToExpr(ix), argToExpr(writersDecr),
        TurbineTypes.arrayValueType(array, false));

    pointAdd(r);
  }

  @Override
  public void arrayRefStoreImm(Var array, Arg arrIx, Arg member) {
    assert(Types.isArrayRef(array));
    assert(Types.isArrayKeyVal(array, arrIx));
    assert(Types.isElemValType(array, member));

    Command r = Turbine.arrayRefStoreImmediate(
        argToExpr(member), varToExpr(array), argToExpr(arrIx),
        TurbineTypes.arrayValueType(array, false));
    pointAdd(r);
  }

  @Override
  public void arrayRefStoreFuture(Var array, Var ix, Arg member) {
    assert(Types.isArrayRef(array));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(Types.isElemValType(array, member));
    Command r = Turbine.arrayRefStoreComputed(
        argToExpr(member), varToExpr(array),
        varToExpr(ix), TurbineTypes.arrayValueType(array, false));

    pointAdd(r);
  }

  @Override
  public void arrayCopyInImm(Var array, Arg arrIx, Var member, Arg writersDecr) {
    assert(Types.isArray(array));
    assert(Types.isArrayKeyVal(array, arrIx));
    assert(writersDecr.isImmInt());
    assert(Types.isElemType(array, member));
    Command r = Turbine.arrayDerefStore(
        varToExpr(member), varToExpr(array),
        argToExpr(arrIx), argToExpr(writersDecr),
        TurbineTypes.arrayValueType(array, false));
    pointAdd(r);
  }

  @Override
  public void arrayCopyInFuture(Var array, Var ix, Var member,
                                Arg writersDecr) {
    assert(Types.isArray(array));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(writersDecr.isImmInt());
    assert(Types.isElemType(array, member));

    Command r = Turbine.arrayDerefStoreComputed(
        varToExpr(member), varToExpr(array),
        varToExpr(ix), argToExpr(writersDecr),
        TurbineTypes.arrayValueType(array, false));

    pointAdd(r);
  }

  @Override
  public void arrayRefCopyInImm(Var array, Arg arrIx, Var member) {
    assert(Types.isArrayRef(array));
    assert(Types.isArrayKeyVal(array, arrIx));
    assert(Types.isElemType(array, member));

    Command r = Turbine.arrayRefDerefStore(
        varToExpr(member), varToExpr(array),
        argToExpr(arrIx), TurbineTypes.arrayValueType(array, false));
    pointAdd(r);
  }

  @Override
  public void arrayRefCopyInFuture(Var array, Var ix, Var member) {
    assert(Types.isArrayRef(array));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(Types.isElemType(array, member));

    Command r = Turbine.arrayRefDerefStoreComputed(
        varToExpr(member), varToExpr(array),
        varToExpr(ix), TurbineTypes.arrayValueType(array, false));

    pointAdd(r);
  }

  @Override
  public void arrayBuild(Var array, List<Arg> keys, List<Arg> vals) {
    assert(Types.isArray(array));
    assert(keys.size() == vals.size());
    int elemCount = keys.size();


    List<Pair<Expression, Expression>> kvExprs =
        new ArrayList<Pair<Expression, Expression>>(elemCount);
    for (int i = 0; i < elemCount; i++) {
      Arg key = keys.get(i);
      Arg val = vals.get(i);
      assert(Types.isElemValType(array, val));
      assert(Types.isArrayKeyVal(array, key));
      kvExprs.add(Pair.<Expression, Expression>create(
                  argToExpr(key), argToExpr(val)));
    }

    Dict dict = Dict.dictCreate(true, kvExprs);

    pointAdd(arrayBuild(array, dict));
  }

  @Override
  public void asyncCopy(Var dst, Var src) {
    assert(src.type().assignableTo(dst.type()));

    startAsync("copy-" + src.name() + "_" + dst.name(),
               src.asList(), Arrays.asList(src, dst), false,
               ExecTarget.nonDispatchedAny(), new TaskProps());
    syncCopy(dst, src);
    endAsync();
  }

  @Override
  public void syncCopy(Var dst, Var src) {
    assert(dst.storage() != Alloc.LOCAL);
    assert(src.type().assignableTo(dst.type()));

    syncCopy(dst, src, copyIncrReferand(dst));
  }

  /**
   * Compute number we need to increment referand
   * @param dst
   * @return
   */
  private static Expression copyIncrReferand(Var dst) {
    // Depending on type, see if there are any refcounts
    // that need to be incremented
    if (Types.isContainer(dst)) {
      Type elemType = Types.containerElemType(dst);
      boolean refElems = Types.isRef(elemType);
      return refElems ? LiteralInt.ONE : LiteralInt.ZERO;
    } else if (Types.isStruct(dst)) {
      // Could check to see if any ref elems
      return LiteralInt.ONE;
    } else if (Types.isPrimFuture(dst)) {
      return LiteralInt.ZERO;
    } else if (Types.isRef(dst)) {
      return LiteralInt.ONE;
    }
    // Default to one: safe since ignored if not needed
    return LiteralInt.ONE;

  }

  /**
   * Helper to synchronously copy a compound type
   * @param dst
   * @param src
   * @param incrReferand
   */
  private void syncCopy(Var dst, Var src, Expression incrReferand) {
    // Implement as load followed by store
    Value tmpVal = new Value(TCLTMP_RETRIEVED);
    TypeName simpleReprType = TurbineTypes.reprType(src);
    pointAdd(Turbine.retrieveAcquire(tmpVal.variable(), varToExpr(src),
                               simpleReprType, incrReferand, LiteralInt.ONE));


    // Must decrement any refcounts not explicitly tracked since
    // we're assigning the struct in whole
    long writeDecr = RefCounting.baseWriteRefCount(dst, true, true);

    List<Expression> fullReprType;

    if (Types.isContainer(src)) {
      fullReprType = TurbineTypes.adlbStoreTypeInfo(dst);
    } else {
      fullReprType = Collections.<Expression>singletonList(
                        TurbineTypes.reprType(src));
    }
    pointAdd(Turbine.adlbStore(varToExpr(dst), tmpVal, fullReprType,
                              new LiteralInt(writeDecr), null));
  }

  /**
   * Helper function to generate arrayBuild call given a Tcl expression
   * that is a dict
   * @param array
   * @param dict
   */
  private Command arrayBuild(Var array, Expression dict) {
    TypeName keyType = TurbineTypes.reprType(Types.arrayKeyType(array));
    Type valType2 = Types.containerElemType(array);
    TypeName valType = TurbineTypes.reprType(valType2);

    return Turbine.arrayBuild(varToExpr(array), dict, LiteralInt.ONE,
                keyType, Collections.singletonList(valType));
  }

  @Override
  public void bagInsert(Var bag, Arg elem, Arg writersDecr) {
    assert(Types.isElemValType(bag, elem));
    pointAdd(Turbine.bagAppend(varToExpr(bag),
          TurbineTypes.arrayValueType(bag, false), argToExpr(elem),
          argToExpr(writersDecr)));
  }

  @Override
  public void initScalarUpdateable(Var updateable, Arg val) {
    assert(Types.isScalarUpdateable(updateable));
    if (!updateable.type().equals(Types.UP_FLOAT)) {
      throw new STCRuntimeError(updateable.type() +
          " not yet supported");
    }
    assert(val.isImmFloat());
    pointAdd(Turbine.updateableFloatInit(varToExpr(updateable),
                                                      argToExpr(val)));
  }

  @Override
  public void latestValue(Var result, Var updateable) {
    assert(Types.isScalarUpdateable(updateable));
    assert(Types.isScalarValue(result));
    assert(updateable.type().primType() ==
                  result.type().primType());
    if (!updateable.type().equals(Types.UP_FLOAT)) {
      throw new STCRuntimeError(updateable.type().typeName()
              + " not yet supported");
    }
    // get with caching disabled
    pointAdd(Turbine.floatGet(prefixVar(result),
                          varToExpr(updateable), CacheMode.UNCACHED));
  }

  @Override
  public void updateScalarFuture(Var updateable, UpdateMode updateMode, Var val) {
    assert(Types.isScalarUpdateable(updateable));
    assert(Types.isScalarFuture(val));
    assert(updateable.type().primType() == val.type().primType());
    assert(updateMode != null);
    String builtinName = getUpdateBuiltin(updateMode);
    pointAdd(new Command(builtinName, Arrays.asList(
                  (Expression)varToExpr(updateable), varToExpr(val))));
  }

  private String getUpdateBuiltin(UpdateMode updateMode) {
    String builtinName;
    switch(updateMode) {
    case INCR:
      builtinName = "turbine::update_incr";
      break;
    case MIN:
      builtinName = "turbine::update_min";
      break;
    case SCALE:
      builtinName = "turbine::update_scale";
      break;
    default:
      throw new STCRuntimeError("Unknown UpdateMode: " + updateMode);
    }
    return builtinName;
  }

  @Override
  public void updateScalarImm(Var updateable, UpdateMode updateMode,
                                                Arg val) {
    assert(Types.isScalarUpdateable(updateable));
    if (updateable.type().equals(Types.UP_FLOAT)) {
      assert(val.isImmFloat());
    } else {
      throw new STCRuntimeError("only updateable floats are"
          + " implemented so far");
    }
    assert(updateMode != null);
    String builtinName = getUpdateBuiltin(updateMode) + "_impl";
    pointAdd(new Command(builtinName, Arrays.asList(
        varToExpr(updateable), argToExpr(val))));
  }

  /** This prevents duplicate "package require" statements */
  private final Set<String> requiredPackages = new HashSet<String>();

  @Override
  public void requirePackage(RequiredPackage pkg) {
    if (!(pkg instanceof TclPackage)) {
      throw new STCRuntimeError("Expected Tcl package, but got: " +
                                 pkg.getClass().getCanonicalName());
    }

    TclPackage tclPkg = (TclPackage)pkg;
    String pv = tclPkg.name + tclPkg.version;
    if (!tclPkg.name.equals("turbine")) {
      if (!requiredPackages.contains(pv))
      {
        PackageRequire pr = new PackageRequire(tclPkg.name, tclPkg.version);
        pointAdd(pr);
        requiredPackages.add(tclPkg.name);
        pointAdd(new Command(""));
      }
    }
  }

  @Override
  public void defineForeignFunction(FnID id, FunctionType type,
        LocalForeignFunction localImpl, WrappedForeignFunction wrappedImpl) {
    if (wrappedImpl != null && !(wrappedImpl instanceof TclFunRef)) {
      throw new STCRuntimeError("Bad foreign function type: " +
                         wrappedImpl.getClass().getCanonicalName());
    }
    if (localImpl != null && !(localImpl instanceof TclOpTemplate)) {
      throw new STCRuntimeError("Bad foreign function type: " +
                          localImpl.getClass().getCanonicalName());
    }

    TclFunRef tclWrappedImpl = (TclFunRef)wrappedImpl;
    TclOpTemplate tclLocalImpl = (TclOpTemplate)localImpl;
    tclFuncSymbols.put(id, Pair.create(tclLocalImpl, tclWrappedImpl));
    logger.debug("TurbineGenerator: Defined built-in " + id);
  }

  @Override
  public void startFunction(FnID id, List<Var> oList, List<Var> iList,
                           ExecTarget mode) throws UserException {
    List<String> outputs = prefixVars(oList);
    List<String> inputs  = prefixVars(iList);
    // System.out.println("function" + functionName);
    boolean isEntryPoint = id.equals(FnID.ENTRY_FUNCTION);
    String prefixedFunctionName = null;
    if (isEntryPoint)
      prefixedFunctionName = ENTRY_FUNCTION_NAME;
    else
      prefixedFunctionName = TclNamer.swiftFuncName(id);

    List<String> args =
      new ArrayList<String>(inputs.size()+outputs.size());
    args.addAll(outputs);
    args.addAll(inputs);

    // This better be the bottom
    Sequence point = point();

    Sequence s = new Sequence();
    Proc proc = new Proc(prefixedFunctionName,
                         usedTclFunctionNames, args, s);

    point.add(proc);
    s.add(Turbine.turbineLog("enter function: " + id));

    pointPush(s);
    functionStack.push(id);
  }

  @Override
  public void endFunction() {
    pointPop();
    functionStack.pop();
  }

  @Override
  public void startNestedBlock() {
    Sequence block = new Sequence();
    Sequence point = point();
    point.add(block);
    pointPush(block);
  }

  @Override
  public void endNestedBlock() {
    pointPop();
  }

  @Override
  public void addComment(String comment) {
    pointAdd(new Comment(comment));
  }


  /**
   * @param condition the variable name to branch based on
   * @param hasElse whether there will be an else clause ie. whether startElseBlock()
   *                will be called later for this if statement
   */
  @Override
  public void startIfStatement(Arg condition, boolean hasElse) {
    logger.trace("startIfStatement()...");
    assert(condition != null);
    assert(!condition.isVar()
        || condition.getVar().storage() == Alloc.LOCAL);
    assert(condition.isImmBool() || condition.isImmInt());


    Sequence thenBlock = new Sequence();
    Sequence elseBlock = hasElse ? new Sequence() : null;

    If i = new If(argToExpr(condition),
        thenBlock, elseBlock);
    pointAdd(i);

    if (hasElse) {
       // Put it on the stack so it can be fetched when we start else block
      pointPush(elseBlock);
    }
    pointPush(thenBlock);
  }

  @Override
  public void startElseBlock() {
      logger.trace("startElseBlock()...");
    pointPop(); // Remove then block
  }

  @Override
  public void endIfStatement() {
    logger.trace("endIfStatement()...");
    pointPop();
  }

    @Override
    public void startWaitStatement(String procName, List<Var> waitVars,
        List<Var> passIn, boolean recursive, ExecTarget target,
        TaskProps props) {
      logger.trace("startWaitStatement()...");
      startAsync(procName, waitVars, passIn, recursive, target, props);
    }

    @Override
    public void endWaitStatement() {
      logger.trace("endWaitStatement()...");
      endAsync();
    }

    /**
     * Internal helper to implement constructs that need to wait for
     * a number of variables, and then run some code
     * @param procName
     * @param waitVars
     * @param passIn
     * @param priority
     * @param recursive
     */
    private void startAsync(String procName, List<Var> waitVars,
        List<Var> passIn, boolean recursive, ExecTarget mode, TaskProps props) {
      props.assertInternalTypesValid();

      // Track target contexts
      usedExecContexts.add(mode.targetContext());

      for (Var v: passIn) {
        if (Types.isBlobVal(v)) {
          throw new STCRuntimeError("Can't directly pass blob value: " + v);
        }
      }

      List<String> args = new ArrayList<String>();
      for (Var v: passIn) {
        args.add(prefixVar(v));
      }

      String uniqueName = uniqueTCLFunctionName(procName);
      Proc proc = new Proc(uniqueName, usedTclFunctionNames, args);
      tree.add(proc);

      // True if we have to use special wait impl
      boolean useDeepWait = recursive &&
              anySupportRecursiveWait(waitVars);

      List<Expression> waitFor = getTurbineWaitIDs(waitVars);

      // Build up the rule string
      List<Expression> action = buildActionFromVars(uniqueName, passIn);

      RuleProps ruleProps = buildRuleProps(props);
      if (useDeepWait) {
        // Nesting depth of arrays (0 == not array)
        int depths[] = new int[waitVars.size()];
        Expression baseTypes[] = new Expression[waitVars.size()];
        for (int i = 0; i < waitVars.size(); i++) {
          Type waitVarType = waitVars.get(i).type();
          Pair<Integer, Expression> data =
              TurbineTypes.depthBaseDescriptor(waitVarType);
          depths[i] = data.val1;
          baseTypes[i] = data.val2;
        }

        Sequence rule = Turbine.deepRule(uniqueName, waitFor, depths,
              baseTypes, action, mode, execContextStack.peek(), ruleProps);
        point().append(rule);

      } else {
        // Whether we can enqueue rules locally
        point().append(Turbine.rule(uniqueName,
            waitFor, action, mode, execContextStack.peek(), ruleProps));
      }

      pointPush(proc.getBody());

      execContextStack.push(mode.actualContext(execContextStack.peek()));
    }

    private boolean anySupportRecursiveWait(List<Var> waitVars) {
      for (Var w: waitVars) {
        if (checkRecursiveWait(w))
          return true;
      }
      return false;
    }

    /**
     * Return true if recursive and non-recursive wait are different for variable.
     * Throw exception if not supported yet
     * @param var
     * @return
     */
    private boolean checkRecursiveWait(Typed typed) {
      boolean requiresRecursion = false;
      Type baseType = typed.type();
      if (Types.isContainer(typed)) {
        baseType = new NestedContainerInfo(baseType).baseType;
        requiresRecursion = true;
      }

      if (Types.isPrimFuture(baseType)) {
        // ok
      } else if (Types.isRef(baseType)) {
        // Can follow refs

        // Check valid
        checkRecursiveWait(Types.retrievedType(baseType));
        requiresRecursion = true;
      } else if (Types.isStruct(baseType)) {
        // Can't follow struct field refs yet
        for (StructField f: ((StructType)baseType.getImplType()).fields()) {
          if (checkRecursiveWait(f.type())) {
            requiresRecursion = true;
          }
        }
      } else {
        throw new STCRuntimeError("Recursive wait not yet supported"
            + " for type: " + typed.type().typeName());
      }
      return requiresRecursion;
    }

    private void endAsync() {
      execContextStack.pop();
      pointPop();
    }

    /**
     * Increment refcount of all vars by one
     * @param vars
     */
    private void incrementReaders(List<Var> vars, Expression incr) {
      point().append(buildIncReaders(vars, incr, false));
    }

    private void decrementReaders(List<Var> vars, Expression decr) {
      point().append(buildIncReaders(vars, decr, true));
    }

    /**
     * Increment readers by a
     * @param vars
     * @param incr expression for the amount of increment/decrement.  If null, assume 1
     * @param negate if true, then negate incr
     * @return
     */
    private static Sequence buildIncReaders(List<Var> vars, Expression incr, boolean negate) {
      Sequence seq = new Sequence();
      for (VarCount vc: Var.countVars(vars)) {
        Var var = vc.var;
        if (!RefCounting.trackReadRefCount(var)) {
          continue;
        }
        Expression amount;
        if (vc.count == 1 && incr == null) {
          amount = null;
        } else if (incr == null) {
          amount = new LiteralInt(vc.count);
        } else if (vc.count == 1 && incr != null) {
          amount = incr;
        } else {
          amount = TclExpr.mult(new LiteralInt(vc.count), incr);
        }
        if (Types.isFile(var)) {
          // Need to use different function to handle file reference
          if (negate) {
            seq.add(Turbine.decrFileRef(varToExpr(var), amount));
          } else {
            seq.add(Turbine.incrFileRef(varToExpr(var), amount));
          }
        } else {
          if (negate) {
            seq.add(Turbine.decrRef(varToExpr(var), amount));
          } else {
            seq.add(Turbine.incrRef(varToExpr(var), amount));
          }
        }
      }
      return seq;
    }

    private void incrementWriters(List<Var> keepOpenVars,
          Expression incr) {
      Sequence seq = new Sequence();
      for (VarCount vc: Var.countVars(keepOpenVars)) {
        if (!RefCounting.trackWriteRefCount(vc.var)) {
          continue;
        }

        if (incr == null) {
          seq.add(Turbine.incrWriters(varToExpr(vc.var), new LiteralInt(vc.count)));
        } else if (vc.count == 1) {
          seq.add(Turbine.incrWriters(varToExpr(vc.var), incr));
        } else {
          seq.add(Turbine.incrWriters(varToExpr(vc.var),
              TclExpr.mult(new LiteralInt(vc.count), incr)));
        }
      }
      point().append(seq);
    }

    private void decrementWriters(List<Var> vars,
                                             Expression decr) {
      Sequence seq = new Sequence();
      for (VarCount vc: Var.countVars(vars)) {
        if (!RefCounting.trackWriteRefCount(vc.var)) {
          continue;
        }
        if (decr == null) {
          seq.add(Turbine.decrWriters(varToExpr(vc.var), new LiteralInt(vc.count)));
        } else if (vc.count == 1) {
          seq.add(Turbine.decrWriters(varToExpr(vc.var), decr));
        } else {
          seq.add(Turbine.decrWriters(varToExpr(vc.var),
              TclExpr.mult(new LiteralInt(vc.count), decr)));
        }
      }
      point().append(seq);
    }

    private List<Expression> buildAction(String procName, List<Expression> args) {
      ArrayList<Expression> ruleTokens = new ArrayList<Expression>();
      ruleTokens.add(new Token(procName));
      ruleTokens.addAll(args);

      return ruleTokens;
    }

    private List<Expression> buildActionFromVars(String procName, List<Var> usedVariables) {
      List<Expression> exprs = new ArrayList<Expression>();
      // Pass in variable ids directly in rule string
      for (Var v: usedVariables) {
        Type t = v.type();
        if (Types.isPrimFuture(t) || Types.isRef(t) ||
            Types.isArray(t) || Types.isStruct(t) ||
            Types.isPrimUpdateable(t) || Types.isBag(t)) {
          // Just passing turbine id
          exprs.add(varToExpr(v));
        } else if (Types.isPrimValue(t)) {
          PrimType pt = t.primType();
          if (pt == PrimType.INT || pt == PrimType.BOOL
              || pt == PrimType.FLOAT || pt == PrimType.STRING
              || pt == PrimType.FILE) {
            // Serialize
            exprs.add(varToExpr(v));
          } else if (pt == PrimType.VOID) {
            // Add a dummy token
            exprs.add(new Token("void"));
          } else {
            throw new STCRuntimeError("Don't know how to pass var " + v);
          }
        } else if (Types.isContainerLocal(t) || Types.isStructLocal(t)) {
          exprs.add(varToExpr(v));
        } else {
          throw new STCRuntimeError("Don't know how to pass var with type "
              + v);
        }
      }
      return buildAction(procName, exprs);
    }



    @Override
    public void startSwitch(Arg switchVar, List<Integer> caseLabels,
              boolean hasDefault) {
    logger.trace("startSwitch()...");
    assert(switchVar != null);
    assert(!switchVar.isVar() ||
        switchVar.getVar().storage() == Alloc.LOCAL);
    assert(switchVar.isImmInt());

    int casecount = caseLabels.size();
    if (hasDefault) casecount++;

    List<Sequence> caseBodies = new ArrayList<Sequence>(casecount);
    for (int c=0; c < casecount; c++) {
      Sequence casebody = new Sequence();
      // there might be new locals in the case
      caseBodies.add(casebody);
    }

    Switch sw = new Switch(argToExpr(switchVar),
        caseLabels, hasDefault, caseBodies);
    pointAdd(sw);

    for (int c = 1; c <= casecount; c++) {
      // Push case in reverse order so we can pop off as we add cases
      pointPush(caseBodies.get(casecount - c));
    }
  }

    @Override
    public void endCase() {
    logger.trace("endCase()...");
    // Pop the body of the last case statement off the stack
    pointPop();

  }

  @Override
  public void endSwitch() {
    logger.trace("endSwitch()...");
    // don't pop anything off, last case should already be gone
  }

  @Override
  public void startForeachLoop(String loopName, Var container, Var memberVar,
        Var loopCountVar, int splitDegree, int leafDegree, boolean arrayClosed,
        List<PassedVar> passedVars, List<RefCount> perIterIncrs,
        ListMultimap<Var, RefCount> constIncrs) {
    boolean haveKeys = loopCountVar != null;

    boolean isKVContainer;
    if (Types.isArray(container) || Types.isArrayLocal(container)) {
      assert(!haveKeys ||
            Types.isArrayKeyVal(container, loopCountVar.asArg()));
      isKVContainer = true;
    } else {
      assert(Types.isBag(container) || Types.isBagLocal(container));
      assert(!haveKeys);
      isKVContainer = false;
    }


    boolean localContainer = Types.isContainerLocal(container);

    if (localContainer) {
      if (!arrayClosed || splitDegree >= 0) {
        throw new STCRuntimeError(
            "Can't do async foreach with local container currently;");
      }

      assert(Types.isElemType(container, memberVar));
    } else {
      assert(Types.isElemValType(container, memberVar));
    }

    if (!arrayClosed) {
      throw new STCRuntimeError("Loops over open containers not yet supported");
    }

    boolean isDict;
    Value tclContainer;
    if (splitDegree <= 0) {
      if (localContainer) {
        // Already have var
        tclContainer = varToExpr(container);
        isDict = isKVContainer;
      } else {
        // Load container contents and increment refcounts
        // Only get keys if needed
        tclContainer = new Value(TCLTMP_ARRAY_CONTENTS);
        pointAdd(Turbine.enumerateAll(tclContainer.variable(),
                            varToExpr(container), haveKeys));
        isDict = haveKeys;
      }

      Expression containerSize;
      if (isDict) {
        containerSize = Turbine.dictSize(tclContainer);
      } else {
        containerSize = Turbine.listLength(tclContainer);
      }
      handleForeachContainerRefcounts(perIterIncrs, constIncrs, containerSize);
    } else {
      assert(!localContainer);
      tclContainer = new Value(TCLTMP_ARRAY_CONTENTS);
      startForeachSplit(loopName, container, tclContainer.variable(),
          splitDegree, leafDegree, haveKeys, passedVars, perIterIncrs,
          constIncrs);
      isDict = haveKeys;
    }
    startForeachInner(tclContainer, memberVar, loopCountVar, isDict);
  }

  private void handleForeachContainerRefcounts(List<RefCount> perIterIncrs,
      ListMultimap<Var, RefCount> constIncrs, Expression containerSize) {
    if (!perIterIncrs.isEmpty()) {
      pointAdd(new SetVariable(TCLTMP_ITERS,
                                      containerSize));

      handleRefcounts(constIncrs, perIterIncrs, Value.numericValue(TCLTMP_ITERS), false);
    }
  }

  private void startForeachSplit(String procName, Var arrayVar,
      String contentsVar, int splitDegree, int leafDegree, boolean haveKeys,
      List<PassedVar> usedVars, List<RefCount> perIterIncrs,
      ListMultimap<Var, RefCount> constIncrs) {
    // load array size
    pointAdd(Turbine.containerSize(TCLTMP_CONTAINER_SIZE,
                                      varToExpr(arrayVar)));
    Value containerSize = Value.numericValue(TCLTMP_CONTAINER_SIZE);

    Expression lastIndex = TclExpr.minus(containerSize, LiteralInt.ONE);

    handleForeachContainerRefcounts(perIterIncrs, constIncrs, containerSize);

    // recursively split the range
    ArrayList<PassedVar> splitUsedVars = new ArrayList<PassedVar>(usedVars);
    if (!PassedVar.contains(splitUsedVars, arrayVar)) {
      splitUsedVars.add(new PassedVar(arrayVar, false));
    }
    startRangeSplit(procName, splitUsedVars, perIterIncrs, splitDegree,
                    leafDegree, LiteralInt.ZERO, lastIndex, LiteralInt.ONE);

    // need to find the length of this split since that is what the turbine
    //  call wants
    pointAdd(new SetVariable(TCLTMP_SPLITLEN,
            new TclExpr(Value.numericValue(TCLTMP_RANGE_HI), TclExpr.MINUS,
                        Value.numericValue(TCLTMP_RANGE_LO), TclExpr.PLUS,
                        LiteralInt.ONE)));

    // load the subcontainer
    pointAdd(Turbine.enumerate(contentsVar, varToExpr(arrayVar),
        haveKeys, TCLTMP_RANGE_LO_V, Value.numericValue(TCLTMP_SPLITLEN)));
  }

  private void startForeachInner(
      Value arrayContents, Var memberVar, Var loopCountVar, boolean isDict) {
    Sequence curr = point();
    Sequence loopBody = new Sequence();

    String tclMemberVar = prefixVar(memberVar);
    String tclCountVar = (loopCountVar != null) ?
                  prefixVar(loopCountVar) : TCLTMP_IGNORE;

    /* Iterate over keys and values, or just values */
    Sequence tclLoop;
    if (isDict) {
      tclLoop = new DictFor(new Token(tclCountVar), new Token(tclMemberVar),
                      arrayContents, loopBody);
    } else {
      tclLoop = new ForEach(new Token(tclMemberVar), arrayContents, loopBody);
    }
    curr.add(tclLoop);
    pointPush(loopBody);
  }


  @Override
  public void endForeachLoop(int splitDegree, boolean arrayClosed,
                  List<RefCount> perIterDecrements) {
    assert(pointStack.size() >= 2);
    pointPop(); // tclloop body
    if (splitDegree > 0) {
      endRangeSplit(perIterDecrements);
    }
  }

  @Override
  public void startRangeLoop(String loopName, Var loopVar, Var countVar,
      Arg start, Arg end, Arg increment, int splitDegree, int leafDegree,
      List<PassedVar> passedVars, List<RefCount> perIterIncrs,
      ListMultimap<Var, RefCount> constIncrs) {
    if (countVar != null) {
      // TODO
      throw new STCRuntimeError("Backend doesn't support counter var in range " +
                                "loop yet");
    }

    if (start.isImmInt()) {
      assert(Types.isIntVal(loopVar));
      String loopVarName = prefixVar(loopVar);
      startIntRangeLoop(loopName, loopVarName, start, end, increment,
          splitDegree, leafDegree, passedVars, perIterIncrs, constIncrs);
    } else {
      assert(start.isImmFloat()) : "Invalid range loop type " + start.type();
      startFloatRangeLoop(loopName, loopVar, start, end, increment,
          splitDegree, leafDegree, passedVars, perIterIncrs, constIncrs);
    }

  }

  private void startFloatRangeLoop(String loopName, Var loopVar,
      Arg start, Arg end, Arg increment, int splitDegree, int leafDegree,
      List<PassedVar> passedVars, List<RefCount> perIterIncrs,
      ListMultimap<Var, RefCount> constIncrs) {
    assert(start.isImmFloat());
    assert(end.isImmFloat());
    assert(increment.isImmFloat());

    assert(Types.isFloatVal(loopVar));

    Expression startE = argToExpr(start);
    Expression endE = argToExpr(end);
    Expression incrE = argToExpr(increment);

    // Iterate over integers to get the index of each float
    Value iterLimitVar = new Value(TCLTMP_FLOAT_RANGE_ITERMAX);

    pointAdd(new SetVariable(iterLimitVar.variable(),
        new TclExpr(
            TclExpr.exprFn(TclExpr.INT_CONV, TclExpr.group(
                TclExpr.exprFn(TclExpr.FLOOR, TclExpr.group(
                    TclExpr.paren(endE, TclExpr.MINUS, startE,
                                  TclExpr.PLUS, incrE),
                    TclExpr.DIV, incrE)
                 )
             )), TclExpr.MINUS, LiteralInt.ONE)));

    Value dummyLoopVar = new Value(TCLTMP_FLOAT_RANGE_ITER);

    // Passed vars plus variables used in calculation
    List<PassedVar> passedVars2 = PassedVar.mergeLists(passedVars,
                      PassedVar.fromArgs(false, start, increment));

    startIntRangeLoop2(loopName, dummyLoopVar.variable(),
        LiteralInt.ZERO, iterLimitVar, LiteralInt.ONE,
        splitDegree, leafDegree, passedVars2, perIterIncrs, constIncrs);

    // TODO: need pass in values?
    // Compute real float loop var
    pointAdd(new SetVariable(prefixVar(loopVar),
        new TclExpr(startE, TclExpr.PLUS, incrE, TclExpr.TIMES, dummyLoopVar)));
  }

  private void startIntRangeLoop(String loopName, String loopVarName,
      Arg start, Arg end, Arg increment, int splitDegree, int leafDegree,
      List<PassedVar> passedVars, List<RefCount> perIterIncrs,
      ListMultimap<Var, RefCount> constIncrs) {
    assert(start.isImmInt());
    assert(end.isImmInt());
    assert(increment.isImmInt());

    startIntRangeLoop2(loopName, loopVarName,
        argToExpr(start), argToExpr(end), argToExpr(increment),
        splitDegree, leafDegree, passedVars, perIterIncrs, constIncrs);
  }

  private void startIntRangeLoop2(String loopName, String loopVarName,
      Expression start, Expression end, Expression incr,
      int splitDegree, int leafDegree, List<PassedVar> passedVars,
      List<RefCount> perIterIncrs, ListMultimap<Var, RefCount> constIncrs) {
    if (!perIterIncrs.isEmpty()) {
      // Increment references by # of iterations
      pointAdd(new SetVariable(TCLTMP_ITERSTOTAL,
                       rangeItersLeft(start, end, incr)));

      Value itersTotal = Value.numericValue(TCLTMP_ITERSTOTAL);
      handleRefcounts(constIncrs, perIterIncrs, itersTotal, false);
    }

    if (splitDegree > 0) {
      startRangeSplit(loopName, passedVars, perIterIncrs,
              splitDegree, leafDegree, start, end, incr);
      startRangeLoopInner(loopName, loopVarName,
          TCLTMP_RANGE_LO_V, TCLTMP_RANGE_HI_V, TCLTMP_RANGE_INC_V);
    } else {
      startRangeLoopInner(loopName, loopVarName, start, end, incr);
    }
  }

  @Override
  public void endRangeLoop(int splitDegree, List<RefCount> perIterDecrements) {
    assert(pointStack.size() >= 2);
    pointPop(); // for loop body

    if (splitDegree > 0) {
      endRangeSplit(perIterDecrements);
    }
  }

  private void startRangeLoopInner(String loopName, String loopVarName,
          Expression startE, Expression endE, Expression incrE) {
    Sequence loopBody = new Sequence();
    ForLoop tclLoop = new ForLoop(loopVarName, startE, endE, incrE, loopBody);
    pointAdd(tclLoop);
    pointPush(loopBody);
  }

  /**
   * After this function is called, in the TCL context at the top of the stack
   * will be available the bottom, top (inclusive) and increment of the split in
   * tcl values: TCLTMP_RANGE_LO TCLTMP_RANGE_HI and TCLTMP_RANGE_INC
   * @param loopName
   * @param splitDegree
   * @param leafDegree
   * @param startE start of range (inclusive)
   * @param endE end of range (inclusive)
   * @param incrE
   * @param usedVariables
   */
  private void startRangeSplit(String loopName,
          List<PassedVar> passedVars, List<RefCount> perIterIncrs, int splitDegree,
          int leafDegree, Expression startE, Expression endE,
          Expression incrE) {
    // Create two procedures that will be called: an outer procedure
    //  that recursively breaks up the foreach loop into chunks,
    //  and an inner procedure that actually runs the loop
    List<String> commonFormalArgs = new ArrayList<String>();
    for (PassedVar pv: passedVars) {
      commonFormalArgs.add(prefixVar(pv.var.name()));
    }

    Value loVal = Value.numericValue(TCLTMP_RANGE_LO);
    Value hiVal = Value.numericValue(TCLTMP_RANGE_HI);
    Value incVal = Value.numericValue(TCLTMP_RANGE_INC);

    commonFormalArgs.add(loVal.variable());
    commonFormalArgs.add(hiVal.variable());
    commonFormalArgs.add(incVal.variable());
    List<String> outerFormalArgs = new ArrayList<String>(commonFormalArgs);


    List<Expression> commonArgs = new ArrayList<Expression>();
    for (PassedVar pv: passedVars) {
      commonArgs.add(varToExpr(pv.var));
    }

    List<Expression> outerCallArgs = new ArrayList<Expression>(commonArgs);
    outerCallArgs.add(startE);
    outerCallArgs.add(endE);
    outerCallArgs.add(incrE);

    List<Expression> innerCallArgs = new ArrayList<Expression>(commonArgs);
    innerCallArgs.add(loVal);
    innerCallArgs.add(hiVal);
    innerCallArgs.add(incVal);

    Sequence outer = new Sequence();
    String outerProcName = uniqueTCLFunctionName(loopName + ":outer");
    tree.add(new Proc(outerProcName,
            usedTclFunctionNames, outerFormalArgs, outer));

    Sequence inner = new Sequence();
    String innerProcName = uniqueTCLFunctionName(loopName + ":inner");
    tree.add(new Proc(innerProcName,
          usedTclFunctionNames, commonFormalArgs, inner));

    // Call outer directly
    pointAdd(new Command(outerProcName, outerCallArgs));



    Expression done = new TclExpr(loVal, TclExpr.GT, hiVal);
    If finishedIf = new If(done, false);
    finishedIf.thenBlock().add(Command.returnCommand());
    outer.add(finishedIf);


    // While loop to allow us to process smaller chunks here
    WhileLoop iter = new WhileLoop(LiteralInt.ONE);
    outer.add(iter);

    Value itersLeft = Value.numericValue(TCLTMP_ITERSLEFT);
    iter.loopBody().add(new SetVariable(itersLeft.variable(),
              rangeItersLeft(loVal, hiVal, incVal)));

    Expression doneSplitting = new TclExpr(itersLeft,
            TclExpr.LTE, new LiteralInt(leafDegree));
    If splitIf = new If(doneSplitting, true);
    iter.loopBody().add(splitIf);


    // if (iters < splitFactor) then <call inner> else <split more>
    splitIf.thenBlock().add(new Command(innerProcName, innerCallArgs));
    splitIf.thenBlock().add(Command.returnCommand());

    splitIf.elseBlock().append(rangeDoSplit(splitDegree, leafDegree,
            loVal, hiVal, incVal,
            outerProcName, commonArgs, itersLeft));


    pointPush(inner);
  }

  /**
   * Spawn off n - 1 splits of the loop.  Update loVar and hiVar to reflect
   *    the size of the remaining split to be executed here.
   *
   * @param splitDegree
   * @param leafDegree
   * @param loVar var containing low index of loop upon entry to generated code.
   *          Will be reassigned by this code to a lower split range
   * @param hiVar var containing high index of loop upon entry to generated code.
   *          Will be reassigned by this code to a lower split range
   * @param incVar var containing increment of loop upon entry to generated code.
   * @param itersLeft Number of iterations left for loop
   * @param outerProcName proc to call for loop body
   * @param commonArgs first args for function call.  Also append
   *                    lo, hi and inc for inner loop
   * @return
   */
  private Sequence rangeDoSplit(int splitDegree, int leafDegree,
          Value lo, Value hi, Value inc, String outerProcName,
          List<Expression> commonArgs, Value itersLeft) {
    Value splitStart = Value.numericValue(TCLTMP_SPLIT_START);
    Value skip = Value.numericValue(TCLTMP_SKIP);
    Value splitEnd = Value.numericValue(TCLTMP_SPLITEND);


    Sequence result = new Sequence();

    // skip = max(splitFactor,  ceil(iters /(float) splitfactor))
    // skip = max(splitFactor,  ((iters - 1) /(int) splitfactor) + 1)
    result.add(new SetVariable(skip.variable(),
        TclExpr.mult(inc,
          TclExpr.max(new LiteralInt(leafDegree),
            TclExpr.group(
                TclExpr.paren(
                    TclExpr.paren(itersLeft, TclExpr.MINUS,
                        LiteralInt.ONE),
                     TclExpr.DIV,  new LiteralInt(splitDegree)),
                TclExpr.PLUS, LiteralInt.ONE)))));

    // Save first iteration to execute here
    TclExpr iter2Start = new TclExpr(true, Arrays.asList(
                                     lo, TclExpr.PLUS, skip));
    ForLoop splitLoop = new ForLoop(splitStart.variable(),
                                    iter2Start, hi, skip);
    result.add(splitLoop);
    result.add(new SetVariable(hi.variable(), new TclExpr(true,
            TclExpr.group(lo, TclExpr.PLUS, skip,
                            TclExpr.MINUS, LiteralInt.ONE))));

    // splitEnd = min(hi, start + skip - 1)
    TclExpr splitEndExpr = new TclExpr(
        TclExpr.min(hi,
          TclExpr.group(splitStart, TclExpr.PLUS,
                        skip, TclExpr.MINUS,
                        LiteralInt.ONE)));
    splitLoop.loopBody().add(new SetVariable(splitEnd.variable(), splitEndExpr));

    ArrayList<Expression> outerRecCall = new ArrayList<Expression>();
    outerRecCall.add(new Token(outerProcName));
    outerRecCall.addAll(commonArgs);
    outerRecCall.add(splitStart);
    outerRecCall.add(splitEnd);
    outerRecCall.add(inc);

    splitLoop.loopBody().add(Turbine.rule(outerProcName, new ArrayList<Value>(0),
                    outerRecCall, ExecTarget.dispatchedControl(),
                    execContextStack.peek(), RuleProps.DEFAULT));


    // TODO: add update range expressions

    return result;
  }

  /**
   * Generate refcounting code from RefCount list
   * @param constIncrs constant increments.  Assume that every constant incr
   *            has a corresponding multipled one.  This can be null
   * @param multipliedIncrs
   * @param multiplier amount to multiply all refcounts by
   * @param decrement if true, generate decrements instead
   */
  private void handleRefcounts(ListMultimap<Var, RefCount> constIncrs,
      List<RefCount> multipliedIncrs, Value multiplier, boolean decrement) {
    // Track which were added for validation
    Set<Pair<Var, RefCountType>> processed = new HashSet<Pair<Var, RefCountType>>();

    for (RefCount refCount: multipliedIncrs) {
      List<Expression> refCountExpr;
      if (refCount.amount.equals(Arg.ONE)) {
        refCountExpr = new ArrayList<Expression>();
        refCountExpr.add(multiplier);
      } else {
        refCountExpr = new ArrayList<Expression>();
        refCountExpr.addAll(Arrays.asList(
            argToExpr(refCount.amount), TclExpr.TIMES, multiplier));
      }


      if (constIncrs != null) {
        for (RefCount constRC: constIncrs.get(refCount.var)) {
          if (constRC.type == refCount.type) {
            if (constRC.amount.isInt() && constRC.amount.getInt() < 0) {
              refCountExpr.add(TclExpr.MINUS);
              refCountExpr.add(new LiteralInt(constRC.amount.getInt() * -1));
            } else {
              refCountExpr.add(TclExpr.PLUS);
              refCountExpr.add(argToExpr(constRC.amount));
            }
          }
        }
      }

      Expression totalAmount = new TclExpr(refCountExpr);

      if (refCount.type == RefCountType.READERS) {
        if (decrement) {
          decrementReaders(Arrays.asList(refCount.var), totalAmount);
        } else {
          incrementReaders(Arrays.asList(refCount.var), totalAmount);
        }
      } else {
        assert(refCount.type == RefCountType.WRITERS);
        if (decrement) {
          decrementWriters(Arrays.asList(refCount.var), totalAmount);
        } else {
          incrementWriters(Arrays.asList(refCount.var), totalAmount);
        }
      }
      processed.add(Pair.create(refCount.var, refCount.type));
    }

    // Check that all constant increments has a corresponding multiplier
    if (constIncrs != null) {
      for (RefCount rc: constIncrs.values()) {
        assert(processed.contains(Pair.create(rc.var, rc.type))) :
          rc + "\n" + constIncrs + "\n" + multipliedIncrs;
      }
    }
  }

  private TclExpr rangeItersLeft(Expression lo, Expression hi, Expression inc) {
    Expression calcLeft; // Expression to calculate how many left (may be neg.)
    if (LiteralInt.ONE.equals(inc)) {
      // More readable
      calcLeft = TclExpr.group(hi, TclExpr.MINUS, lo, TclExpr.PLUS,
                            LiteralInt.ONE);
    } else {
      calcLeft = TclExpr.group(
            TclExpr.paren(hi, TclExpr.MINUS, lo), TclExpr.DIV,
            inc, TclExpr.PLUS, LiteralInt.ONE);
    }
    return new TclExpr(TclExpr.max(LiteralInt.ZERO, calcLeft));
  }

  private void endRangeSplit(List<RefCount> perIterDecrements) {
    if (!perIterDecrements.isEmpty()) {
      // Decrement # of iterations executed in inner block
      pointAdd(new SetVariable(TCLTMP_ITERS,
                   rangeItersLeft(Value.numericValue(TCLTMP_RANGE_LO),
                                  Value.numericValue(TCLTMP_RANGE_HI),
                                  Value.numericValue(TCLTMP_RANGE_INC))));
      Value iters = Value.numericValue(TCLTMP_ITERS);
      handleRefcounts(null, perIterDecrements, iters, true);
    }
    pointPop(); // inner proc body
  }

  @Override
  public void addGlobalConst(Var var, Arg val) {
    String tclName = prefixVar(var);
    Value tclVal = new Value(tclName);
    globInit.add(Turbine.makeTCLGlobal(tclName));
    TypeName typePrefix;
    Expression expr;
    Command setCmd;
    switch (val.getKind()) {
    case INTVAL:
      typePrefix = Turbine.ADLB_INT_TYPE;
      expr = new LiteralInt(val.getInt());
      setCmd = Turbine.integerSet(tclVal, expr);
      break;
    case FLOATVAL:
      typePrefix = Turbine.ADLB_FLOAT_TYPE;
      expr = new LiteralFloat(val.getFloat());
      setCmd = Turbine.floatSet(tclVal, expr);
      break;
    case STRINGVAL:
      typePrefix = Turbine.ADLB_STRING_TYPE;
      expr = new TclString(val.getString(), true);
      setCmd = Turbine.stringSet(tclVal, expr);
      break;
    case BOOLVAL:
      typePrefix = Turbine.ADLB_INT_TYPE;
      expr = new LiteralInt(val.getBool() ? 1 : 0);
      setCmd = Turbine.integerSet(tclVal, expr);
      break;
    default:
      throw new STCRuntimeError("Non-constant oparg type "
          + val.getKind());
    }
    int debugSymbol = nextDebugSymbol(var);
    globInit.add(Turbine.allocatePermanent(tclName, typePrefix, debugSymbol));
    globInit.add(setCmd);
  }

  @Override
  public void declareGlobalVars(List<VarDecl> vars) {
    this.globalVars.addAll(vars);
  }

  private Sequence initGlobalVars() {
    List<String> varNames = new ArrayList<String>();
    List<TclList> createArgs = new ArrayList<TclList>();

    List<String> fileVarNames = new ArrayList<String>();
    List<TclList> fileCreateArgs = new ArrayList<TclList>();
    List<Boolean> isMapped = new ArrayList<Boolean>();

    Sequence commands = new Sequence();


    for (VarDecl decl: globalVars) {
      assert(decl.var.storage() == Alloc.GLOBAL_VAR);
      if (Types.isFile(decl.var)) {
        fileVarNames.add(prefixVar(decl.var));
        fileCreateArgs.add(createArgs(decl.var, decl.initReaders,
                                      decl.initWriters));
        isMapped.add(decl.var.mappedDecl());
      } else {
        varNames.add(prefixVar(decl.var));

        createArgs.add(createArgs(decl.var, decl.initReaders, decl.initWriters));
      }
    }

    commands.add(Turbine.batchDeclareGlobals(varNames, createArgs));

    commands.add(Turbine.batchDeclareGlobalFiles(fileVarNames, fileCreateArgs,
                                                 isMapped));

    return commands;
  }

  private static Value varToExpr(Var v) {
    return TclUtil.varToExpr(v);
  }

  /**
   * Return an expression for the Turbine id to
   * wait on.
   * @param var
   * @return
   */
  private Expression getTurbineWaitId(Var var) {
    Value wv = varToExpr(var);
    Expression waitExpr;
    if (Types.isFile(var)) {
      // Block on file status
      waitExpr = Turbine.getFileID(wv);
    } else if (Types.isScalarFuture(var) || Types.isRef(var) ||
            Types.isArray(var) || Types.isScalarUpdateable(var)||
            Types.isBag(var) || Types.isStruct(var)) {
      waitExpr = wv;
    } else {
      throw new STCRuntimeError("Don't know how to wait on var: "
              + var.toString());
    }
    return waitExpr;
  }

  private List<Expression> getTurbineWaitIDs(List<Var> waitVars) {
    List<Expression> waitFor = new ArrayList<Expression>();
    Set<Var> alreadyBlocking = new HashSet<Var>();
    for (Var v: waitVars) {
      if (!alreadyBlocking.contains(v)) {
        Expression waitExpr = getTurbineWaitId(v);
        waitFor.add(waitExpr);
        alreadyBlocking.add(v);
      }
    }
    return waitFor;
  }

  private Expression argToExpr(Arg in) {
    return TclUtil.argToExpr(in);
  }

  private Expression argToExpr(Arg in, boolean passThroughNull) {
    return TclUtil.argToExpr(in, passThroughNull);
  }

  private static String prefixVar(String varname) {
    return TclNamer.prefixVar(varname);
  }

  private static String prefixVar(Var var) {
    return TclNamer.prefixVar(var.name());
  }

  private static List<String> prefixVars(List<Var> vars) {
    return TclNamer.prefixVars(vars);
  }

  @Override
  public void startLoop(String loopName, List<Var> loopVars,
      List<Arg> initVals, List<Var> usedVariables,
      List<Var> initWaitVars,
      boolean simpleLoop) {
    assert(initWaitVars.isEmpty() || !simpleLoop) : initWaitVars;
    List<String> tclLoopVars = new ArrayList<String>();
    // call rule to start the loop, pass in initVals, usedVariables
    ArrayList<String> loopFnArgs = new ArrayList<String>();
    ArrayList<Expression> firstIterArgs = new ArrayList<Expression>();

    for (Var loopVar: loopVars) {
      String tclVar = prefixVar(loopVar);
      loopFnArgs.add(tclVar);
      tclLoopVars.add(tclVar);
    }
    for (Arg init: initVals) {
      firstIterArgs.add(argToExpr(init));
    }

    for (Var uv: usedVariables) {
      loopFnArgs.add(prefixVar(uv));
      firstIterArgs.add(varToExpr(uv));
    }


    // See which values the loop should block on
    ArrayList<Value> blockingVals = new ArrayList<Value>();
    for (Var initWaitVar: initWaitVars) {
      blockingVals.add(varToExpr(initWaitVar));
    }

    String uniqueLoopName = uniqueTCLFunctionName(loopName);

    pointAdd(Turbine.loopRule(
        uniqueLoopName, firstIterArgs, blockingVals, execContextStack.peek()));

    Proc loopProc = new Proc(uniqueLoopName, usedTclFunctionNames, loopFnArgs);
    tree.add(loopProc);

    if (simpleLoop) {
      // Implement execution of loop body immediately with while loop
      Value loopCond = new Value(TclNamer.TCL_TMP_LOOP_COND);
      WhileLoop iterFor = new WhileLoop(loopCond);
      loopProc.getBody().add(
          new SetVariable(loopCond.variable(), LiteralInt.TRUE));
      loopProc.getBody().add(iterFor);
      pointPush(iterFor.loopBody());

      // Update loop variables for next iteration
      If updateVars = new If(loopCond, false);
      pointAddEnd(updateVars);
      for (int i = 0; i < loopVars.size(); i++) {
        Var loopVar = loopVars.get(i);
        String tclLoopVar = prefixVar(loopVar);
        Value nextLoopVar = new Value(
              TclNamer.TCL_NEXTITER_PREFIX + tclLoopVar);
        updateVars.thenBlock().add(new SetVariable(tclLoopVar, nextLoopVar));
      }

    } else {
      // add loop body to pointstack, loop to loop stack
      pointPush(loopProc.getBody());
    }

    loopStack.push(new EnclosingLoop(uniqueLoopName, simpleLoop, tclLoopVars));
  }

  private String uniqueTCLFunctionName(String tclFunctionName) {
    String unique = tclFunctionName;
    int next = 1;
    while (usedTclFunctionNames.contains(unique)) {
      unique = tclFunctionName + "-" + next;
      next++;
    }
    return unique;
  }

  @Override
  public void loopContinue(List<Arg> newVals,
         List<Var> usedVariables,
         List<Boolean> blockingVars) {
    ArrayList<Expression> nextIterArgs = new ArrayList<Expression>();
    EnclosingLoop context = loopStack.peek();
    assert(context.tclLoopVarNames.size() == newVals.size());

    if (context.simpleLoop) {
      assert(blockingVars.indexOf(true) == -1) : newVals + " " + blockingVars;
      // Just assign variables for next iteration
      for (int i = 0; i < context.tclLoopVarNames.size(); i++) {
        String tclLoopVarName = context.tclLoopVarNames.get(i);
        String nextLoopVar = TclNamer.TCL_NEXTITER_PREFIX + tclLoopVarName;
        pointAdd(new SetVariable(nextLoopVar, argToExpr(newVals.get(i))));
      }
    } else {
      // Setup rule call to execute next iteration later

      for (Arg v: newVals) {
        nextIterArgs.add(argToExpr(v));
      }
      for (Var v: usedVariables) {
        nextIterArgs.add(varToExpr(v));
      }
      ArrayList<Var> blockingVars2 = new ArrayList<Var>();
      assert(newVals.size() == blockingVars.size());
      for (int i = 0; i < newVals.size(); i++) {
        Arg newVal = newVals.get(i);
        if (blockingVars.get(i) && newVal.isVar()) {
          assert(Types.canWaitForFinalize(newVal)) : newVal;

          blockingVars2.add(newVal.getVar());
        }
      }
      pointAdd(Turbine.loopRule(context.loopName,
          nextIterArgs, getTurbineWaitIDs(blockingVars2),
          execContextStack.peek()));
    }
  }

  @Override
  public void loopBreak(List<Var> loopUsedVars, List<Var> keepOpenVars) {
    EnclosingLoop context = loopStack.peek();
    if (context.simpleLoop) {
      // Break out of while loop after cleanups execute
      Value loopCond = new Value(TclNamer.TCL_TMP_LOOP_COND);
      pointAdd(new SetVariable(loopCond.variable(), LiteralInt.FALSE));
    } else {
      // Nothing: will fall out of function
    }
  }

  @Override
  public void endLoop() {
    assert(pointStack.size() >= 2);
    assert(loopStack.size() > 0);
    pointPop();
    loopStack.pop();
  }

  @Override
  public void checkpointLookupEnabled(Var out) {
    pointAdd(new SetVariable(prefixVar(out), Turbine.xptLookupEnabled()));
  }


  @Override
  public void startAsyncExec(String procName, List<Var> passIn,
      AsyncExecutor executor, Arg cmdName, List<Var> taskOutputs,
      List<Arg> taskArgs, Map<String, Arg> taskProps,
      boolean hasContinuation) {

    Proc proc = null;
    List<String> continuationArgs = new ArrayList<String>();
    List<Expression> continuationArgVals = new ArrayList<Expression>();

    if (hasContinuation) {
      // Setup proc for continuation
      String uniqueName = uniqueTCLFunctionName(procName);
      proc = new Proc(uniqueName, usedTclFunctionNames, continuationArgs);
      tree.add(proc);

      // Pass in context variables
      for (Var passVar: passIn) {
        continuationArgs.add(prefixVar(passVar));
        continuationArgVals.add(varToExpr(passVar));
      }
    }

    List<Token> outVarNames = new ArrayList<Token>(taskOutputs.size());
    for (Var taskOutput: taskOutputs) {
      outVarNames.add(new Token(prefixVar(taskOutput)));
    }

    List<Expression> taskArgExprs = new ArrayList<Expression>(taskArgs.size());
    for (Arg taskArg: taskArgs) {
      // May need to expand args onto command line
      if (executor.isAppExecutor()) {
        taskArgExprs.add(cmdLineArgExpr(taskArg));
      } else {
        taskArgExprs.add(argToExpr(taskArg));
      }
    }

    List<Pair<String, Expression>> taskPropExprs =
        new ArrayList<Pair<String,Expression>>(taskProps.size());
    for (Entry<String, Arg> e: taskProps.entrySet()) {
      taskPropExprs.add(Pair.create(e.getKey(), argToExpr(e.getValue())));
    }

    // Put properties into alphabetical order
    Collections.sort(taskPropExprs,
        new Comparator<Pair<String, Expression>> () {
      @Override
      public int compare(Pair<String, Expression> a,
          Pair<String, Expression> b) {
        return a.val1.compareTo(b.val1);
      }
    });

    List<Expression> continuation = new ArrayList<Expression>();
    if (hasContinuation) {
      continuation.add(new Token(proc.name()));
      continuation.addAll(continuationArgVals);
    }

    // TODO: information about stageIns/stageOuts
    List<Expression> stageIns = new ArrayList<Expression>();
    List<Expression> stageOuts = new ArrayList<Expression>();

    // TODO: proper failure continuation
    List<Expression> failureContinuation = new ArrayList<Expression>();
    failureContinuation.add(new Token("error"));
    failureContinuation.add(new TclString(Arrays.asList(
        new TclString("Execution of ", true), argToExpr(cmdName),
        new TclString(" failed", true)), ExprContext.VALUE_STRING));

    pointAdd(Turbine.asyncExec(executor, argToExpr(cmdName), outVarNames,
              taskArgExprs, taskPropExprs, stageIns, stageOuts,
              continuation, failureContinuation));

    if (hasContinuation) {
      // Enter proc body for code generation of continuation
      pointPush(proc.getBody());
    }
  }

  @Override
  public void endAsyncExec(boolean hasContinuation) {
    if (hasContinuation) {
      assert(pointStack.size() >= 2);
      pointPop();
    }
  }

  @Override
  public void checkpointWriteEnabled(Var out) {
    pointAdd(new SetVariable(prefixVar(out), Turbine.xptWriteEnabled()));
  }

  @Override
  public void writeCheckpoint(Arg key, Arg val) {
    assert(Types.isBlobVal(key));
    assert(Types.isBlobVal(val));
    // Write checkpoint with binary keys
    // Want to persist data to disk.
    // Don't need to store new entries in index.
    pointAdd(Turbine.xptWrite(argToExpr(key), argToExpr(val),
                  XptPersist.PERSIST, LiteralInt.FALSE));
  }

  @Override
  public void lookupCheckpoint(Var checkpointExists, Var val, Arg key) {
    assert(Types.isBoolVal(checkpointExists));
    assert(Types.isBlobVal(key));
    assert(Types.isBlobVal(val));

    pointAdd(Turbine.xptLookupStmt(prefixVar(checkpointExists),
            prefixVar(val), argToExpr(key)));
  }

  @Override
  public void packValues(Var packed, List<Arg> unpacked) {
    assert(Types.isBlobVal(packed));
    for (Arg u: unpacked) {
      assert(u.isConst() || u.getVar().storage() == Alloc.LOCAL);
    }

    // Need to pass type names to packing routine
    List<Expression> exprs = xptPackArgs(unpacked);
    pointAdd(new SetVariable(prefixVar(packed), Turbine.xptPack(exprs)));
  }

  /**
   * Used on local value types
   * Make a list of values, with each value preceded by the ADLB type.
   * For compound ADLB types, we have multiple expressions to describe
   * the "layers" of the type.
   * @param vals
   * @return
   */
  private List<Expression> xptPackArgs(List<Arg> vals) {
    List<Expression> result = new ArrayList<Expression>();
    for (Arg val: vals) {
      List<Expression> typeList = TurbineTypes.xptPackType(val);
      result.addAll(typeList);
      result.add(argToExpr(val));
    }
    return result;
  }

  @Override
  public void unpackValues(List<Var> unpacked, Arg packed) {
    List<String> unpackedVarNames = new ArrayList<String>(unpacked.size());
    List<TypeName> types = new ArrayList<TypeName>();
    for (Var unpackedVar: unpacked) {
      unpackedVarNames.add(prefixVar(unpackedVar));
      types.add(TurbineTypes.valReprType(unpackedVar));
    }
    pointAdd(Turbine.xptUnpack(unpackedVarNames, argToExpr(packed), types));
  }

  @Override
  public void unpackArrayToFlat(Var flatLocalArray, Arg inputArray) {
    // TODO: other container types?
    assert(Types.isArray(inputArray));
    NestedContainerInfo c = new NestedContainerInfo(inputArray.type());
    assert(Types.isArrayLocal(flatLocalArray));
    Type baseType = c.baseType;

    // Get type inside reference
    if (Types.isRef(baseType)) {
      baseType = Types.retrievedType(baseType);
    }

    Type memberValT = Types.retrievedType(baseType);

    assert(memberValT.assignableTo(Types.containerElemType(flatLocalArray)))
      : memberValT + " " + flatLocalArray;


    pointAdd(new SetVariable(prefixVar(flatLocalArray),
                              unpackArrayInternal(inputArray)));
  }

  private Expression unpackArrayInternal(Arg arg) {
    Pair<Integer, Expression> rct = TurbineTypes.depthBaseDescriptor(arg);
    Expression unpackArrayExpr = Turbine.unpackArray(
                            argToExpr(arg), rct.val1, rct.val2);
    return unpackArrayExpr;
  }

  private static class EnclosingLoop {
    private EnclosingLoop(String loopName, boolean simpleLoop,
        List<String> tclLoopVarNames) {
      this.loopName = loopName;
      this.simpleLoop = simpleLoop;
      this.tclLoopVarNames = tclLoopVarNames;
    }
    public final String loopName;
    public final boolean simpleLoop;
    public final List<String> tclLoopVarNames;
  }
}
