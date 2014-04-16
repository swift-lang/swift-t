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

import exm.stc.common.CompilerBackend;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.TclFunRef;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCFatal;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.AsyncExecutor;
import exm.stc.common.lang.CompileTimeArgs;
import exm.stc.common.lang.Constants;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.Unimplemented;
import exm.stc.common.lang.ForeignFunctions.TclOpTemplate;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Operators.UpdateMode;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FileKind;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.NestedContainerInfo;
import exm.stc.common.lang.Types.PrimType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarCount;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.tclbackend.Turbine.CacheMode;
import exm.stc.tclbackend.Turbine.RuleProps;
import exm.stc.tclbackend.Turbine.StackFrameType;
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
  private static final String TCLTMP_INIT_REFCOUNT = "tcltmp:init_rc";
  private static final String TCLTMP_SPLIT_START = "tcltmp:splitstart";
  private static final String TCLTMP_SKIP = "tcltmp:skip";
  private static final String TCLTMP_IGNORE = "tcltmp:ignore";
  
  private static final String MAIN_FUNCTION_NAME = "swift:main";
  private static final String CONSTINIT_FUNCTION_NAME = "swift:constants";

  final String timestamp;
  final Logger logger;

  /**
     Our output Tcl
     Convenience reference to bottom of pointStack
   */
  Sequence tree = new Sequence();


  /**
   * For function that initializes globals
   */
  Sequence globInit = new Sequence();

  /**
     Stack for previous values of point.
     First entry is the sequence
     Second entry is a list of things to add at end of sequence
   */
  StackLite<Pair<Sequence, Sequence>> pointStack = 
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
  StackLite<EnclosingLoop> loopStack = new StackLite<EnclosingLoop>();

  /**
   * Stack for what context we're in. 
   */
  StackLite<ExecContext> execContextStack = new StackLite<ExecContext>();

  String turbineVersion = Settings.get(Settings.TURBINE_VERSION);

  HashSet<String> usedTclFunctionNames = new HashSet<String>();

  
  private final TurbineStructs structTypes = new TurbineStructs();
  
  /**
   * TCL symbol names for builtins
   * Swift function name -> TCL proc name
   */
  private final HashMap<String, TclFunRef> builtinSymbols =
                      new HashMap<String, TclFunRef>();

  /**
     If true, enable debug comments in Tcl source
   */
  boolean debuggerComments = false;

  public TurbineGenerator(Logger logger, String timestamp)
  {
    this.logger = logger;
    this.timestamp = timestamp;
    pointPush(tree);
    
    execContextStack.push(ExecContext.CONTROL);

    if (Settings.get("DEBUGGER") == "COMMENTS")
      debuggerComments = true;
  }

  @Override
  public void header()
  {
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
  
  @Override
  public void turbineStartup()
  {
    tree.add(new Command("turbine::defaults"));
    if (Settings.NO_TURBINE_ENGINE) {
      tree.add(new Command("turbine::init $servers \"Swift\""));
    } else {
      tree.add(new Command("turbine::init $engines $servers \"Swift\""));
    }
    try {
      if (Settings.getBoolean(Settings.ENABLE_REFCOUNTING)) {
        tree.add(Turbine.enableReferenceCounting());
      }
      
      if (Settings.getBoolean(Settings.ENABLE_CHECKPOINTING)) {
        tree.add(Turbine.xptInit());
      }
      
      // Initialize struct types
      tree.append(structTypeDeclarations());
      
      // Insert code to check versions
      tree.add(Turbine.checkConstants());
      
      tree.append(compileTimeArgs());
  
      tree.add(new Command("turbine::start " + MAIN_FUNCTION_NAME +
                                          " " + CONSTINIT_FUNCTION_NAME));
      tree.add(new Command("turbine::finalize"));
  
      if (Settings.getBoolean(Settings.ENABLE_CHECKPOINTING)) {
        tree.add(Turbine.xptFinalize());
      }
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.getMessage());
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
  public void finalizeTree() {
    pointPop();
    assert(pointStack.isEmpty());
  }

  /**
     Generate and return Tcl from  our internal TclTree
   */
  @Override
  public String code() {
    finalizeTree();
      
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
    return sb.toString();
  }

    
  @Override
  public void declareStructType(StructType st) {
    structTypes.newType(st);
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
      for (StructField field: st.getFields()) {
        // Field name and type
        fieldInfo.add(new TclString(field.getName(), true));
        fieldInfo.addAll(dataDeclarationFullType(field.getType()));
        if (Types.isStruct(field.getType())) {
          assert(declared.contains(field.getType())) :
            field.getType() + " struct type was not initialized";
        }
      }
      
      Command decl = Turbine.declareStructType(new LiteralInt(typeId),
                          structTypeName(st), new TclList(fieldInfo));
      result.add(decl);
      declared.add(st);
    }
    return result;
  }
  
  private TypeName structTypeName(Type type) {
    assert(Types.isStruct(type) || Types.isStructLocal(type));
    // Prefix Swift name with prefix to indicate struct type
    
    StructType st = (StructType)type.getImplType();
    return new TypeName("s:" + st.getStructTypeName());
  }

  @Override
  public void declare(List<VarDecl> decls) {
    List<VarDecl> batchedFiles = new ArrayList<VarDecl>();
    List<TclList> batched = new ArrayList<TclList>();
    List<String> batchedVarNames = new ArrayList<String>();
    
    for (VarDecl decl: decls) {
      Var var = decl.var;
      Arg initReaders = decl.initReaders;
      Arg initWriters = decl.initWriters;
      Type t = var.type();
      assert(!var.mappedDecl() || Types.isMappable(t));
      if (var.storage() == Alloc.ALIAS) {
        assert(initReaders == null && initWriters == null);
        continue;
      }
      // For now, just add provenance info as a comment
      pointAdd(new Comment("Var: " + t.typeName() + " " +
                prefixVar(var.name()) + " " + var.provenance().logFormat()));
  
      if (var.storage() == Alloc.GLOBAL_CONST) {
        // If global, it should already be in TCL global scope, just need to
        // make sure that we've imported it
        pointAdd(Turbine.makeTCLGlobal(prefixVar(var)));
        continue;
      }
  
      try {
        if (!Settings.getBoolean(Settings.ENABLE_REFCOUNTING)) {
          // Have initial* set to regular amount to avoid bugs with reference counting
          initReaders = Arg.ONE;
        }
      } catch (InvalidOptionException e) {
        throw new STCRuntimeError(e.getMessage());
      }
      
      String tclVarName = prefixVar(var);
      if (Types.isFile(t)) {
        batchedFiles.add(decl);
      } else if (Types.isPrimFuture(t) || Types.isPrimUpdateable(t) ||
          Types.isArray(t) || Types.isRef(t) || Types.isBag(t) ||
          Types.isStruct(t)) {
        List<Expression> createArgs = new ArrayList<Expression>();
        createArgs.addAll(dataDeclarationFullType(t));
        if (initReaders != null)
          createArgs.add(argToExpr(initReaders));
        if (initWriters != null)
          createArgs.add(argToExpr(initWriters));
        batched.add(new TclList(createArgs));
        batchedVarNames.add(tclVarName);
      } else if (Types.isPrimValue(t) || Types.isContainerLocal(t) ||
                  Types.isStructLocal(t)) {
        assert(var.storage() == Alloc.LOCAL);
        // don't need to do anything
      } else {
        throw new STCRuntimeError("Code generation not supported for declaration " +
        		"of type: " + t.typeName());
      }
    }
    
    if (!batched.isEmpty()) {
      pointAdd(Turbine.batchDeclare(batchedVarNames, batched));

      // Log in small batches to avoid turbine log limitations
      // and overly long lines
      final int logBatch = 5;
      for (int start = 0; start < batchedVarNames.size(); start += logBatch) {
        List<Expression> logExprs = new ArrayList<Expression>();
        logExprs.add(new Token("allocated"));
        int end = Math.min(batchedVarNames.size(), start + logBatch);
        for (String tclVarName: batchedVarNames.subList(start, end)) {
          logExprs.add(new Token(" " + tclVarName + "=<"));
          logExprs.add(new Value(tclVarName));
          logExprs.add(new Token(">"));
        }
        TclString msg = new TclString(logExprs, ExprContext.VALUE_STRING);
        pointAdd(Turbine.log(msg));
      }
    }
    
    // Allocate files after so that mapped args are visible
    for (VarDecl file: batchedFiles) {
      // TODO: allocate filename/signal vars in batch and build file
      //      handle here
      allocateFile(file.var, file.initReaders);
    }
    
    for (VarDecl decl: decls) {
      // Store the name->TD in the stack
      if (decl.var.storage() == Alloc.STACK && !noStackVars()) {
        Command s = Turbine.storeInStack(decl.var.name(), prefixVar(decl.var));
        // Store the name->TD in the stack
        pointAdd(s);
      }
    }
  }

  /**
   * Return the full type required to create data by ADLB.
   * In case of simple data, just the name - e.g. "int", or "mystruct"
   * For containers, may need to have key/value/etc as separate arguments
   * @param type
   * @param createArgs
   * @return
   */
  private List<Expression> dataDeclarationFullType(Type type) {
    List<Expression> typeExprList = new ArrayList<Expression>();
    // Basic data type
    typeExprList.add(representationType(type));
    // Subscript and value type for containers only
    if (Types.isArray(type)) {
      typeExprList.add(arrayKeyType(type, true)); // key
      typeExprList.add(arrayValueType(type, true)); // value
    } else if (Types.isBag(type)) {
      typeExprList.add(bagValueType(type, true));
    }
    return typeExprList;
  }

  private TypeName adlbPrimType(PrimType pt) {
    switch (pt) {
      case INT:
      case BOOL:
      case VOID:
        return Turbine.ADLB_INT_TYPE;
      case BLOB:
        return Turbine.ADLB_BLOB_TYPE;
      case FLOAT:
        return Turbine.ADLB_FLOAT_TYPE;
      case STRING:
        return Turbine.ADLB_STRING_TYPE;
      default:
        throw new STCRuntimeError("Unknown ADLB representation for " + pt);
    }
  }
  
  /**
   * @param t
   * @return ADLB representation type
   */
  private TypeName representationType(Type t) {
    if (Types.isScalarFuture(t) || Types.isScalarUpdateable(t)) {
      return adlbPrimType(t.primType());
    } else if (Types.isRef(t)) {
      return refRepresentationType(t.memberType());
    } else if (Types.isArray(t)) {
      return Turbine.ADLB_CONTAINER_TYPE;
    } else if (Types.isBag(t)) {
      return Turbine.ADLB_MULTISET_TYPE;
    } else if (Types.isStruct(t)) {
      return structTypeName(t);
    } else if (Types.isFile(t)) {
      return Turbine.ADLB_FILE_TYPE;
    } else {
      throw new STCRuntimeError("Unknown ADLB representation type for " + t);
    }
  }

  private TypeName refRepresentationType(Type memberType) {
      if (Types.isFile(memberType)) {
        return Turbine.ADLB_FILE_REF_TYPE;
      } else {
        return Turbine.ADLB_REF_TYPE;
      }
  }

  /**
   * Representation type for value if stored into data store
   * @param t
   * @param creation
   * @return
   */
  private TypeName valRepresentationType(Type t) {
    if (Types.isScalarValue(t)) {
      return adlbPrimType(t.primType());
    } else if (Types.isArrayLocal(t)) {
      return Turbine.ADLB_CONTAINER_TYPE;
    } else if (Types.isBagLocal(t)) {
      return Turbine.ADLB_MULTISET_TYPE;
    } else if (Types.isFile(t)) {
      return Turbine.ADLB_FILE_REF_TYPE;
    } else if (Types.isScalarFuture(t) || Types.isContainer(t) ||
               Types.isRef(t)) {
      // Local handle to remote data
      return Turbine.ADLB_REF_TYPE; 
    } else if (Types.isStructLocal(t)) {
      return structTypeName(t);
    } else {
      throw new STCRuntimeError("Unknown ADLB representation type for " + t);
    }
  }

  private TypeName arrayKeyType(Typed arr, boolean creation) {
    return representationType(Types.arrayKeyType(arr));
  }
  
  private TypeName arrayValueType(Typed arrType, boolean creation) {
    return representationType(Types.containerElemType(arrType));
  }
  
  private TypeName bagValueType(Typed bagType, boolean creation) {
    return representationType(Types.containerElemType(bagType));
  }
  
  private void allocateFile(Var var, Arg initReaders) {
    Expression mappedExpr = LiteralInt.boolValue(var.mappedDecl());
    pointAdd(Turbine.allocateFile(mappedExpr, prefixVar(var),
                             argToExpr(initReaders)));
  }

  @Override
  public void decrWriters(Var var, Arg amount) {
    assert(RefCounting.trackWriteRefCount(var));
    // Close array by removing the slot we created at startup
    decrementWriters(Arrays.asList(var), argToExpr(amount));
  }
  
  @Override
  public void decrRef(Var var, Arg amount) {
    assert(RefCounting.trackReadRefCount(var));
    decrementReaders(Arrays.asList(var), argToExpr(amount));
  }
  
  @Override
  public void incrRef(Var var, Arg amount) {
    assert(RefCounting.trackReadRefCount(var));
    assert(amount.isImmediateInt());
    incrementReaders(Arrays.asList(var), argToExpr(amount));
  }
  
  @Override
  public void incrWriters(Var var, Arg amount) {
    assert(RefCounting.trackWriteRefCount(var));
    assert(amount.isImmediateInt());
    incrementWriters(Arrays.asList(var), argToExpr(amount));
  }
  
  /**
   * Set target=addressof(src)
   */
  @Override
  public void assignReference(Var target, Var src,
                     long readRefs, long writeRefs) {
    assert(Types.isRef(target.type()));
    assert(target.type().memberType().equals(src.type()));
    if (Types.isFileRef(target.type())) {
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
  public void retrieveRef(Var dst, Var src, Arg acquireRead,
                          Arg acquireWrite, Arg decr) {
    assert(Types.isRef(src.type()));
    assert(acquireRead.isIntVal());
    assert(acquireWrite.isIntVal());
    if (acquireWrite.isVar() || acquireWrite.getIntLit() > 0) {
      assert(Types.isAssignableRefTo(src.type(), dst.type(), true));
    } else {
      assert(Types.isAssignableRefTo(src.type(), dst.type()));
    }

    assert(decr.isImmediateInt());
    
    Expression acquireReadExpr = argToExpr(acquireRead);
    Expression acquireWriteExpr = argToExpr(acquireWrite);
    
    TypeName refType = refRepresentationType(dst.type());
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
    assert(Types.isScalarFuture(src.type()));
    assert(Types.retrievedType(src).assignableTo(dst.type()));
    assert(decr.isImmediateInt());
    
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
          decrRef(src, decr);
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
    assert(Types.isFile(dst.type()));
    assert(Types.isFileVal(src.type()));
    // Sanity check that we're not setting mapped file
    assert(setFilename.isImmediateBool());
    if (setFilename.isBoolVal() && setFilename.getBoolLit()) {
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
    assert(decr.isImmediateInt());
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
    assert(Types.isArrayLocal(src.type()));
    assert(Types.containerElemType(src.type()).assignableTo(
              Types.containerElemValType(target)));
    assert(Types.arrayKeyType(src.type()).assignableTo(
            Types.arrayKeyType(target.type())));
    
    pointAdd(arrayBuild(target, argToExpr(src)));
  }

  @Override
  public void retrieveArray(Var target, Var src, Arg decr) {
    assert(Types.isArray(src));
    assert(Types.isArrayLocal(target));
    assert(Types.containerElemValType(src).assignableTo(
                    Types.containerElemType(target)));

    assert(Types.arrayKeyType(src.type()).assignableTo(
            Types.arrayKeyType(target.type())));  
    assert(decr.isImmediateInt());
    
    pointAdd(Turbine.enumerateAll(prefixVar(target), varToExpr(src), true,
            argToExpr(decr)));
  }
  
  @Override
  public void assignBag(Var target, Arg src) {
    assert(Types.isBag(target));
    assert(Types.isBagLocal(src.type()));
    assert(Types.containerElemType(src.type()).assignableTo(
              Types.containerElemValType(target)));    

    TypeName elemType = representationType(Types.containerElemType(target));
    pointAdd(Turbine.multisetBuild(varToExpr(target), argToExpr(src), LiteralInt.ONE,
                                   Collections.singletonList(elemType)));
  }

  @Override
  public void retrieveBag(Var target, Var src, Arg decr) {
    assert(Types.isBag(src));
    assert(Types.isBagLocal(target));
    assert(decr.isImmediateInt());
    assert(Types.containerElemValType(src).assignableTo(
                    Types.containerElemType(target)));

    pointAdd(Turbine.enumerateAll(prefixVar(target), varToExpr(src), false,
            argToExpr(decr)));
  }
  
  @Override
  public void structInitFields(Var struct, List<List<String>> fieldPaths,
      List<Arg> fieldVals, Arg writeDecr) {
    /*
     * Implement by storing a local struct with missing fields.
     * ADLB/Turbine semantics allow us to do this: only the required
     * fields will be overwritten.
     */
    // TODO: assertions
    assert(Types.isStruct(struct));
    assert(fieldPaths.size() == fieldVals.size());
    assert(writeDecr.isImmediateInt());
    

    Dict dict = localStructDict(struct, fieldPaths, fieldVals);
    
    List<TypeName> structTypeName = Collections.singletonList(
            representationType(struct.type()));
    
    // Struct should own both refcount types
    Expression storeReadRC = LiteralInt.ONE;
    Expression storeWriteRC = LiteralInt.ONE;
    
    pointAdd(Turbine.adlbStore(varToExpr(struct),
            dict, structTypeName, argToExpr(writeDecr),
            LiteralInt.ZERO, storeReadRC, storeWriteRC));
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
    assert(Types.isStructLocal(src.type()));
    assert(StructType.sharedStruct((StructType)src.type().getImplType())
            .assignableTo(target.type()));
    
    // Must decrement any refcounts not explicitly tracked since
    // we're assigning the struct in whole
    long writeDecr = RefCounting.baseWriteRefCount(target, true, true);
    
    TypeName structType = representationType(target.type());
    pointAdd(Turbine.structSet(varToExpr(target), argToExpr(src),
                          structType, new LiteralInt(writeDecr)));
  }

  @Override
  public void retrieveStruct(Var target, Var src, Arg decr) {
    assert(Types.isStruct(src));
    assert(Types.isStructLocal(target));
    assert(decr.isImmediateInt());

    assert(StructType.sharedStruct((StructType)target.type().getImplType())
            .assignableTo(src.type()));
    
    pointAdd(Turbine.structDecrGet(prefixVar(target), varToExpr(src),
                                   argToExpr(decr)));
  }
  
  @Override
  public void assignRecursive(Var target, Arg src) {
    assert(Types.isContainer(target));
    assert(Types.isContainerLocal(src.type()));
    assert(src.type().assignableTo(
              Types.unpackedContainerType(target)));    

    List<TypeName> typeList = recursiveTypeList(target.type(), false, true,
                                                true, true, true); 
    pointAdd(Turbine.buildRec(typeList, varToExpr(target), argToExpr(src)));
  }
  
  @Override
  public void retrieveRecursive(Var target, Var src, Arg decr) {
    assert(Types.isContainer(src));
    assert(Types.isContainerLocal(target));
    assert(Types.unpackedContainerType(src).assignableTo(target.type()));

    List<TypeName> typeList =
        recursiveTypeList(src.type(), false, false, true, true, true);
    
    pointAdd(Turbine.enumerateRec(prefixVar(target), typeList,
              varToExpr(src), argToExpr(decr)));
  }

  /**
   * 
   * @param type
   * @param valueType
   * @param includeKeyTypes
   * @param includeBaseType
   * @param followRefs if false, stop at first reference type
   * @param includeRefs Include ref types followed in output
   * @return
   */
  private List<TypeName> recursiveTypeList(Type type,
        boolean valueType, boolean includeKeyTypes,
        boolean includeBaseType, boolean followRefs,
        boolean includeRefs) {
    List<TypeName> typeList = new ArrayList<TypeName>();
    Type curr = type;
    do {
      typeList.add(reprTypeHelper(valueType, curr));
      if (includeKeyTypes &&
          (Types.isArray(curr) || Types.isArrayLocal(curr))) {
        // Include key type if requested
        typeList.add(representationType(Types.arrayKeyType(
        curr)));
      }
      
      curr = Types.containerElemType(curr);
      if (followRefs && Types.isContainerRef(curr)) {
        // Strip off reference
        curr = Types.retrievedType(curr);
        if (includeRefs) {
          typeList.add(Turbine.ADLB_REF_TYPE);  
        }
      }
    } while ((Types.isContainer(curr) ||
              Types.isContainerLocal(curr)));
    
    while (followRefs && Types.isRef(curr)) {
      curr = Types.retrievedType(curr);
      if (includeRefs && includeBaseType) {
        typeList.add(Turbine.ADLB_REF_TYPE);
      }
    }

    if (includeBaseType) {
      typeList.add(reprTypeHelper(valueType, curr));
    }
    
    return typeList;
  }

  private TypeName reprTypeHelper(boolean valueType, Type type) {
    TypeName reprType;
    if (valueType) {
      reprType = valRepresentationType(type);
    } else {
      reprType = representationType(type);
    }
    return reprType;
  }

  @Override
  public void decrLocalFileRef(Var localFile) {
    assert(Types.isFileVal(localFile));
    pointAdd(Turbine.decrLocalFileRef(prefixVar(localFile)));
  }
  
  @Override
  public void getFileNameAlias(Var filename, Var file) {
    assert(Types.isString(filename.type()));
    assert(filename.storage() == Alloc.ALIAS);
    assert(Types.isFile(file.type()));
    
    SetVariable cmd = new SetVariable(prefixVar(filename),
                          Turbine.getFileName(varToExpr(file)));
    pointAdd(cmd);
  }
  
  /**
   * Copy filename from future to file
   */
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
    assert(Types.isFile(file.type()));
    assert(Types.isStringVal(filenameVal));
    pointAdd(new SetVariable(prefixVar(filenameVal),
            Turbine.getFilenameVal(varToExpr(file))));
  }
  
  @Override
  public void setFilenameVal(Var file, Arg filenameVal) {
    assert(Types.isFile(file.type()));
    assert(filenameVal.isImmediateString());
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
    FileKind fileKind = localFile.type().fileKind();
    assert(filenameVal.isImmediateString());
    assert(isMapped.isImmediateBool());
    assert(isMapped.isVar() || isMapped.getBoolLit() ||
        fileKind.supportsTmpImmediate()) : "Can't give unmapped file of kind " 
      + fileKind.typeName() + " a generated temporary file name";
    
    // Initialize refcount to 1 if unmapped, or 2 if mapped so that the file
    // isn't deleted upon the block finishing
    Sequence ifMapped = new Sequence(), ifUnmapped = new Sequence();
    ifMapped.add(new SetVariable(TCLTMP_INIT_REFCOUNT, LiteralInt.TWO));
    ifUnmapped.add(new SetVariable(TCLTMP_INIT_REFCOUNT, LiteralInt.ONE));
    
    if (isMapped.isBoolVal()) {
      if (isMapped.getBoolLit()) {
        point().append(ifMapped);  
      } else {
        point().append(ifUnmapped);
      }
    } else {
      pointAdd(new If(argToExpr(isMapped), ifMapped, ifUnmapped)); 
    }
    pointAdd(Turbine.createLocalFile(prefixVar(localFile),
             argToExpr(filenameVal), new Value(TCLTMP_INIT_REFCOUNT)));
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
  public void localOp(BuiltinOpcode op, Var out,
                                            List<Arg> in) {
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
    
    //TODO: for time being, share code with built-in function generation
    TclFunRef fn = BuiltinOps.getBuiltinOpImpl(op);
    if (fn == null) {
      List<String> impls = ForeignFunctions.findOpImpl(op);
      
      // It should be impossible for there to be no implementation for a function
      // like this
      assert(impls != null) : op;
      assert(impls.size() > 0) : op;
      
      if (impls.size() > 1) {
        Logging.getSTCLogger().warn("Multiple implementations for operation " +
            op + ": " + impls.toString());
      }
      fn = builtinSymbols.get(impls.get(0));
    }
    
    List<Var> outL = (out == null) ? 
          Arrays.<Var>asList() : Arrays.asList(out);

    builtinFunctionCall("operator: " + op.toString(), fn, 
                        in, outL, props);
  }

  @Override
  public void arrayCreateNestedFuture(Var arrayResult,
      Var array, Var ix) {
    assert(Types.isArray(array.type()));
    assert(Types.isArrayRef(arrayResult.type()));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(arrayResult.storage() != Alloc.ALIAS);
    TclTree t = Turbine.containerCreateNested(
        varToExpr(arrayResult), varToExpr(array),
        varToExpr(ix), arrayKeyType(arrayResult, true),
        arrayValueType(arrayResult, true));
    pointAdd(t);
  }

  @Override
  public void arrayRefCreateNestedFuture(Var arrayResult, Var arrayRefVar,
                                         Var ix) {
    assert(Types.isArrayRef(arrayRefVar.type()));
    assert(Types.isArrayRef(arrayResult.type()));
    assert(arrayResult.storage() != Alloc.ALIAS);
    assert(Types.isArrayKeyFuture(arrayRefVar, ix));

    TclTree t = Turbine.containerRefCreateNested(
        varToExpr(arrayResult), varToExpr(arrayRefVar), varToExpr(ix),
        arrayKeyType(arrayResult, true), arrayValueType(arrayResult, true));
    pointAdd(t);
  }


  @Override
  public void arrayCreateNestedImm(Var arrayResult, Var array, Arg ix,
        Arg callerReadRefs, Arg callerWriteRefs,
        Arg readDecr, Arg writeDecr) {
    assert(Types.isArray(array.type()));
    assert(Types.isArray(arrayResult.type()));
    assert(arrayResult.storage() == Alloc.ALIAS);
    assert(Types.isArrayKeyVal(array, ix));
    assert(callerReadRefs.isImmediateInt());
    assert(callerWriteRefs.isImmediateInt());
    assert(readDecr.isImmediateInt());
    assert(writeDecr.isImmediateInt());
    
    TclTree t = Turbine.containerCreateNestedImmIx(
        prefixVar(arrayResult), varToExpr(array), argToExpr(ix),
        arrayKeyType(arrayResult, true), arrayValueType(arrayResult, true),
        argToExpr(callerReadRefs), argToExpr(callerWriteRefs),
        argToExpr(readDecr), argToExpr(writeDecr));
    pointAdd(t);
  }

  @Override
  public void arrayRefCreateNestedImm(Var arrayResult, Var array, Arg ix) {
    assert(Types.isArrayRef(array.type()));
    assert(Types.isArrayRef(arrayResult.type()));
    assert(arrayResult.storage() != Alloc.ALIAS);
    assert(Types.isArrayKeyVal(array, ix));

    TclTree t = Turbine.containerRefCreateNestedImmIx(
        varToExpr(arrayResult), varToExpr(array), argToExpr(ix),
        arrayKeyType(arrayResult, true), arrayValueType(arrayResult, true));
    pointAdd(t);
  }

  @Override
  public void arrayCreateBag(Var bag, Var arr, Arg ix, Arg callerReadRefs,
      Arg callerWriteRefs, Arg readDecr, Arg writeDecr) {
    assert(Types.isBag(bag));
    assert(bag.storage() == Alloc.ALIAS);
    assert(Types.isArrayKeyVal(arr, ix));
    assert(Types.isElemValType(arr, bag)) : arr + " " + bag;
    assert(callerReadRefs.isImmediateInt());
    assert(callerWriteRefs.isImmediateInt());
    assert(readDecr.isImmediateInt());
    assert(writeDecr.isImmediateInt());
    
    TclTree t = Turbine.containerCreateNestedBag(
            prefixVar(bag), varToExpr(arr), argToExpr(ix),
            bagValueType(bag, true),
            argToExpr(callerReadRefs), argToExpr(callerWriteRefs),
            argToExpr(readDecr), argToExpr(writeDecr));
    pointAdd(t);
  }

  @Override
  public void builtinFunctionCall(String function,
          List<Arg> inputs, List<Var> outputs, TaskProps props) {
    assert(props != null);
    props.assertInternalTypesValid();
    logger.debug("call builtin: " + function);
    TclFunRef tclf = builtinSymbols.get(function);
    assert tclf != null : "Builtin " + function + "not found";
    ForeignFunctions.getTaskMode(function).checkSpawn(execContextStack.peek());

    builtinFunctionCall(function, tclf, inputs, outputs, props);
  }

  private void builtinFunctionCall(String function, TclFunRef tclf,
      List<Arg> inputs, List<Var> outputs, TaskProps props) {
    assert(props != null);
    props.assertInternalTypesValid();
    
    TclList iList = TclUtil.tclListOfArgs(inputs);
    TclList oList = TclUtil.tclListOfVariables(outputs);
    
    if (tclf == null) {
      //should have all builtins in symbols
      throw new STCRuntimeError("call to undefined builtin function "
          + function);
    }

    // Properties can be null
    Arg priority = props.get(TaskPropKey.PRIORITY);
    TclTarget target = TclTarget.fromArg(props.get(TaskPropKey.LOCATION));
    Expression parExpr = TclUtil.argToExpr(props.get(TaskPropKey.PARALLELISM),
                                           true);

    setPriority(priority);
    
    Token tclFunction = new Token(tclf.pkg + "::" + tclf.symbol);
    List<Expression> funcArgs = new ArrayList<Expression>();
    funcArgs.add(oList);
    funcArgs.add(iList);
    funcArgs.addAll(Turbine.ruleKeywordArgs(target, parExpr));
    Command c = new Command(tclFunction, funcArgs);
    pointAdd(c);
    
    clearPriority(priority);
  }

  @Override
  public void builtinLocalFunctionCall(String functionName,
          List<Arg> inputs, List<Var> outputs) {
    TclOpTemplate template = ForeignFunctions.getInlineTemplate(functionName);
    assert(template != null);
    
    List<TclTree> result = TclTemplateProcessor.processTemplate(
                        functionName, template, inputs, outputs);
    
    Command cmd = new Command(result.toArray(new TclTree[result.size()]));
    pointAdd(cmd);
  }

  
  
  @Override
  public void functionCall(String function,
              List<Arg> inputs, List<Var> outputs,
              List<Boolean> blocking, TaskMode mode, TaskProps props)  {
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
    if (mode == TaskMode.CONTROL || mode == TaskMode.LOCAL ||
        mode == TaskMode.LOCAL_CONTROL) {
      List<Expression> args = new ArrayList<Expression>();
      args.addAll(TclUtil.varsToExpr(outputs));
      args.addAll(TclUtil.argsToExpr(inputs));
      List<Expression> action = buildAction(swiftFuncName, args);
      
      Sequence rule = Turbine.rule(function, blockOn, action, mode,
                       execContextStack.peek(), buildRuleProps(props));
      point().append(rule);
      
    } else if (mode == TaskMode.SYNC) {
      // Calling synchronously, can't guarantee anything blocks
      assert blockOn.size() == 0 : function + ": " + blockOn;
      
      List<Expression> inVars = TclUtil.argsToExpr(inputs);
      List<Expression> outVars = TclUtil.varsToExpr(outputs);
      
      pointAdd(Turbine.callFunctionSync(
          swiftFuncName, outVars, inVars));
    } else {
      throw new STCRuntimeError("Unexpected mode: " + mode);
    }
  }

  private RuleProps buildRuleProps(TaskProps props) {
    Expression priority = TclUtil.argToExpr(
                    props.get(TaskPropKey.PRIORITY), true);
    TclTarget target = TclTarget.fromArg(props.get(TaskPropKey.LOCATION));
    Expression parallelism = TclUtil.argToExpr(
                      props.get(TaskPropKey.PARALLELISM), true);
    RuleProps ruleProps = new RuleProps(target, parallelism, priority);
    return ruleProps;
  }

  @Override
  public void runExternal(String cmd, List<Arg> args,
          List<Arg> inFiles, List<Var> outFiles, 
          Redirects<Arg> redirects,
          boolean hasSideEffects, boolean deterministic) {
    for (Arg inFile: inFiles) {
      assert(inFile.isVar());
      assert(Types.isFileVal(inFile.type()));
    }
    
    List<Expression> tclArgs = new ArrayList<Expression>(args.size());
    List<Expression> logMsg = new ArrayList<Expression>();
    logMsg.add(new Token("exec: " + cmd));
    
    for (int argNum = 0; argNum < args.size(); argNum++) {
      Arg arg = args.get(argNum);
      // Should only accept local arguments
      assert(arg.isConstant() || arg.getVar().storage() == Alloc.LOCAL);
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
    pointAdd(Turbine.exec(cmd, stdinFilename,
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
    if (Types.isContainerLocal(arg.type())) {
      // Expand list
      return new Expand(argToExpr(arg));
    } else {
      // Plain argument
      return argToExpr(arg);
    }
  }
  
  private void clearPriority(Arg priority) {
    if (priority != null) {
      pointAdd(Turbine.resetPriority());
    }
  }

  private void setPriority(Arg priority) {
    if (priority != null) {
      logger.trace("priority: " + priority);
      pointAdd(Turbine.setPriority(argToExpr(priority)));
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

  private static int[] structFieldIndices(Type type, List<String> fields) {
    // use struct type info to construct index list
    int indices[] = new int[fields.size()];
    for (int i = 0; i < fields.size(); i++) {
      assert(type instanceof StructType);
      String field = fields.get(i);
      int fieldIx = ((StructType)type).getFieldIndexByName(field);
      assert(fieldIx >= 0) : field + " " + type;
      indices[i] = fieldIx;
      // Get inner type
      type = ((StructType)type).getFields().get(fieldIx).getType();
    }
    return indices;
  }

  @Override
  public void structStore(Var struct, List<String> fields,
      Arg fieldContents) {
    assert(Types.isStruct(struct));
    assert(Types.isStructFieldVal(struct, fields, fieldContents));
    
    int[] indices = structFieldIndices(struct.type(), fields);
    
    // Work out write refcounts for field (might be > 1 if struct)
    Type fieldType;
    try {
      fieldType = Types.structFieldType(struct, fields);
    } catch (TypeMismatchException e) {
      throw new STCRuntimeError(e.getMessage());
    }
    long writeDecr = RefCounting.baseRefCount(fieldType, DefType.LOCAL_COMPILER,
                                          RefCountType.WRITERS, false, true);
    
    pointAdd(Turbine.insertStruct(varToExpr(struct),
        Turbine.structSubscript(indices), argToExpr(fieldContents),
        Collections.singletonList(valRepresentationType(fieldContents.type())),
        new LiteralInt(writeDecr)));
  }
  
  @Override
  public void structCopyIn(Var struct, List<String> fields,
                           Var fieldContents) {
    assert(Types.isStruct(struct));
    assert(Types.isStructField(struct, fields, fieldContents));
    Expression subscript = structSubscript(struct, fields);
    throw new STCRuntimeError("TODO: Not yet implemented");
  }
  
  @Override
  public void structRefCopyIn(Var structRef, List<String> fields,
                           Var fieldContents) {
    assert(Types.isStructRef(structRef));
    assert(Types.isStructField(structRef, fields, fieldContents));
    Expression subscript = structSubscript(structRef, fields);
    throw new STCRuntimeError("Not yet implemented");
  }

  @Override
  public void structRefStoreSub(Var structRef, List<String> fields,
      Arg fieldContents) {
    assert(Types.isStructRef(structRef));
    assert(Types.isStructField(structRef, fields, fieldContents));
    Expression subscript = structSubscript(structRef, fields);
    throw new STCRuntimeError("Not yet implemented");
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
    // Simple create alias as handle
    Expression aliasExpr = Turbine.structAlias(varToExpr(struct), 
                       structFieldIndices(struct.type(), fields));
    pointAdd(new SetVariable(prefixVar(alias), aliasExpr));
  }


  @Override
  public void structRetrieveSub(Var output, Var struct, List<String> fields,
      Arg readDecr) {
    assert(Types.isStruct(struct));
    assert(Types.isStructFieldVal(struct, fields, output));

    Expression subscript = structSubscript(struct, fields);
    Expression readAcquire = LiteralInt.ONE;
    
    // TODO: may want to support acquiring write in future
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
    pointAdd(Turbine.copyStructSubscript(varToExpr(output), varToExpr(struct),
              subscript, representationType(output.type())));
  }
  
  @Override
  public void structRefCopyOut(Var output, Var structRef,
                              List<String> fields) {
    assert(Types.isStructRef(structRef)) : structRef;
    assert(Types.isStructField(structRef, fields, output)) :
      structRef.name() + ":" + structRef.type() + " " 
      + fields + " " + output;
    
    Expression subscript = structSubscript(structRef, fields);
    
    pointAdd(Turbine.copyStructRefSubscript(varToExpr(output),
        varToExpr(structRef), subscript, representationType(output.type())));
  }

  @Override
  public void arrayRetrieve(Var oVar, Var arrayVar, Arg arrIx, Arg readDecr) {
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(Types.isElemValType(arrayVar, oVar));
    pointAdd(Turbine.arrayLookupImm(prefixVar(oVar), varToExpr(arrayVar),
             argToExpr(arrIx), argToExpr(readDecr)));
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
    Expression aliasExpr = Turbine.arrayAlias(varToExpr(arrayVar), 
                                              argToExpr(arrIx));
    pointAdd(new SetVariable(prefixVar(alias), aliasExpr));
  }
  
  
  @Override
  public void arrayCopyOutImm(Var oVar, Var arrayVar, Arg arrIx) {
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(Types.isArray(arrayVar)) : arrayVar;
    assert(Types.isElemType(arrayVar, oVar));
    
    Command getRef = Turbine.arrayLookupImmIx(
          varToExpr(oVar),
          arrayValueType(arrayVar.type(), false),
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
        representationType(oVar.type()),
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
          arrayValueType(arrayVar.type(), false),
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
        representationType(oVar.type()),
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
    assert(Types.isArray(array.type()));
    assert(Types.isArrayKeyVal(array, arrIx));
    assert(writersDecr.isImmediateInt());
    assert(Types.isElemValType(array, member));
    
    Command r = Turbine.arrayStoreImmediate(
        argToExpr(member), varToExpr(array),
        argToExpr(arrIx), argToExpr(writersDecr),
        arrayValueType(array, false));
    pointAdd(r);
  }

  @Override
  public void arrayStoreFuture(Var array, Var ix, Arg member,
                                Arg writersDecr) {
    assert(Types.isArray(array));
    assert(Types.isElemValType(array, member));
    assert(writersDecr.isImmediateInt());
    assert(Types.isArrayKeyFuture(array, ix));

    Command r = Turbine.arrayStoreComputed(
        argToExpr(member), varToExpr(array),
        varToExpr(ix), argToExpr(writersDecr),
        arrayValueType(array, false));

    pointAdd(r);
  }
  
  @Override
  public void arrayRefStoreImm(Var array, Arg arrIx, Arg member) {
    assert(Types.isArrayRef(array.type()));
    assert(Types.isArrayKeyVal(array, arrIx));
    assert(Types.isElemValType(array, member));
    
    Command r = Turbine.arrayRefStoreImmediate(
        argToExpr(member), varToExpr(array), argToExpr(arrIx),
        arrayValueType(array, false));
    pointAdd(r);
  }

  @Override
  public void arrayRefStoreFuture(Var array, Var ix, Arg member) {
    assert(Types.isArrayRef(array.type()));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(Types.isElemValType(array, member));
    Command r = Turbine.arrayRefStoreComputed(
        argToExpr(member), varToExpr(array),
        varToExpr(ix), arrayValueType(array, false));
  
    pointAdd(r);
  }

  @Override
  public void arrayCopyInImm(Var array, Arg arrIx, Var member, Arg writersDecr) {
    assert(Types.isArray(array.type()));
    assert(Types.isArrayKeyVal(array, arrIx));
    assert(writersDecr.isImmediateInt());
    assert(Types.isElemType(array, member));
    Command r = Turbine.arrayDerefStore(
        varToExpr(member), varToExpr(array),
        argToExpr(arrIx), argToExpr(writersDecr),
        arrayValueType(array, false));
    pointAdd(r);
  }

  @Override
  public void arrayCopyInFuture(Var array, Var ix, Var member,
                                Arg writersDecr) {
    assert(Types.isArray(array.type()));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(writersDecr.isImmediateInt());
    assert(Types.isElemType(array, member));
    
    Command r = Turbine.arrayDerefStoreComputed(
        varToExpr(member), varToExpr(array),
        varToExpr(ix), argToExpr(writersDecr),
        arrayValueType(array, false));

    pointAdd(r);
  }

  @Override
  public void arrayRefCopyInImm(Var array, Arg arrIx, Var member) {
    assert(Types.isArrayRef(array.type()));
    assert(Types.isArrayKeyVal(array, arrIx));
    assert(Types.isElemType(array, member));
    
    Command r = Turbine.arrayRefDerefStore(
        varToExpr(member), varToExpr(array),
        argToExpr(arrIx), arrayValueType(array, false));
    pointAdd(r);
  }

  @Override
  public void arrayRefCopyInFuture(Var array, Var ix, Var member) {
    assert(Types.isArrayRef(array.type()));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(Types.isElemType(array, member));

    Command r = Turbine.arrayRefDerefStoreComputed(
        varToExpr(member), varToExpr(array),
        varToExpr(ix), arrayValueType(array, false));

    pointAdd(r);
  }

  @Override
  public void arrayBuild(Var array, List<Arg> keys, List<Arg> vals) {
    assert(Types.isArray(array.type()));
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
               TaskMode.LOCAL, new TaskProps());
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
    TypeName simpleReprType = representationType(src.type());
    pointAdd(Turbine.retrieveAcquire(tmpVal.variable(), varToExpr(src),
                               simpleReprType, incrReferand, LiteralInt.ONE));
    

    // Must decrement any refcounts not explicitly tracked since
    // we're assigning the struct in whole
    long writeDecr = RefCounting.baseWriteRefCount(dst, true, true);
    
    List<TypeName> fullReprType;
    
    if (Types.isContainer(src)) {
      fullReprType = recursiveTypeList(dst.type(), false, true,
                                          true, false, false);
    } else {
      fullReprType = Collections.singletonList(
                                  representationType(src.type()));
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
    TypeName keyType = representationType(Types.arrayKeyType(array));
    Type valType2 = Types.containerElemType(array);
    TypeName valType = representationType(valType2);

    return Turbine.arrayBuild(varToExpr(array), dict, LiteralInt.ONE,
                keyType, Collections.singletonList(valType));
  }
  
  @Override
  public void bagInsert(Var bag, Arg elem, Arg writersDecr) {
    assert(Types.isElemValType(bag, elem));
    pointAdd(Turbine.bagAppend(varToExpr(bag),
          arrayValueType(bag, false), argToExpr(elem),
          argToExpr(writersDecr)));
  }
  
  @Override
  public void initUpdateable(Var updateable, Arg val) {
    assert(Types.isScalarUpdateable(updateable.type()));
    if (!updateable.type().equals(Types.UP_FLOAT)) {
      throw new STCRuntimeError(updateable.type() +
          " not yet supported");
    }
    assert(val.isImmediateFloat());
    pointAdd(Turbine.updateableFloatInit(varToExpr(updateable),
                                                      argToExpr(val)));
  }
  
  @Override
  public void latestValue(Var result, Var updateable) {
    assert(Types.isScalarUpdateable(updateable.type()));
    assert(Types.isScalarValue(result.type()));
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
  public void update(Var updateable, UpdateMode updateMode, Var val) {
    assert(Types.isScalarUpdateable(updateable.type()));
    assert(Types.isScalarFuture(val.type()));
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
  public void updateImm(Var updateable, UpdateMode updateMode,
                                                Arg val) {
    assert(Types.isScalarUpdateable(updateable.type()));
    if (updateable.type().equals(Types.UP_FLOAT)) {
      assert(val.isImmediateFloat());
    } else {
      throw new STCRuntimeError("only updateable floats are"
          + " implemented so far");
    }
    assert(updateMode != null);
    String builtinName = getUpdateBuiltin(updateMode) + "_impl";
    pointAdd(new Command(builtinName, Arrays.asList(
        (Expression)varToExpr(updateable), argToExpr(val))));
  }

  /** This prevents duplicate "package require" statements */
  private final Set<String> requiredPackages = new HashSet<String>();

  @Override
  public void requirePackage(String pkg, String version) {
    String pv = pkg + version;
    if (!pkg.equals("turbine")) {
      if (!requiredPackages.contains(pv))
      {
        PackageRequire pr = new PackageRequire(pkg, version);
        pointAdd(pr);
        requiredPackages.add(pv);
        pointAdd(new Command(""));
      }
    }
  }
  
  @Override
  public void defineBuiltinFunction(String name, FunctionType type,
            TclFunRef impl) {
    builtinSymbols.put(name, impl);
    logger.debug("TurbineGenerator: Defined built-in " + name);
  }

  @Override
  public void startFunction(String functionName,
                                     List<Var> oList,
                                     List<Var> iList,
                                     TaskMode mode)
  throws UserException
  {
    List<String> outputs = prefixVars(oList);
    List<String> inputs  = prefixVars(iList);
    // System.out.println("function" + functionName);
    boolean isMain = functionName.equals(Constants.MAIN_FUNCTION);
    String prefixedFunctionName = null;
    if (isMain)
      prefixedFunctionName = MAIN_FUNCTION_NAME;
    else
      prefixedFunctionName = TclNamer.swiftFuncName(functionName);

    List<String> args =
      new ArrayList<String>(inputs.size()+outputs.size());
    if (!isMain) {
      args.add(Turbine.LOCAL_STACK_NAME);
    }
    args.addAll(outputs);
    args.addAll(inputs);

    // This better be the bottom
    Sequence point = point();

    Sequence s = new Sequence();
    Proc proc = new Proc(prefixedFunctionName,
                         usedTclFunctionNames, args, s);

    point.add(proc);
    s.add(Turbine.turbineLog("enter function: " +
                             functionName));

    if (noStack() && isMain) {
      s.add(Turbine.createDummyStackFrame());
    }

    if (!noStack()) {
      TclTree[] setupStack;
      if (isMain) {
        setupStack = Turbine.createStackFrame(StackFrameType.MAIN);
      } else {
        setupStack = Turbine.createStackFrame(StackFrameType.FUNCTION);
      }
      s.add(setupStack);
      if (!noStackVars()) {
        for (Var v : iList)
        {
          Command command = Turbine.storeInStack(v.name(),
                                      prefixVar(v));
          s.add(command);
        }
        for (Var v : oList)
        {
          Command command = Turbine.storeInStack(v.name(),
                                            prefixVar(v));
          s.add(command);
        }
      }
    }

    pointPush(s);
  }

  @Override
  public void endFunction() {
    pointPop();
  }

  @Override
  public void startNestedBlock() {
    Sequence block = new Sequence();
    if (!noStack()) {
      TclTree[] t = Turbine.createStackFrame(StackFrameType.NESTED);
      block.add(t);
    }
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
    assert(condition.isImmediateBool() || condition.isImmediateInt());


    Sequence thenBlock = new Sequence();
    Sequence elseBlock = hasElse ? new Sequence() : null;
    if (!noStack()) {
      thenBlock.add(Turbine.createStackFrame(StackFrameType.NESTED));
      if (hasElse) {
        elseBlock.add(Turbine.createStackFrame(StackFrameType.NESTED));
      }
    }

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
        List<Var> passIn, boolean recursive, TaskMode target,
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
     * @param keepOpenVars
     * @param priority 
     * @param recursive
     */
    private void startAsync(String procName, List<Var> waitVars,
        List<Var> passIn, boolean recursive, TaskMode mode, TaskProps props) {
      props.assertInternalTypesValid();
      mode.checkSpawn(execContextStack.peek());
      for (Var v: passIn) {
        if (Types.isBlobVal(v)) {
          throw new STCRuntimeError("Can't directly pass blob value");
        }
      }
      
      List<String> args = new ArrayList<String>();
      args.add(Turbine.LOCAL_STACK_NAME);
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
        TypeName baseTypes[] = new TypeName[waitVars.size()];
        for (int i = 0; i < waitVars.size(); i++) {
          Type waitVarType = waitVars.get(i).type();
          Pair<Integer, TypeName> data = recursiveContainerType(waitVarType);
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
      
      ExecContext newExecContext;
      if (mode == TaskMode.WORKER) {
        newExecContext = ExecContext.WORKER;
      } else if (mode == TaskMode.CONTROL) {
        newExecContext = ExecContext.CONTROL;
      } else {
        // Executes on same node
        newExecContext = execContextStack.peek();
      }
      execContextStack.push(newExecContext);
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
        baseType = new NestedContainerInfo(typed.type()).baseType;
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
        for (StructField f: ((StructType)baseType.getImplType()).getFields()) {
          if (checkRecursiveWait(f.getType())) {
            requiresRecursion = true;
            throw new STCRuntimeError("Recursively waiting on structs with " +
                         " references to other datums is not yet implemented");
          }
        }
      } else {
        throw new STCRuntimeError("Recursive wait not yet supported"
            + " for type: " + typed.type().typeName());
      }
      return requiresRecursion;
    }

    /**
     * @param depths
     * @param i
     * @param type
     * @return (nesting depth, base type name)
     */
    private Pair<Integer, TypeName> recursiveContainerType(Type type) {
      Type baseType;
      int depth;
      if (Types.isContainer(type)) {
        NestedContainerInfo ai = new NestedContainerInfo(type);
        depth = ai.nesting;
        baseType = ai.baseType;
      } else if (Types.isFuture((type))) {
        depth = 0;
        // Indicate that it's a future not a value
        // TODO: does mutability matter?
        baseType = new RefType(type, false);
      } else if (Types.isPrimValue(type)) {
        depth = 0;
        baseType = type;
      } else {
        throw new STCRuntimeError("Not sure how to deep wait on type "
                                  + type);
      }
      TypeName baseReprType = representationType(baseType);
      return Pair.create(depth, baseReprType);
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
        if (Types.isFile(var.type())) {
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
      ruleTokens.add(Turbine.LOCAL_STACK_VAL);
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
    assert(switchVar.isImmediateInt());

    int casecount = caseLabels.size();
    if (hasDefault) casecount++;

    List<Sequence> caseBodies = new ArrayList<Sequence>(casecount);
    for (int c=0; c < casecount; c++) {
      Sequence casebody = new Sequence();
      // there might be new locals in the case
      if (!noStack()) {
        casebody.add(Turbine.createStackFrame(StackFrameType.NESTED));
      }
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
        MultiMap<Var, RefCount> constIncrs) {
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
      MultiMap<Var, RefCount> constIncrs, Expression containerSize) {
    if (!perIterIncrs.isEmpty()) {
      pointAdd(new SetVariable(TCLTMP_ITERS, 
                                      containerSize));
 
      handleRefcounts(constIncrs, perIterIncrs, Value.numericValue(TCLTMP_ITERS), false);
    }
  }

  private void startForeachSplit(String procName, Var arrayVar,
      String contentsVar, int splitDegree, int leafDegree, boolean haveKeys,
      List<PassedVar> usedVars, List<RefCount> perIterIncrs,
      MultiMap<Var, RefCount> constIncrs) {
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
      MultiMap<Var, RefCount> constIncrs) {
    assert(start.isImmediateInt());
    assert(end.isImmediateInt());
    assert(increment.isImmediateInt());
    assert(Types.isIntVal(loopVar));
    if (countVar != null) { 
      throw new STCRuntimeError("Backend doesn't support counter var in range " +
      		                      "loop yet");
      
    }
    Expression startE = argToExpr(start);
    Expression endE = argToExpr(end);
    Expression incrE = argToExpr(increment);


    if (!perIterIncrs.isEmpty()) {
      // Increment references by # of iterations
      pointAdd(new SetVariable(TCLTMP_ITERSTOTAL,
                       rangeItersLeft(startE, endE, incrE)));
      
      Value itersTotal = Value.numericValue(TCLTMP_ITERSTOTAL);
      handleRefcounts(constIncrs, perIterIncrs, itersTotal, false);
    }
    
    if (splitDegree > 0) {
      startRangeSplit(loopName, passedVars, perIterIncrs,
              splitDegree, leafDegree, startE, endE, incrE);
      startRangeLoopInner(loopName, loopVar,
          TCLTMP_RANGE_LO_V, TCLTMP_RANGE_HI_V, TCLTMP_RANGE_INC_V);
    } else {
      startRangeLoopInner(loopName, loopVar, startE, endE, incrE);
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

  private void startRangeLoopInner(String loopName, Var loopVar,
          Expression startE, Expression endE, Expression incrE) {
    Sequence loopBody = new Sequence();
    String loopVarName = prefixVar(loopVar);
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
   * @param keepOpenVars
   */
  private void startRangeSplit(String loopName,
          List<PassedVar> passedVars, List<RefCount> perIterIncrs, int splitDegree,
          int leafDegree, Expression startE, Expression endE,
          Expression incrE) {
    // Create two procedures that will be called: an outer procedure
    //  that recursively breaks up the foreach loop into chunks,
    //  and an inner procedure that actually runs the loop
    List<String> commonFormalArgs = new ArrayList<String>();
    commonFormalArgs.add(Turbine.LOCAL_STACK_NAME);
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
    commonArgs.add(Turbine.LOCAL_STACK_VAL);
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
                    outerRecCall, TaskMode.CONTROL, 
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
  private void handleRefcounts(MultiMap<Var, RefCount> constIncrs, List<RefCount> multipliedIncrs,
                               Value multiplier, boolean decrement) {
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
            if (constRC.amount.isIntVal() && constRC.amount.getIntLit() < 0) {
              refCountExpr.add(TclExpr.MINUS);
              refCountExpr.add(new LiteralInt(constRC.amount.getIntLit() * -1));
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
      for (List<RefCount> rcList: constIncrs.values()) {
        for (RefCount rc: rcList) {
          assert(processed.contains(Pair.create(rc.var, rc.type))) : 
            rc + "\n" + constIncrs + "\n" + multipliedIncrs;
        }
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
  public void addGlobal(String name, Arg val) {
    String tclName = prefixVar(name);
    Value tclVal = new Value(tclName);
    globInit.add(Turbine.makeTCLGlobal(tclName));
    TypeName typePrefix;
    Expression expr;
    Command setCmd;
    switch (val.getKind()) {
    case INTVAL:
      typePrefix = Turbine.ADLB_INT_TYPE;
      expr = new LiteralInt(val.getIntLit());
      setCmd = Turbine.integerSet(tclVal, expr);
      break;
    case FLOATVAL:
      typePrefix = Turbine.ADLB_FLOAT_TYPE;
      expr = new LiteralFloat(val.getFloatLit());
      setCmd = Turbine.floatSet(tclVal, expr);
      break;
    case STRINGVAL:
      typePrefix = Turbine.ADLB_STRING_TYPE;
      expr = new TclString(val.getStringLit(), true);
      setCmd = Turbine.stringSet(tclVal, expr);
      break;
    case BOOLVAL:
      typePrefix = Turbine.ADLB_INT_TYPE;
      expr = new LiteralInt(val.getBoolLit() ? 1 : 0);
      setCmd = Turbine.integerSet(tclVal, expr);
      break;
    default:
      throw new STCRuntimeError("Non-constant oparg type "
          + val.getKind());
    }
    globInit.add(Turbine.allocatePermanent(tclName, typePrefix));
    globInit.add(setCmd);
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


  private static String prefixVar(String varname) {
    return TclNamer.prefixVar(varname);
  }
  
  private static String prefixVar(Var var) {
    return TclNamer.prefixVar(var.name());
  }

  private static List<String> prefixVars(List<Var> vars) {
    return TclNamer.prefixVars(vars);
  }

  private boolean noStackVars() {
    boolean no_stack_vars;
    try {
      no_stack_vars = Settings.getBoolean(Settings.TURBINE_NO_STACK_VARS);
    } catch (InvalidOptionException e) {
      e.printStackTrace();
      throw new STCRuntimeError(e.getMessage());
    }
    return no_stack_vars;
  }

  private boolean noStack() {
    boolean no_stack;
    try {
      no_stack = Settings.getBoolean(Settings.TURBINE_NO_STACK);
    } catch (InvalidOptionException e) {
      e.printStackTrace();
      throw new STCRuntimeError(e.getMessage());
    }
    return no_stack;
  }

  @Override
  public void startLoop(String loopName, List<Var> loopVars,
      List<Boolean> definedHere, List<Arg> initVals, List<Var> usedVariables,
      List<Var> keepOpenVars, List<Var> initWaitVars,
      boolean simpleLoop) {
    assert(initWaitVars.isEmpty() || !simpleLoop) : initWaitVars;
    List<String> tclLoopVars = new ArrayList<String>(); 
    // call rule to start the loop, pass in initVals, usedVariables
    ArrayList<String> loopFnArgs = new ArrayList<String>();
    ArrayList<Expression> firstIterArgs = new ArrayList<Expression>();
    loopFnArgs.add(Turbine.LOCAL_STACK_NAME);
    firstIterArgs.add(Turbine.LOCAL_STACK_VAL);

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
      nextIterArgs.add(Turbine.LOCAL_STACK_VAL);

      for (Arg v: newVals) {
        nextIterArgs.add(argToExpr(v));
      }
      for (Var v: usedVariables) {
        nextIterArgs.add(varToExpr(v));
      }
      ArrayList<Value> blockingVals = new ArrayList<Value>();
      assert(newVals.size() == blockingVars.size());
      for (int i = 0; i < newVals.size(); i++) {
        Arg newVal = newVals.get(i);
        if (blockingVars.get(i) && newVal.isVar()) {
          blockingVals.add(varToExpr(newVal.getVar()));
        }
      }
      pointAdd(Turbine.loopRule(context.loopName,
          nextIterArgs, blockingVals, execContextStack.peek())); 
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
      AsyncExecutor executor, String cmdName, List<Var> taskOutputs,
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
      if (executor.isCommandLine()) {
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
      public int compare(Pair<String, Expression> a,
          Pair<String, Expression> b) {
        return a.val1.compareTo(b.val1);
      }
    });

    List<Expression> continuation = null;
    if (hasContinuation) {
      continuation = new ArrayList<Expression>();
      continuation.add(new Token(proc.name()));
      continuation.addAll(continuationArgVals);
    }
    
    pointAdd(Turbine.asyncExec(executor, cmdName,
                  outVarNames, taskArgExprs, taskPropExprs, continuation));

    if (hasContinuation) {
      // Enter proc body for code generation of continuation
      pointPush(proc.getBody());
    }
  }
  
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
    // Write checkpoint with binary keys
    // Want to persist data to disk.
    // Don't need to store new entries in index.
    pointAdd(Turbine.xptWrite(argToExpr(key), argToExpr(val),
                  XptPersist.PERSIST, LiteralInt.FALSE));
  }

  @Override
  public void lookupCheckpoint(Var checkpointExists, Var value, Arg key) {
    assert(Types.isBoolVal(checkpointExists));
    assert(Types.isBlobVal(key.type()));
    assert(Types.isBlobVal(value));
    
    pointAdd(Turbine.xptLookupStmt(prefixVar(checkpointExists),
            prefixVar(value), argToExpr(key)));
  }

  @Override
  public void packValues(Var packed, List<Arg> unpacked) {
    assert(Types.isBlobVal(packed));
    for (Arg u: unpacked) {
      assert(u.isConstant() || u.getVar().storage() == Alloc.LOCAL);
    }
    
    // Need to pass type names to packing routine
    List<Expression> exprs = makeTypeValList(unpacked);
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
  private List<Expression> makeTypeValList(List<Arg> vals) {
    List<Expression> result = new ArrayList<Expression>();
    for (Arg val: vals) {
      if (Types.isContainerLocal(val.type())) {
        result.addAll(recursiveTypeList(val.type(), true, true, true, true, false)); 
      } else {
        result.add(valRepresentationType(val.type()));
      }
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
      types.add(valRepresentationType(unpackedVar.type()));
    }
    pointAdd(Turbine.xptUnpack(unpackedVarNames, argToExpr(packed), types));
  }
  
  @Override
  public void unpackArrayToFlat(Var flatLocalArray, Arg inputArray) {
    // TODO: other container types?
    assert(Types.isArray(inputArray.type()));
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
    Pair<Integer, TypeName> rct = recursiveContainerType(arg.type());
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
