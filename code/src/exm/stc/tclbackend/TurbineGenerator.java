/**
 * This module handles the higher-level logic of generating Tcl code.
 * More mechanical aspects of code generation are handled in TclTurbineTree
 * or the classes in the exm.tcl.tree module
 */
package exm.stc.tclbackend;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.TclFunRef;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Builtins;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.Builtins.TclOpTemplate;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Operators.UpdateMode;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.ArrayInfo;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.PrimType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarCount;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.tclbackend.Turbine.CacheMode;
import exm.stc.tclbackend.Turbine.StackFrameType;
import exm.stc.tclbackend.tree.Command;
import exm.stc.tclbackend.tree.Comment;
import exm.stc.tclbackend.tree.DictFor;
import exm.stc.tclbackend.tree.Expand;
import exm.stc.tclbackend.tree.Expression;
import exm.stc.tclbackend.tree.ForEach;
import exm.stc.tclbackend.tree.ForLoop;
import exm.stc.tclbackend.tree.If;
import exm.stc.tclbackend.tree.LiteralFloat;
import exm.stc.tclbackend.tree.LiteralInt;
import exm.stc.tclbackend.tree.PackageRequire;
import exm.stc.tclbackend.tree.Proc;
import exm.stc.tclbackend.tree.Sequence;
import exm.stc.tclbackend.tree.SetVariable;
import exm.stc.tclbackend.tree.Square;
import exm.stc.tclbackend.tree.Switch;
import exm.stc.tclbackend.tree.TclList;
import exm.stc.tclbackend.tree.TclString;
import exm.stc.tclbackend.tree.TclTree;
import exm.stc.tclbackend.tree.Text;
import exm.stc.tclbackend.tree.Token;
import exm.stc.tclbackend.tree.Value;
import exm.stc.ui.ExitCode;

public class TurbineGenerator implements CompilerBackend
{

  /** 
     This prevents duplicate "lappend auto_path" statements
     We use a List because these should stay in order  
   */
  private final List<String> autoPaths = new ArrayList<String>();
  
  private static final String TCLTMP_SPLITLEN = "tcltmp:splitlen";
  private static final String TCLTMP_CONTAINER_SIZE = "tcltmp:container_sz";
  private static final String TCLTMP_ARRAY_CONTENTS = "tcltmp:contents";
  private static final String TCLTMP_RANGE_LO = "tcltmp:lo";
  private static final Value TCLTMP_RANGE_LO_V = new Value(TCLTMP_RANGE_LO);
  private static final String TCLTMP_RANGE_HI = "tcltmp:hi";
  private static final Value TCLTMP_RANGE_HI_V = new Value(TCLTMP_RANGE_HI);
  private static final String TCLTMP_RANGE_INC = "tcltmp:inc";
  private static final Value TCLTMP_RANGE_INC_V = new Value(TCLTMP_RANGE_INC);
  private static final String TCLTMP_ITERSLEFT = "tcltmp:itersleft";
  private static final String TCLTMP_ITERSTOTAL = "tcltmp:iterstotal";
  private static final String TCLTMP_REF_DECR = "tcltmp:ref_decr";
  
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
     Stack for previous values of point
   */
  Deque<Sequence> pointStack = new ArrayDeque<Sequence>();
  
  /**
   * Stack for name of loop functions
   */
  Deque<String> loopNameStack = new ArrayDeque<String>();
  
  private static enum ExecContext {
    CONTROL, LEAF
  }
  /**
   * Stack for what context we're in. 
   */
  Deque<ExecContext> execContextStack = new ArrayDeque<ExecContext>();

  String turbineVersion = Settings.get(Settings.TURBINE_VERSION);

  HashSet<String> usedTclFunctionNames = new HashSet<String>();

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
    pointStack.push(tree);
    
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
    tree.add(new Comment("Generated by stc"));
    tree.add(new Comment("date                    : " + timestamp));
    tree.add(new Comment("Turbine version         : " + this.turbineVersion));
    tree.add(new Comment("Input filename          : " + input_file.getAbsolutePath() ));    
    tree.add(new Comment("Output filename         : " + output_file.getAbsolutePath() ));
    tree.add(new Comment("STC home                : " + Settings.get(Settings.STC_HOME)) );
    tree.add(new Comment("Turbine home            : " + Settings.get(Settings.TURBINE_HOME)) );
    tree.add(new Comment("Compiler settings:"));
    tree.add(new Comment("OPT_BRANCH_PREDICT      : " + Settings.get(Settings.OPT_BRANCH_PREDICT) ));    
    tree.add(new Comment("OPT_CONSTANT_FOLD       : " + Settings.get(Settings.OPT_CONSTANT_FOLD) ));
    tree.add(new Comment("OPT_CONTROLFLOW_FUSION  : " + Settings.get(Settings.OPT_CONTROLFLOW_FUSION) ));
    tree.add(new Comment("OPT_DEAD_CODE_ELIM      : " + Settings.get(Settings.OPT_DEAD_CODE_ELIM) ));
    tree.add(new Comment("OPT_DISABLE_ASSERTS     : " + Settings.get(Settings.OPT_DISABLE_ASSERTS) ));
    tree.add(new Comment("OPT_FLATTEN_NESTED      : " + Settings.get(Settings.OPT_FLATTEN_NESTED) ));
    tree.add(new Comment("OPT_FORWARD_DATAFLOW    : " + Settings.get(Settings.OPT_FORWARD_DATAFLOW) ));
    tree.add(new Comment("OPT_HOIST               : " + Settings.get(Settings.OPT_HOIST) ));
    tree.add(new Comment("OPT_NUM_PASSES          : " + Settings.get(Settings.OPT_NUM_PASSES) ));
    tree.add(new Comment("OPT_SHARED_CONSTANTS    : " + Settings.get(Settings.OPT_SHARED_CONSTANTS) ));
    tree.add(new Comment("OPT_UNROLL_LOOPS        : " + Settings.get(Settings.OPT_UNROLL_LOOPS) ));
    tree.add(new Comment("OPT_WAIT_COALESCE       : " + Settings.get(Settings.OPT_WAIT_COALESCE) ));    
    tree.add(new Comment("ARRAY_REF_SWITCHEROO    : " + Settings.get(Settings.ARRAY_REF_SWITCHEROO) ));
    tree.add(new Comment("EXPERIMENTAL_REFCOUNTING: " + Settings.get(Settings.EXPERIMENTAL_REFCOUNTING) ));    
    tree.add(new Comment("DEBUG_LEVEL             : " + Settings.get(Settings.DEBUG_LEVEL) ));
    tree.add(new Text(""));    

    tree.add(new Command("package require turbine", turbineVersion));
    tree.add(new Command("namespace import turbine::*"));
    tree.add(new Text(""));

    addAutoPaths();
    
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
    tree.add(new Command("turbine::init $engines $servers"));
    try {
      if (Settings.getBoolean(Settings.EXPERIMENTAL_REFCOUNTING)) {
        pointStack.peek().add(Turbine.enableReferenceCounting());
      }
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.getMessage());
    }

    tree.add(new Command("turbine::start " + MAIN_FUNCTION_NAME +
                                        " " + CONSTINIT_FUNCTION_NAME));
    tree.add(new Command("turbine::finalize"));
  }

  @Override
  public void declare(Type t, String name, VarStorage storage,
        DefType defType, Var mapping)
  throws UndefinedTypeException
  {
    assert(mapping == null || Types.isMappable(t));
    String tclName = prefixVar(name);
    Sequence point = pointStack.peek();

    if (storage == VarStorage.ALIAS) {
      point.add(new Comment("Alias " + name + " with type " + t.toString() +
          " was defined"));
      return;
    }

    if (storage == VarStorage.GLOBAL_CONST) {
      // If global, it should already be in TCL global scope, just need to
      // make sure that we've imported it
      point.add(Turbine.makeTCLGlobal(tclName));
      return;
    }


    // Initialize the TD in ADLB with a type
    if (Types.isScalarFuture(t) || Types.isScalarUpdateable(t)) {
      if (Types.isFile(t)) {
        Value mapExpr = (mapping == null) ? null : varToExpr(mapping);
        point.add(Turbine.allocateFile(mapExpr, tclName));
      } else {
        PrimType pt = t.primType();
        String tprefix = typeToString(pt);
        point.add(Turbine.allocate(tclName, tprefix,
                          Types.isScalarUpdateable(t)));
      }
    } else if (Types.isArray(t)) {
      point.add(Turbine.allocateContainer(tclName, Turbine.INTEGER_TYPENAME));
    } else if (Types.isRef(t)) {
      if (refIsString(t)) {
        // Represent some reference types as strings, since they have multiple
        // elements in the handle
        point.add(Turbine.allocate(tclName, Turbine.STRING_TYPENAME, false)); 
      } else {
        point.add(Turbine.allocate(tclName, Turbine.INTEGER_TYPENAME, false));
      }
    } else if (Types.isStruct(t)) {
      point.add(Turbine.allocateStruct(tclName));
    } else if (Types.isScalarValue(t)) {
      if (storage != VarStorage.LOCAL) {
        throw new STCRuntimeError("Expected scalar value to have "
            + "local storage");
      }
      point.add(new Comment("Value " + name + " with type " + t.toString() +
          " was defined"));
      // don't need to do anything
    } else {
      throw new STCRuntimeError("Code generation only implemented" +
          " for initialisation of scalar, reference, array and struct types");
    }


    // Store the name->TD in the stack

      if (storage == VarStorage.STACK && !noStackVars()) {
        Command s = Turbine.storeInStack(name, tclName);
        // Store the name->TD in the stack
        point.add(s);
      }

  }


  @Override
  public void decrWriters(Var arr) {
    Type type = arr.type();
    assert(Types.isArray(type));
    // Close array by removing the slot we created at startup
    pointStack.peek().add(Turbine.decrArrayWriters(varToExpr(arr)));
  }
  
  @Override
  public void decrRef(Var var) {
    decrementReaders(var);
  }

  String typeToString(PrimType type)
  throws UndefinedTypeException
  {
    switch(type) {
    case INT:
      return Turbine.INTEGER_TYPENAME;
    case STRING:
      return Turbine.STRING_TYPENAME;
    case FLOAT:
      return Turbine.FLOAT_TYPENAME;
    case BOOL:
      return Turbine.INTEGER_TYPENAME;
    case VOID:
      return Turbine.VOID_TYPENAME;
    case BLOB:
      return Turbine.BLOB_TYPENAME;
    default:
      // If we did not find the type, fail
      throw new STCRuntimeError("generator: unknown type: " + type);
    }
  }

  /**
   * Set target=addressof(src)
   */
  @Override
  public void assignReference(Var target, Var src) {
    assert(Types.isRef(target.type()));
    assert(target.type().memberType().equals(src.type()));
    if (refIsString(target.type())) {
      pointStack.peek().add(Turbine.stringSet(
          varToExpr(target), varToExpr(src)));
    } else {
      pointStack.peek().add(Turbine.integerSet(
          varToExpr(target), varToExpr(src)));
    }
  }


  @Override
  public void makeAlias(Var dst, Var src) {
    assert(src.type().equals(dst.type()));
    assert(dst.storage() == VarStorage.ALIAS);
    pointStack.peek().add(new SetVariable(prefixVar(dst.name()),
        varToExpr(src)));
  }

  @Override
  public void assignInt(Var target, Arg src) {
    assert(src.isImmediateInt());
    if (!Types.isInt(target.type())) {
      throw new STCRuntimeError("Expected variable to be int, "
          + " but was " + target.type().toString());
    }

    pointStack.peek().add(Turbine.integerSet(
        varToExpr(target), argToExpr(src)));
  }

  @Override
  public void retrieveInt(Var target, Var source) {
    assert(target.type().equals(Types.V_INT));
    assert(Types.isInt(source.type()));
    pointStack.peek().add(Turbine.integerGet(prefixVar(target.name()),
                                                varToExpr(source)));
  }



  @Override
  public void assignBool(Var target, Arg src) {
    assert(src.isImmediateBool());
    if (!Types.isBool(target.type())) {
      throw new STCRuntimeError("Expected variable to be bool, "
          + " but was " + target.type().toString());
    }

    pointStack.peek().add(Turbine.integerSet(
        varToExpr(target), argToExpr(src)));
  }

  @Override
  public void retrieveBool(Var target, Var source) {
    assert(target.type().equals(Types.V_BOOL));
    assert(Types.isBool(source.type()));
    pointStack.peek().add(Turbine.integerGet(prefixVar(target.name()),
        varToExpr(source)));
  }
  
  @Override
  public void assignVoid(Var target, Arg src) {
    assert(Types.isVoid(target.type()));
    assert(src.getType().equals(Types.V_VOID));
    pointStack.peek().add(Turbine.voidSet(varToExpr(target)));
  }

  @Override
  public void retrieveVoid(Var target, Var source) {
    assert(target.type().equals(Types.V_VOID));
    assert(Types.isVoid(source.type()));
    // Don't actually need to retrieve value as it has no contents
    pointStack.peek().add(new SetVariable(prefixVar(target.name()),
                          Turbine.VOID_DUMMY_VAL));
  }


  @Override
  public void assignFloat(Var target, Arg src) {
    assert(src.isImmediateFloat());
    if (!Types.isFloat(target.type())) {
      throw new STCRuntimeError("Expected variable to be float, "
          + " but was " + target.type().toString());
    }

    pointStack.peek().add(Turbine.floatSet(
          varToExpr(target), argToExpr(src)));
  }

  @Override
  public void retrieveFloat(Var target, Var source) {
    assert(target.type().equals(Types.V_FLOAT));
    assert(source.type().equals(Types.F_FLOAT)
            || source.type().equals(Types.UP_FLOAT));
    pointStack.peek().add(Turbine.floatGet(prefixVar(target.name()),
                                                  varToExpr(source)));
  }

  @Override
  public void assignString(Var target, Arg src) {
    assert(src.isImmediateString());
    if (!Types.isString(target.type())) {
      throw new STCRuntimeError("Expected variable to be string, "
          + " but was " + target.type().toString());
    }

    pointStack.peek().add(Turbine.stringSet(
        varToExpr(target), argToExpr(src)));
  }

  @Override
  public void retrieveString(Var target, Var source) {
    assert(target.type().equals(Types.V_STRING));
    assert(source.type().equals(Types.F_STRING));
    pointStack.peek().add(Turbine.stringGet(prefixVar(target.name()),
                                                    varToExpr(source)));
  }

  @Override
  public void assignBlob(Var target, Arg src) {
    assert(Types.isBlob(target.type()));
    assert(src.isImmediateBlob());
    pointStack.peek().add(Turbine.blobSet(varToExpr(target),
                                          argToExpr(src)));
  }

  @Override
  public void retrieveBlob(Var target, Var src) {
    assert(target.type().equals(Types.V_BLOB));
    assert(Types.isBlob(src.type()));
    pointStack.peek().add(Turbine.blobGet(prefixVar(target.name()),
                                                varToExpr(src)));
  }

  @Override
  public void decrBlobRef(Var blob) {
    assert(Types.isBlob(blob.type()));
    pointStack.peek().add(Turbine.freeBlob(varToExpr(blob)));
  }

  @Override
  public void freeBlob(Var blobVal) {
    assert(blobVal.type().equals(Types.V_BLOB));
    pointStack.peek().add(Turbine.freeLocalBlob(varToExpr(blobVal)));
  }
  
  @Override
  public void getFileName(Var filename, Var file,
                          boolean initUnmapped) {
    assert(Types.isString(filename.type()));
    assert(filename.storage() == VarStorage.ALIAS);
    assert(Types.isFile(file.type()));
    
    SetVariable cmd = new SetVariable(prefixVar(filename.name()),
                  Turbine.getFileName(varToExpr(file), initUnmapped));
    pointStack.peek().add(cmd);
  }

  @Override
  public void localOp(BuiltinOpcode op, Var out,
                                            List<Arg> in) {
    ArrayList<Expression> argExpr = new ArrayList<Expression>(in.size());
    for (Arg a: in) {
      argExpr.add(argToExpr(a));
    }

    pointStack.peek().add(BuiltinOps.genLocalOpTcl(op, out, in, argExpr));
  }
  
  @Override
  public void asyncOp(BuiltinOpcode op, Var out, List<Arg> in,
      Arg priority) {
    //TODO: for time being, share code with built-in function generation
    TclFunRef fn = BuiltinOps.getBuiltinOpImpl(op);
    if (fn == null) {
      List<String> impls = Builtins.findOpImpl(op);
      
      // It should be impossible for there to be no implementation for a function
      // like this
      assert(impls != null);
      assert(impls.size() > 0);
      
      if (impls.size() > 1) {
        Logging.getSTCLogger().warn("Multiple implementations for operation " +
            op + ": " + impls.toString());
      }
      fn = builtinSymbols.get(impls.get(0));
    }
    
    ArrayList<Var> inputs = new ArrayList<Var>();
    for (Arg a: in) {
      // Arguments to async ops need to be vars
      assert(a.isVar());
      inputs.add(a.getVar());
    }
    
    List<Var> outL = (out == null) ? 
          Arrays.<Var>asList() : Arrays.asList(out);

    // Increment refcount so function owns ref
    pointStack.peek().add(incrementReaders(inputs, null));

    builtinFunctionCall("operator: " + op.toString(), fn, 
                        inputs, outL, priority);
  }

  @Override
  public void dereferenceInt(Var target, Var src) {
    assert(Types.isInt(target.type()));
    assert(src.type().equals(Types.R_INT));
    incrementReaders(src);
    Command deref = Turbine.dereferenceInteger(varToExpr(target),
                                               varToExpr(src));
    pointStack.peek().add(deref);
  }

  @Override
  public void dereferenceBool(Var target, Var src) {
    assert(Types.isBool(target.type()));
    assert(src.type().equals(Types.R_BOOL));
    incrementReaders(src);
    Command deref = Turbine.dereferenceInteger(varToExpr(target),
                                               varToExpr(src));
    pointStack.peek().add(deref);
  }

  @Override
  public void dereferenceFloat(Var target, Var src) {
    assert(target.type().equals(Types.F_FLOAT));
    assert(src.type().equals(Types.R_FLOAT));
    incrementReaders(src);
    Command deref = Turbine.dereferenceFloat(varToExpr(target),
                                             varToExpr(src));
    pointStack.peek().add(deref);
  }

  @Override
  public void dereferenceString(Var target, Var src) {
    assert(target.type().equals(Types.F_STRING));
    assert(src.type().equals(Types.R_STRING));
    incrementReaders(src);
    Command deref = Turbine.dereferenceString(varToExpr(target), 
                                              varToExpr(src));
    pointStack.peek().add(deref);
  }

  @Override
  public void dereferenceBlob(Var target, Var src) {
    assert(Types.isBlob(target.type()));
    assert(src.type().equals(Types.R_BLOB));
    incrementReaders(src);
    Command deref = Turbine.dereferenceBlob(varToExpr(target), varToExpr(src));
    pointStack.peek().add(deref);
  }
  
  @Override
  public void dereferenceFile(Var target, Var src) {
    assert(Types.isFile(target.type()));
    assert(src.type().equals(Types.REF_FILE));
    incrementReaders(src);
    Command deref = Turbine.dereferenceFile(varToExpr(target),
                                            varToExpr(src));
    pointStack.peek().add(deref);
  }

  @Override
  public void retrieveRef(Var target, Var src) {
    assert(Types.isRef(src.type()));
    assert(Types.isRefTo(src.type(), target.type()));
    TclTree deref;
    if (refIsString(src.type())) {
      deref = Turbine.stringGet(prefixVar(target.name()),
          varToExpr(src));      
    } else {
      deref = Turbine.integerGet(prefixVar(target.name()),
                                                   varToExpr(src));
    }
    pointStack.peek().add(deref);
  }

  @Override
  public void arrayCreateNestedFuture(Var arrayResult,
      Var arrayVar, Var indexVar) {
    assert(Types.isArray(arrayVar.type()));
    assert(Types.isArrayRef(arrayResult.type()));
    assert(arrayResult.storage() == VarStorage.ALIAS);
    incrementReaders(arrayVar, indexVar);
    TclTree t = Turbine.containerCreateNested(
        prefixVar(arrayResult.name()), prefixVar(arrayVar.name()),
        prefixVar(indexVar.name()));
    pointStack.peek().add(t);
  }

  @Override
  public void arrayRefCreateNestedFuture(Var arrayResult,
      Var arrayRefVar, Var indexVar) {
    assert(Types.isArrayRef(arrayRefVar.type()));
    assert(Types.isArrayRef(arrayResult.type()));
    assert(arrayResult.storage() == VarStorage.ALIAS);
    incrementReaders(arrayRefVar, indexVar);

    TclTree t = Turbine.containerRefCreateNested(
        prefixVar(arrayResult.name()), prefixVar(arrayRefVar.name()),
        prefixVar(indexVar.name()));
    pointStack.peek().add(t);
  }


  @Override
  public void arrayCreateNestedImm(Var arrayResult,
      Var arrayVar, Arg arrIx) {
    assert(Types.isArray(arrayVar.type()));
    assert(Types.isArray(arrayResult.type()));
    assert(arrayResult.storage() == VarStorage.ALIAS);
    assert(arrIx.isImmediateInt());

    TclTree t = Turbine.containerCreateNestedImmIx(
        prefixVar(arrayResult.name()), prefixVar(arrayVar.name()),
        argToExpr(arrIx));
    pointStack.peek().add(t);
  }

  @Override
  public void arrayRefCreateNestedImm(Var arrayResult,
      Var arrayVar, Arg arrIx) {
    assert(Types.isArrayRef(arrayVar.type()));
    assert(Types.isArrayRef(arrayResult.type()));
    assert(arrayResult.storage() == VarStorage.ALIAS);
    assert(arrIx.isImmediateInt());

    TclTree t = Turbine.containerRefCreateNestedImmIx(
        prefixVar(arrayResult.name()), prefixVar(arrayVar.name()),
        argToExpr(arrIx));
    pointStack.peek().add(t);
  }

  @Override
  public void builtinFunctionCall(String function,
          List<Var> inputs, List<Var> outputs, Arg priority)
  {
    assert(priority == null || priority.isImmediateInt());
    logger.debug("call builtin: " + function);
    TclFunRef tclf = builtinSymbols.get(function);

    // Increment references so that function owns ref
    pointStack.peek().add(incrementReaders(inputs, null));

    builtinFunctionCall(function, tclf, inputs, outputs, priority);
  }

  private void builtinFunctionCall(String function, TclFunRef tclf,
      List<Var> inputs, List<Var> outputs, Arg priority) {
    TclList iList = TclUtil.tclListOfVariables(inputs);
    TclList oList = TclUtil.tclListOfVariables(outputs);
    
    if (tclf == null) {
      //should have all builtins in symbols
      throw new STCRuntimeError("call to undefined builtin function "
          + function);
    }
    Token f = new Token(tclf.pkg + "::" + tclf.symbol);
    Value s = new Value(Turbine.LOCAL_STACK_NAME);
    Command c = new Command(f, s, oList, iList);

    setPriority(priority);
    pointStack.peek().add(c);
    clearPriority(priority);
  }

  @Override
  public void builtinLocalFunctionCall(String functionName,
          List<Arg> inputs, List<Var> outputs) {
    TclOpTemplate template = Builtins.getInlineTemplate(functionName);
    assert(template != null);
    
    List<TclTree> result = TclTemplateProcessor.processTemplate(template,
                                                        inputs, outputs);
    
    Command cmd = new Command(result.toArray(new TclTree[result.size()]));
    pointStack.peek().add(cmd);
  }

  
  
  @Override
  public void functionCall(String function,
              List<Var> inputs, List<Var> outputs,
              List<Boolean> blocking, TaskMode mode, Arg priority)  {
    assert(priority == null || priority.isImmediateInt());
    logger.debug("call: " + function);
    TclList iList = TclUtil.tclListOfVariables(inputs);
    TclList oList = TclUtil.tclListOfVariables(outputs);
    ArrayList<Var> blockOn = new ArrayList<Var>();
    HashSet<String> alreadyBlocking = new HashSet<String>();
    for (int i = 0; i < inputs.size(); i++) {
      Var v = inputs.get(i);
      if (blocking.get(i) && !alreadyBlocking.contains(v.name())) {
        blockOn.add(v);
        alreadyBlocking.add(v.name());
      }
    }

    setPriority(priority);
    if (mode == TaskMode.CONTROL || mode == TaskMode.LOCAL) {
      // Increment reference counts to keep open
      pointStack.peek().append(incrementReaders(outputs, null));
      pointStack.peek().append(incrementReaders(inputs, null));
      // TODO: should handle local separately - this will put local tasks
      //      into load balancer
      pointStack.peek().add(Turbine.callFunction(
                            TclNamer.swiftFuncName(function),
                            oList, iList, TclUtil.tclListOfVariables(blockOn)));
    } else if (mode == TaskMode.SYNC) {
      // Calling synchronously, can't guarantee anything blocks
      assert(blocking.size() == 0);
      pointStack.peek().add(Turbine.callFunctionSync(
          TclNamer.swiftFuncName(function),
          oList, iList));
    } else {
      throw new STCRuntimeError("Unexpected mode: " + mode);
    }
    clearPriority(priority);
  }

  @Override
  public void runExternal(String cmd, List<Arg> args,
          List<Var> outFiles, Redirects<Arg> redirects,
          boolean hasSideEffects, boolean deterministic) {
    List<Expression> tclArgs = new ArrayList<Expression>(args.size());
    List<Expression> logMsg = new ArrayList<Expression>();
    logMsg.add(new Token("exec: " + cmd));
    
    for (Arg arg: args) {
      Expression argExpr;
      if (Types.isArray(arg.getType())) {
        // Special case: expand arrays to list
        ArrayInfo ai = new ArrayInfo(arg.getType());
        argExpr = new Expand(Turbine.unpackArray(argToExpr(arg),
                        ai.nesting, Types.isFile(ai.baseType)));
      } else {
        argExpr = argToExpr(arg);
      }
      tclArgs.add(argExpr);
      logMsg.add(argExpr);
    }
    
    Expression stdinFilename = TclUtil.argToExpr(redirects.stdin, true);
    Expression stdoutFilename = TclUtil.argToExpr(redirects.stdout, true);
    Expression stderrFilename = TclUtil.argToExpr(redirects.stderr, true);
    
    pointStack.peek().add(Turbine.turbineLog(logMsg));
    pointStack.peek().add(Turbine.exec(cmd, stdinFilename,
                stdoutFilename, stderrFilename, tclArgs));
        
    // Close outputs
    for (Var o: outFiles) {
      if (Types.isFile(o.type())) {
        pointStack.peek().add(Turbine.closeFile(varToExpr(o)));
      } else if (Types.isVoid(o.type())) {
        pointStack.peek().add(Turbine.voidSet(varToExpr(o)));
      } else {
        throw new STCRuntimeError("Invalid app output type: " + o);
      }
    }
  }

  private void clearPriority(Arg priority) {
    if (priority != null) {
      pointStack.peek().add(Turbine.resetPriority());
    }
  }

  private void setPriority(Arg priority) {
    if (priority != null) {
      logger.trace("priority: " + priority);
      pointStack.peek().add(Turbine.setPriority(argToExpr(priority)));
    }
  }

  @Override
  public void structInsert(Var structVar, String fieldName,
      Var fieldContents) {
    pointStack.peek().add(
        Turbine.structInsert(prefixVar(structVar.name()),
            fieldName, prefixVar(fieldContents.name())));
  }

  /**
   * Called once all fields have been added to struct
   * @param struct
   */
  @Override
  public void structClose(Var struct) {
    // Now we're using local dicts for struct, this is a noop
  }

  /**
   * load the turbine id of the field into alias
   * @param structVar
   * @param structField
   * @param alias
   */
  @Override
  public void structLookup(Var structVar, String structField,
        Var alias) {
    pointStack.peek().add(
        Turbine.structLookupFieldID(prefixVar(structVar.name()),
            structField, prefixVar(alias.name())));
  }

  @Override
  public void structRefLookup(Var structVar, String structField,
        Var alias) {
    String refReprType; 
    if (refIsString(alias.type())) {
      refReprType = Turbine.STRING_TYPENAME;
    } else {
      refReprType = Turbine.INTEGER_TYPENAME;
    }
    incrementReaders(structVar);
    pointStack.peek().add(
        Turbine.structRefLookupFieldID(prefixVar(structVar.name()),
            structField, prefixVar(alias.name()),
            refReprType));
  }


  @Override
  public void arrayLookupFuture(Var oVar, Var arrayVar, Var indexVar,
        boolean isArrayRef) {
    arrayLoadCheckTypes(oVar, arrayVar, isArrayRef);
    assert(Types.isInt(indexVar.type()));
    assert(Types.isRef(oVar.type()));
    incrementReaders(arrayVar, indexVar);
    // Nested arrays - oVar should be a reference type
    Command getRef = Turbine.arrayLookupComputed(
        prefixVar(oVar.name()), refIsString(oVar.type()),
        prefixVar(arrayVar.name()), prefixVar(indexVar.name()), isArrayRef);
    pointStack.peek().add(getRef);
  }

  @Override
  public void arrayLookupRefImm(Var oVar, Var arrayVar, Arg arrIx,
        boolean isArrayRef) {
    assert(arrIx.isImmediateInt());
    if (isArrayRef) {
      incrementReaders(arrayVar);
    }
    arrayLoadCheckTypes(oVar, arrayVar, isArrayRef);
    Command getRef = Turbine.arrayLookupImmIx(
          prefixVar(oVar.name()),
          refIsString(oVar.type()),
          prefixVar(arrayVar.name()),
          argToExpr(arrIx), isArrayRef);

    pointStack.peek().add(getRef);
  }

  @Override
  public void arrayLookupImm(Var oVar, Var arrayVar,
                                                      Arg arrIx) {
    assert(arrIx.isImmediateInt());
    assert(oVar.type().equals(
                      Types.getArrayMemberType(arrayVar.type())));
     pointStack.peek().add(Turbine.arrayLookupImm(
         prefixVar(oVar.name()),
         prefixVar(arrayVar.name()),
         argToExpr(arrIx)));
  }

  /**
   * Make sure that types are valid for array load invocation
   * @param oVar The variable the result of the array should go into
   * @param arrayVar
   * @param isReference
   * @return the member type of the array
   */
  private Type arrayLoadCheckTypes(Var oVar, Var arrayVar,
      boolean isReference) {
    Type memberType;
    // Check that the types of the array variable are correct
    if (isReference) {
      assert(Types.isArrayRef(arrayVar.type()));
      Type arrayType = arrayVar.type().memberType();
      assert(Types.isArray(arrayType));
      memberType = arrayType.memberType();
    } else {
      assert(Types.isArray(arrayVar.type()));
      memberType = arrayVar.type().memberType();
    }


    Type oType = oVar.type();
    if (!Types.isRef(oType)) {
      throw new STCRuntimeError("Output variable for " +
          "array lookup should be a reference " +
          " but had type " + oType.toString());
    }
    if (!oType.memberType().equals(memberType)) {
      throw new STCRuntimeError("Output variable for "
          +" array lookup should be reference to "
          + memberType.toString() + ", but was reference to"
          + oType.memberType().toString());
    }

    return memberType;
  }

  @Override
  public void arrayInsertFuture(Var iVar, Var arrayVar,
                                                      Var indexVar) {
    assert(Types.isArray(arrayVar.type()));
    // Increment reference for var being inserted into container
    incrementReaders(iVar, arrayVar, indexVar);
    Type memberType = arrayVar.type().memberType();
    if (Types.isRef(iVar.type())) {
      assert(iVar.type().memberType().equals(memberType));
      Command r = Turbine.arrayDerefStoreComputed(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          prefixVar(indexVar.name()));

      pointStack.peek().add(r);
    } else {
      assert(iVar.type().equals(memberType));
      Command r = Turbine.arrayStoreComputed(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          prefixVar(indexVar.name()));

      pointStack.peek().add(r);
    }
  }

  @Override
  public void arrayRefInsertFuture(Var iVar, Var arrayVar,
                                Var indexVar, Var outerArrayVar) {
    assert(Types.isArrayRef(arrayVar.type()));
    assert(Types.isArray(outerArrayVar.type()));
    assert(Types.isInt(indexVar.type()));
    incrementReaders(iVar, arrayVar, indexVar, outerArrayVar);
    Type memberType = arrayVar.type().memberType().memberType();
    if (Types.isRef(iVar.type())) {
      assert(iVar.type().memberType().equals(memberType));
      Command r = Turbine.arrayRefDerefStoreComputed(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          prefixVar(indexVar.name()), prefixVar(outerArrayVar.name()));

      pointStack.peek().add(r);
    } else {
      assert(iVar.type().equals(memberType));
      Command r = Turbine.arrayRefStoreComputed(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          prefixVar(indexVar.name()), prefixVar(outerArrayVar.name()));

      pointStack.peek().add(r);
    }
  }


  @Override
  public void arrayInsertImm(Var iVar, Var arrayVar,
        Arg arrIx) {
    assert(Types.isArray(arrayVar.type()));
    if (!arrIx.isImmediateInt()) {
      throw new STCRuntimeError("Not immediate int: " + arrIx);
    }
    assert(arrIx.isImmediateInt());

    // Reference for var in array.  Don't need to increment refs for other vars since
    // they aren't held
    incrementReaders(iVar);

    Type memberType = arrayVar.type().memberType();
    if (Types.isRef(iVar.type())) {
      // Check that we get the right thing when we dereference it
      if (!iVar.type().memberType().equals(memberType)) {
        throw new STCRuntimeError("Type mismatch when trying to store " +
            "from variable " + iVar.toString() + " into array " + arrayVar.toString());
      }
      Command r = Turbine.arrayDerefStore(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          argToExpr(arrIx));
      pointStack.peek().add(r);
    } else {
      if (!iVar.type().equals(memberType)) {
        throw new STCRuntimeError("Type mismatch when trying to store " +
            "from variable " + iVar.toString() + " into array " + arrayVar.toString());
      }
      Command r = Turbine.arrayStoreImmediate(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          argToExpr(arrIx));
      pointStack.peek().add(r);
    }
  }

  @Override
  public void arrayRefInsertImm(Var iVar, Var arrayVar,
        Arg arrIx, Var outerArrayVar) {
    assert(Types.isArrayRef(arrayVar.type()));
    assert(Types.isArray(outerArrayVar.type()));
    assert(arrIx.isImmediateInt());
    incrementReaders(iVar, arrayVar, outerArrayVar);
    Type memberType = arrayVar.type().memberType().memberType();
    if (Types.isRef(iVar.type())) {
      // Check that we get the right thing when we dereference it
      if (!iVar.type().memberType().equals(memberType)) {
        throw new STCRuntimeError("Type mismatch when trying to store " +
            "from variable " + iVar.toString() + " into array " + arrayVar.toString());
      }
      Command r = Turbine.arrayRefDerefStore(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          argToExpr(arrIx), prefixVar(outerArrayVar.name()));
      pointStack.peek().add(r);
    } else {
      if (!iVar.type().equals(memberType)) {
        throw new STCRuntimeError("Type mismatch when trying to store " +
            "from variable " + iVar.toString() + " into array " + arrayVar.toString());
      }
      Command r = Turbine.arrayRefStoreImmediate(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          argToExpr(arrIx), prefixVar(outerArrayVar.name()));
      pointStack.peek().add(r);
    }
  }

  @Override
  public void initUpdateable(Var updateable, Arg val) {
    assert(Types.isScalarUpdateable(updateable.type()));
    if (!updateable.type().equals(Types.UP_FLOAT)) {
      throw new STCRuntimeError(updateable.type() +
          " not yet supported");
    }
    assert(val.isImmediateFloat());
    pointStack.peek().add(Turbine.updateableFloatInit(varToExpr(updateable),
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
    pointStack.peek().add(Turbine.floatGet(prefixVar(result.name()),
                          varToExpr(updateable), CacheMode.UNCACHED));
  }

  @Override
  public void update(Var updateable, UpdateMode updateMode, Var val) {
    assert(Types.isScalarUpdateable(updateable.type()));
    assert(Types.isScalarFuture(val.type()));
    assert(updateable.type().primType() ==
                             val.type().primType());
    assert(updateMode != null);
    String builtinName = getUpdateBuiltin(updateMode);
    pointStack.peek().add(new Command(builtinName, Arrays.asList(
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
    pointStack.peek().add(new Command(builtinName, Arrays.asList(
        (Expression)varToExpr(updateable), argToExpr(val))));
  }

  /** This prevents duplicate "package require" statements */
  private final Set<String> requiredPackages = new HashSet<String>();

  @Override
  public void defineBuiltinFunction(String name, FunctionType type,
            TclFunRef impl) {
    String pv = impl.pkg + impl.version;
    if (!impl.pkg.equals("turbine")) {
      if (!requiredPackages.contains(pv))
      {
        PackageRequire pr = new PackageRequire(impl.pkg, impl.version);
        pointStack.peek().add(pr);
        requiredPackages.add(pv);
        pointStack.peek().add(new Command(""));
      }
    }
    builtinSymbols.put(name, impl);
    logger.debug("TurbineGenerator: Defined built-in " + name);
  }

  @Override
  public void startFunction(String functionName,
                                     List<Var> oList,
                                     List<Var> iList)
  throws UserException
  {
    List<String> outputs = prefixVars(Var.nameList(oList));
    List<String> inputs  = prefixVars(Var.nameList(iList));
    // System.out.println("function" + functionName);
    boolean isMain = functionName.equals("main");
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
    Sequence point = pointStack.peek();

    //TODO: call const init code
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
                                      prefixVar(v.name()));
          s.add(command);
        }
        for (Var v : oList)
        {
          Command command = Turbine.storeInStack(v.name(),
                                            prefixVar(v.name()));
          s.add(command);
        }
      }
    }

    pointStack.push(s);
  }

    @Override
    public void endFunction()
  {
    pointStack.pop();
  }

    @Override
    public void startNestedBlock()
  {
    Sequence block = new Sequence();
    if (!noStack()) {
      TclTree[] t = Turbine.createStackFrame(StackFrameType.NESTED);
      block.add(t);
    }
    Sequence point = pointStack.peek();
    point.add(block);
    pointStack.push(block);
  }

    @Override
    public void endNestedBlock() {
    pointStack.pop();
  }

    @Override
    public void addComment(String comment) {
      pointStack.peek().add(new Comment(comment));
    }

  /** NOT UPDATED */

  /**
   * @param condition the variable name to branch based on
   * @param hasElse whether there will be an else clause ie. whether startElseBlock()
   *                will be called later for this if statement
   */
    @Override
    public void startIfStatement(Arg condition, boolean hasElse)
  {
    logger.trace("startIfStatement()...");
    assert(condition != null);
    assert(!condition.isVar()
        || condition.getVar().storage() == VarStorage.LOCAL);
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
    pointStack.peek().add(i);

    if (hasElse) {
       // Put it on the stack so it can be retrieved when we start else block
      pointStack.push(elseBlock);
    }
    pointStack.push(thenBlock);
  }

  @Override
    public void startElseBlock() {
      logger.trace("startElseBlock()...");
    pointStack.pop(); // Remove then block
  }

    @Override
    public void endIfStatement()
  {
    logger.trace("endIfStatement()...");
    pointStack.pop();
  }

    @Override
    public void startWaitStatement(String procName, List<Var> waitVars,
        List<Var> usedVariables, List<Var> keepOpenVars,
        WaitMode mode, boolean recursive, TaskMode target) {
      logger.trace("startWaitStatement()...");
      startAsync(procName, waitVars, usedVariables, keepOpenVars,
                 recursive, target);
    }

    @Override
    public void endWaitStatement(List<Var> usedVars, List<Var> keepOpenVars) {
      logger.trace("endWaitStatement()...");
      endAsync(usedVars, keepOpenVars);
    }

    /**
     * Internal helper to implement constructs that need to wait for
     * a number of variables, and then run some code
     * @param procName
     * @param waitVars
     * @param usedVariables
     * @param keepOpenVars
     * @param recursive 
     * @param shareWork if true, work will be shared with other rule engines
     *                  at the cost of higher overhead
     */
    private void startAsync(String procName, List<Var> waitVars,
        List<Var> usedVariables, List<Var> keepOpenVars,
        boolean recursive, TaskMode mode) {
      ArrayList<Var> toPassIn = new ArrayList<Var>();
      HashSet<String> alreadyInSet = new HashSet<String>();
      for (Var v: usedVariables) {
        toPassIn.add(v);
        alreadyInSet.add(v.name());
        if (v.type().equals(Types.V_BLOB)) {
          throw new STCRuntimeError("Can't directly pass blob value");
        }
      }
      
      // Also need to pass in refs to containers
      for (Var v: keepOpenVars) {
        if (!alreadyInSet.contains(v.name())) {
          toPassIn.add(v);
        }
      }
      
      List<String> args = new ArrayList<String>();
      args.add(Turbine.LOCAL_STACK_NAME);
      for (Var v: toPassIn) {
        args.add(prefixVar(v.name()));
      }

      Sequence constructProc = new Sequence();

      String uniqueName = uniqueTCLFunctionName(procName);
      Proc proc = new Proc(uniqueName, usedTclFunctionNames, args,
                                                    constructProc);
      tree.add(proc);

      boolean useDeepWait = false; // True if we have to use special wait impl. 
      
      // Build up the rule string
      List<Expression> waitFor = new ArrayList<Expression>();
      for (Var w: waitVars) {
        if (recursive) {
          Type baseType = w.type();
          if (Types.isArray(w.type())) {
            baseType = new ArrayInfo(w.type()).baseType;
            useDeepWait = true;
          }
          if (Types.isScalarFuture(baseType)) {
            // ok
          } else {
            throw new STCRuntimeError("Recursive wait not yet supported"
                + " for type: " + w.type().typeName());
          }
        }
        
        Expression waitExpr = getTurbineWaitId(w);
        waitFor.add(waitExpr);
      }

      // increment read or write refs as needed
      incrementAllRefs(usedVariables, keepOpenVars);

      TclList action = buildAction(uniqueName, toPassIn);

      boolean local = execContextStack.peek() == ExecContext.CONTROL;
      
      if (useDeepWait) {
        // Nesting depth of arrays (0 == not array)
        int depths[] = new int[waitVars.size()];
        boolean isFile[] = new boolean[waitVars.size()];
        for (int i = 0; i < waitVars.size(); i++) {
          Type waitVarType = waitVars.get(i).type();
          Type baseType;
          if (Types.isArray(waitVarType)) {
            ArrayInfo ai = new ArrayInfo(waitVarType);
            depths[i] = ai.nesting;
            baseType = ai.baseType;
          } else {
            depths[i] = 0;
            baseType = waitVarType;
          }
          isFile[i] = Types.isFile(baseType);
        }
        pointStack.peek().add(Turbine.deepRule(uniqueName, waitFor, depths,
                                             isFile, action, mode, local));
      } else {
        // Whether we can enqueue rules locally
        pointStack.peek().add(
              Turbine.rule(uniqueName, waitFor, action, mode, local));
      }
      pointStack.push(constructProc);
      
      
      ExecContext newExecContext;
      if (mode == TaskMode.LEAF) {
        newExecContext = ExecContext.LEAF;
      } else if (mode == TaskMode.CONTROL) {
        newExecContext = ExecContext.CONTROL;
      } else {
        // Executes on same node
        newExecContext = execContextStack.peek();
      }
      execContextStack.push(newExecContext);
    }

    private void endAsync(List<Var> usedVars, List<Var> keepOpenVars) {
      // decrement read or write refs as needed
      decrementAllRefs(usedVars, keepOpenVars);
      execContextStack.pop();
      pointStack.pop();
    }


    private void incrementAllRefs(List<Var> usedVars, List<Var> keepOpenVars) {
      incrementAllRefs(usedVars, keepOpenVars, null);
    }
    
    private void incrementAllRefs(List<Var> usedVars, List<Var> keepOpenVars, Expression refIncrAmount) {
      List<Var> readOnlyUsedVars = Var.varListDiff(usedVars,
                                                   keepOpenVars);
      pointStack.peek().append(incrementReaders(readOnlyUsedVars,
                                   refIncrAmount));
      pointStack.peek().append(incrementWriters(keepOpenVars,
                                   refIncrAmount));
    }

    /**
     * @param t
     * @return true if type has refcount to be managed
     */
    private static boolean hasRefcount(Var v) {
      if (Types.isScalarValue(v.type())) {
        return false;
      } else if (v.defType() == DefType.GLOBAL_CONST) {
        return false;
      }
      return true;
    }

    /**
     * Increment refcount of all vars by one
     * @param vars
     */
    private void incrementReaders(Var ...vars) {
      pointStack.peek().append(incrementReaders(Arrays.asList(vars), null));
    }

    /**
     * Decrement refcount of all vars by one
     * @param vars
     */
    private void decrementReaders(Var ...vars) {
      pointStack.peek().append(decrementReaders(Arrays.asList(vars), null));
    }

    private static Sequence incrementReaders(List<Var> vars, Expression incr) {
      return incrementReaders(vars, incr, false);
    }

    private Sequence decrementReaders(List<Var> vars, Expression incr) {
      return incrementReaders(vars, incr, true);
    }

    /**
     * Increment readers by a
     * TODO: handle struct and file vars
     * @param vars
     * @param incr expression for the amount of increment/decrement.  If null, assume 1
     * @param negate if true, then negate incr
     * @return
     */
    private static Sequence incrementReaders(List<Var> vars, Expression incr, boolean negate) {
      Sequence seq = new Sequence();
      for (VarCount vc: Var.countVars(vars)) {
        Var var = vc.var;
        if (!hasRefcount(var)) {
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
          amount = Square.arithExpr(new LiteralInt(vc.count), new Token("*"), incr);
        }
        if (Types.isFile(var.type())) {
          // Need to use different function to handle file reference
          if (negate) {
            seq.add(Turbine.decrFileRef(varToExpr(var), amount));
          } else {
            seq.add(Turbine.incrFileRef(varToExpr(var), amount));
          }
        } else if (Types.isStruct(var.type())) {
          // TODO: how to refcount for struct
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
    
    private static Sequence incrementWriters(List<Var> keepOpenVars,
          Expression incr) {
      Sequence seq = new Sequence();
      for (VarCount vc: Var.countVars(keepOpenVars)) {
        if (!hasRefcount(vc.var)) {
          continue;
        }
        if (incr == null) {
          seq.add(Turbine.containerSlotCreate(varToExpr(vc.var), new LiteralInt(vc.count)));
        } else if (vc.count == 1) {
          seq.add(Turbine.containerSlotCreate(varToExpr(vc.var), incr));
        } else {
          seq.add(Turbine.containerSlotCreate(varToExpr(vc.var),
              Square.arithExpr(new LiteralInt(vc.count), new Token("*"), incr)));
        }
      }
      return seq;
    }
    
    private void decrementAllRefs(List<Var> usedVars, List<Var> keepOpenVars) {
      decrementAllRefs(usedVars, keepOpenVars, null);
    }
    
    private void decrementAllRefs(List<Var> usedVars, List<Var> keepOpenVars,
        Expression refDecrAmount) {
      List<Var> readOnlyUsedVars = Var.varListDiff(usedVars, keepOpenVars);
      pointStack.peek().append(decrementReaders(readOnlyUsedVars, refDecrAmount));
      pointStack.peek().append(decrementWriters(keepOpenVars, refDecrAmount));
    }

    private static Sequence decrementWriters(List<Var> vars,
                                             Expression decr) {
      Sequence seq = new Sequence();
      for (VarCount vc: Var.countVars(vars)) {
        if (!hasRefcount(vc.var)) {
          continue;
        }
        if (decr == null) {
          seq.add(Turbine.containerSlotDrop(varToExpr(vc.var), new LiteralInt(vc.count)));
        } else if (vc.count == 1) {
          seq.add(Turbine.containerSlotDrop(varToExpr(vc.var), decr));
        } else {
          seq.add(Turbine.containerSlotDrop(varToExpr(vc.var),
              Square.arithExpr(new LiteralInt(vc.count), new Token("*"), decr)));
        }
      }
      return seq;
    }

    private TclList buildAction(String procName,
        List<Var> usedVariables) {

      ArrayList<Expression> ruleTokens = new ArrayList<Expression>();
      ruleTokens.add(new Token(procName));
      ruleTokens.add(new Value(Turbine.LOCAL_STACK_NAME));
      // Pass in variable ids directly in rule string
      for (Var v: usedVariables) {
        Type t = v.type();
        if (Types.isScalarFuture(t) || Types.isRef(t) ||
            Types.isArray(t) || Types.isStruct(t) ||
            Types.isScalarUpdateable(t)) {
          // Just passing turbine id
          ruleTokens.add(varToExpr(v));
        } else if (Types.isScalarValue(t)) {
          PrimType pt = t.primType();
          if (pt == PrimType.INT || pt == PrimType.BOOL
              || pt == PrimType.FLOAT || pt == PrimType.STRING) {
            // Serialize
            ruleTokens.add(varToExpr(v));
          } else {
            throw new STCRuntimeError("Don't know how to pass" +
                  " var with type " + v);
          }
        } else {
          throw new STCRuntimeError("Don't know how to pass var with type "
              + v);
        }
      }
      return new TclList(ruleTokens);
    }

    @Override
    public void startSwitch(Arg switchVar, List<Integer> caseLabels,
              boolean hasDefault) {
    logger.trace("startSwitch()...");
    assert(switchVar != null);
    assert(!switchVar.isVar() ||
        switchVar.getVar().storage() == VarStorage.LOCAL);
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
    pointStack.peek().add(sw);

    for (int c = 1; c <= casecount; c++) {
      // Push case in reverse order so we can pop off as we add cases
      pointStack.push(caseBodies.get(casecount - c));
    }
  }

    @Override
    public void endCase() {
    logger.trace("endCase()...");
    // Pop the body of the last case statement off the stack
    pointStack.pop();

  }

  @Override
  public void endSwitch() {
    logger.trace("endSwitch()...");
    // don't pop anything off, last case should already be gone
  }

  @Override
  public void startForeachLoop(String loopName,
          Var arrayVar, Var memberVar,
          Var loopCountVar, int splitDegree,
          boolean arrayClosed, List<Var> usedVariables, List<Var> keepOpenVars) {
    assert(Types.isArray(arrayVar.type()));
    assert(loopCountVar == null ||
              loopCountVar.type().equals(Types.V_INT));
    if (!arrayClosed) {
      throw new STCRuntimeError("Loops over open containers not yet supported");
    }

    if (!arrayClosed) {
      ArrayList<Var> passIn = new ArrayList<Var>(usedVariables);
      if (!passIn.contains(arrayVar)) {
        passIn.add(arrayVar);
      }
      startAsync(loopName + ":arrwait", 
                  Arrays.asList(arrayVar), passIn,
                  keepOpenVars, false, TaskMode.LOCAL);
    }

    boolean haveKeys = loopCountVar != null;
    String contentsVar = TCLTMP_ARRAY_CONTENTS;

    if (splitDegree <= 0) {
      pointStack.peek().add(Turbine.containerContents(contentsVar,
                          varToExpr(arrayVar), haveKeys));
    } else {
      startForeachSplit(loopName, arrayVar, contentsVar, splitDegree, haveKeys,
          usedVariables, keepOpenVars);
    }
    startForeachInner(new Value(contentsVar), memberVar, loopCountVar,
        usedVariables, keepOpenVars);
  }

  private void startForeachSplit(String procName, Var arrayVar,
      String contentsVar, int splitDegree, boolean haveKeys,
      List<Var> usedVars, List<Var> keepOpenVars) {
    // load array size
    pointStack.peek().add(Turbine.containerSize(TCLTMP_CONTAINER_SIZE,
                                      varToExpr(arrayVar)));
    Expression lastIndex = Square.arithExpr(new Value(TCLTMP_CONTAINER_SIZE),
          new Token("-"), new LiteralInt(1));

    // recursively split the range
    ArrayList<Var> splitUsedVars = new ArrayList<Var>(usedVars);
    splitUsedVars.add(arrayVar);
    startRangeSplit(procName, splitUsedVars, keepOpenVars,
          splitDegree, new LiteralInt(0), lastIndex, new LiteralInt(1));

    // need to find the length of this split since that is what the turbine
    //  call wants
    pointStack.peek().add(new SetVariable(TCLTMP_SPLITLEN,
            Square.arithExpr(new Token(
            String.format("${%s} - ${%s} + 1", TCLTMP_RANGE_HI,
                                               TCLTMP_RANGE_LO)))));

    // load the subcontainer
    pointStack.peek().add(Turbine.containerContents(contentsVar,
        varToExpr(arrayVar), haveKeys, new Value(TCLTMP_SPLITLEN),
        TCLTMP_RANGE_LO_V));
  }

  private void startForeachInner(
      Value arrayContents, Var memberVar, Var loopCountVar,
      List<Var> usedVariables, List<Var> keepOpenVars) {
    Sequence curr = pointStack.peek();
    boolean haveKeys = loopCountVar != null;
    Sequence loopBody = new Sequence();

    String tclMemberVar = prefixVar(memberVar.name());
    String tclCountVar = haveKeys ? prefixVar(loopCountVar.name()) : null;

    /* Iterate over keys and values, or just values */
    Sequence tclLoop;
    if (haveKeys) {
      tclLoop = new DictFor(new Token(tclCountVar), new Token(tclMemberVar),
                      arrayContents, loopBody);
    } else {
      tclLoop = new ForEach(new Token(tclMemberVar), arrayContents, loopBody);
    }
    curr.add(tclLoop);
    pointStack.push(loopBody);
  }


  @Override
  public void endForeachLoop(int splitDegree, boolean arrayClosed,
                             List<Var> usedVars, List<Var> keepOpenVars) {
    assert(pointStack.size() >= 2);
    pointStack.pop(); // tclloop body
    if (splitDegree > 0) {
      endRangeSplit(usedVars, keepOpenVars);
    }
  }

  @Override
  public void startRangeLoop(String loopName, Var loopVar, Var countVar,
      Arg start, Arg end, Arg increment, List<Var> usedVariables,
      List<Var> keepOpenVars, int desiredUnroll, int splitDegree) {
    assert(start.isImmediateInt());
    assert(end.isImmediateInt());
    assert(increment.isImmediateInt());
    assert(loopVar.type().equals(Types.V_INT));
    if (countVar != null) { 
      throw new STCRuntimeError("Backend doesn't support counter var in range " +
      		                      "loop yet");
      
    }
    Expression startE = argToExpr(start);
    Expression endE = argToExpr(end);
    Expression incrE = argToExpr(increment);

    if (splitDegree > 0) {
      startRangeSplit(loopName, usedVariables,
              keepOpenVars, splitDegree, startE, endE, incrE);
      startRangeLoopInner(loopName, loopVar, usedVariables,
              keepOpenVars, TCLTMP_RANGE_LO_V, TCLTMP_RANGE_HI_V,
                                                      TCLTMP_RANGE_INC_V);
    } else {
      startRangeLoopInner(loopName, loopVar, usedVariables,
              keepOpenVars, startE, endE, incrE);
    }
  }

  @Override
  public void endRangeLoop(List<Var> usedVars, List<Var> keepOpenVars,
                        int splitDegree) {
    assert(pointStack.size() >= 2);
    pointStack.pop(); // for loop body

    if (splitDegree > 0) {
      endRangeSplit(usedVars,keepOpenVars);
    }
  }

  private void startRangeLoopInner(String loopName, Var loopVar,
          List<Var> usedVariables, List<Var> keepOpenVars,
          Expression startE, Expression endE, Expression incrE) {
    Sequence loopBody = new Sequence();
    String loopVarName = prefixVar(loopVar.name());
    ForLoop tclLoop = new ForLoop(loopVarName, startE, endE, incrE, loopBody);
    pointStack.peek().add(tclLoop);
    pointStack.push(loopBody);

    ArrayList<Var> loopUsedVars = new ArrayList<Var>(usedVariables);
    loopUsedVars.add(loopVar);
  }

  /**
   * After this function is called, in the TCL context at the top of the stack
   * will be available the bottom, top (inclusive) and increment of the split in
   * tcl values: TCLTMP_RANGE_LO TCLTMP_RANGE_HI and TCLTMP_RANGE_INC
   * @param loopName
   * @param usedVariables
   * @param keepOpenVars
   * @param splitDegree
   * @param startE start of range (inclusive)
   * @param endE end of range (inclusive)
   * @param incrE
   */
  private void startRangeSplit(String loopName,
          List<Var> usedVariables,
          List<Var> keepOpenVars, int splitDegree,
          Expression startE, Expression endE, Expression incrE) {
    
    // All variables that must be passed in, including any keepopenvars
    List<Var> allUsedVars = Var.varListUnion(usedVariables, keepOpenVars);
    
    // Create two procedures that will be called: an outer procedure
    //  that recursively breaks up the foreach loop into chunks,
    //  and an inner procedure that actually runs the loop
    List<String> commonFormalArgs = new ArrayList<String>();
    commonFormalArgs.add(Turbine.LOCAL_STACK_NAME);
    for (Var uv: allUsedVars) {
      commonFormalArgs.add(prefixVar(uv.name()));
    }
    commonFormalArgs.add(TCLTMP_RANGE_LO);
    commonFormalArgs.add(TCLTMP_RANGE_HI);
    commonFormalArgs.add(TCLTMP_RANGE_INC);
    List<String> outerFormalArgs = new ArrayList<String>(commonFormalArgs);
    

    Value loVal = new Value(TCLTMP_RANGE_LO);
    Value hiVal = new Value(TCLTMP_RANGE_HI);
    Value incVal = new Value(TCLTMP_RANGE_INC);

    List<Expression> commonArgs = new ArrayList<Expression>();
    commonArgs.add(new Value(Turbine.LOCAL_STACK_NAME));
    for (Var uv: allUsedVars) {
      commonArgs.add(varToExpr(uv));
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

    // Increment references by # of iterations
    pointStack.peek().add(new SetVariable(TCLTMP_ITERSTOTAL,
                     rangeItersLeft(startE, endE, incrE)));
    incrementAllRefs(usedVariables, keepOpenVars, new Value(TCLTMP_ITERSTOTAL));
    
    // Call outer directly
    pointStack.peek().add(new Command(outerProcName, outerCallArgs));


    // itersLeft = ceil( (hi - lo + 1) /(double) inc))
    // ==> itersLeft = ( (hi - lo) / inc ) + 1
    outer.add(new SetVariable(TCLTMP_ITERSLEFT,
              rangeItersLeft(new Value(TCLTMP_RANGE_LO),
                             new Value(TCLTMP_RANGE_HI),
                             new Value(TCLTMP_RANGE_INC))));
    Expression doneSplitting = Square.arithExpr(
            new Value(TCLTMP_ITERSLEFT), new Token("<="),
            new LiteralInt(splitDegree));
    Sequence thenB = new Sequence();
    Sequence elseB = new Sequence();

    // if (iters < splitFactor) then <call inner> else <split more>
    If splitIf = new If(doneSplitting, thenB, elseB);
    outer.add(splitIf);

    thenB.add(new Command(innerProcName, innerCallArgs));

    Sequence splitBody = new Sequence();
    String splitStart = "tcltmp:splitstart";
    String skip = "tcltmp:skip";
    // skip = max(splitFactor,  ceil(iters /(float) splitfactor))
    // skip = max(splitFactor,  ((iters - 1) /(int) splitfactor) + 1)
    elseB.add(new SetVariable(skip, Square.arithExpr(new Token(
          String.format("max(%d, ((%s - 1) / %d ) + 1) * %s",
                  splitDegree, new Value(TCLTMP_ITERSLEFT),
                  splitDegree, new Value(TCLTMP_RANGE_INC))))));

    ForLoop splitLoop = new ForLoop(splitStart, loVal,
            hiVal, new Value(skip), splitBody);
    elseB.add(splitLoop);


    ArrayList<Expression> outerRecCall = new ArrayList<Expression>();
    outerRecCall.add(new Token(outerProcName));
    outerRecCall.addAll(commonArgs);
    outerRecCall.add(new Value(splitStart));
    // splitEnd = min(hi, start + skip - 1)
    Square splitEnd = Square.arithExpr(new Token(String.format(
            "min(${%s}, ${%s} + ${%s} - 1)", TCLTMP_RANGE_HI,
            splitStart, skip)));
    outerRecCall.add(splitEnd);
    outerRecCall.add(incVal);

    splitBody.add(Turbine.rule(outerProcName, new ArrayList<Value>(0),
                    new TclList(outerRecCall), TaskMode.CONTROL, true));

    pointStack.push(inner);
  }

  private Square rangeItersLeft(Expression lo, Expression hi, Expression inc) {
    return Square.arithExpr(new Token("(("), hi, new Token("-"), 
            lo, new Token(")"), new Token("/"), inc, new Token(")"),
            new Token("+"), new LiteralInt(1));
  }

  private void endRangeSplit(List<Var> usedVars, List<Var> keepOpenVars) {
    // Decrement # of iterations executed in inner block
    pointStack.peek().add(new SetVariable(TCLTMP_REF_DECR, 
                            rangeItersLeft(new Value(TCLTMP_RANGE_LO),
                                           new Value(TCLTMP_RANGE_HI),
                                           new Value(TCLTMP_RANGE_INC))));
    decrementAllRefs(usedVars, keepOpenVars, new Value(TCLTMP_REF_DECR));
    pointStack.pop(); // inner proc body
  }

  @Override
  public void addGlobal(String name, Arg val) {
    String tclName = prefixVar(name);
    Value tclVal = new Value(tclName);
    globInit.add(Turbine.makeTCLGlobal(tclName));
    String typePrefix;
    Expression expr;
    Command setCmd;
    switch (val.getKind()) {
    case INTVAL:
      typePrefix = Turbine.INTEGER_TYPENAME;
      expr = new LiteralInt(val.getIntLit());
      setCmd = Turbine.integerSet(tclVal, expr);
      break;
    case FLOATVAL:
      typePrefix = Turbine.FLOAT_TYPENAME;
      expr = new LiteralFloat(val.getFloatLit());
      setCmd = Turbine.floatSet(tclVal, expr);
      break;
    case STRINGVAL:
      typePrefix = Turbine.STRING_TYPENAME;
      expr = new TclString(val.getStringLit(), true);
      setCmd = Turbine.stringSet(tclVal, expr);
      break;
    case BOOLVAL:
      typePrefix = Turbine.INTEGER_TYPENAME;
      expr = new LiteralInt(val.getBoolLit() ? 1 : 0);
      setCmd = Turbine.integerSet(tclVal, expr);
      break;
    default:
      throw new STCRuntimeError("Non-constant oparg type "
          + val.getKind());
    }
    globInit.add(Turbine.allocate(tclName, typePrefix, false));
    globInit.add(setCmd);
    try {
      if (Settings.getBoolean(Settings.EXPERIMENTAL_REFCOUNTING)) {
        globInit.add(Turbine.makePermanent(new Value(tclName)));
      }
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError(e.getMessage());
    }
  }

  /**
     Generate and return Tcl from  our internal TclTree
   */
    @Override
    public String code()
  {
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
      System.exit(ExitCode.ERROR_INTERNAL.code());
    }
    return sb.toString();
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
    if (Types.isFile(var.type())) {
      // Block on file status
      waitExpr = Turbine.getFileStatus(wv);
    } else if (Types.isScalarFuture(var.type()) ||
            Types.isRef(var.type()) ||
            Types.isArray(var.type())) {
      waitExpr = wv;
    } else {
      throw new STCRuntimeError("Don't know how to wait on var: "
              + var.toString());
    }
    return waitExpr;
  }

  private Expression argToExpr(Arg in) {
    return TclUtil.argToExpr(in);
  }

    private static String prefixVar(String varname) {
      return TclNamer.prefixVar(varname);
    }

    private static List<String> prefixVars(List<String> vlist) {
      return TclNamer.prefixVars(vlist);
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


    /** Some types have handles which aren't simple integers:
     * represent references to these types as strings
     * @param t
     * @return
     */
    private boolean refIsString(Type t) {
      return Types.isStructRef(t) || Types.isFileRef(t);
    }
    
    @Override
    public void optimise() {
      // do nothing
    }

    @Override
    public void regenerate(CompilerBackend codeGen) {
      throw new UnsupportedOperationException("TurbineGenerator can't "
          + " reconstitute code");

    }

    @Override
    public void startLoop(String loopName, List<Var> loopVars,
        List<Var> initVals, List<Var> usedVariables,
        List<Var> keepOpenVars, List<Boolean> blockingVars) {

      // call rule to start the loop, pass in initVals, usedVariables
      ArrayList<String> loopFnArgs = new ArrayList<String>();
      ArrayList<Value> firstIterArgs = new ArrayList<Value>();
      loopFnArgs.add(Turbine.LOCAL_STACK_NAME);
      firstIterArgs.add(new Value(Turbine.LOCAL_STACK_NAME));

      for (Var arg: loopVars) {
        loopFnArgs.add(prefixVar(arg.name()));
      }
      for (Var init: initVals) {
        firstIterArgs.add(varToExpr(init));
      }

      for (Var uv: usedVariables) {
        loopFnArgs.add(prefixVar(uv.name()));
        firstIterArgs.add(varToExpr(uv));
      }


      // See which values the loop should block on
      ArrayList<Value> blockingVals = new ArrayList<Value>();
      assert(blockingVars.size() == initVals.size());
      for (int i = 0; i < blockingVars.size(); i++) {
        Var iv = initVals.get(i);
        if (blockingVars.get(i)) {
          blockingVals.add(varToExpr(iv));
        }
      }
      // Increment references before async call
      incrementAllRefs(usedVariables, keepOpenVars);
      // Increment references for condition variable and loop var
      pointStack.peek().append(incrementReaders(initVals, null));


      String uniqueLoopName = uniqueTCLFunctionName(loopName);

      pointStack.peek().add(Turbine.loopRule(
          uniqueLoopName, firstIterArgs, blockingVals));

      Sequence loopBody = new Sequence();
      Proc loopProc = new Proc(uniqueLoopName, usedTclFunctionNames,
                                              loopFnArgs, loopBody);
      tree.add(loopProc);
      // add loop body to pointstack, loop to loop stack
      pointStack.push(loopBody);
      loopNameStack.push(uniqueLoopName);
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
    public void loopContinue(List<Var> newVals,
           List<Var> usedVariables, List<Var> registeredContainers,
           List<Boolean> blockingVars) {
      ArrayList<Value> nextIterArgs = new ArrayList<Value>();
      String loopName = loopNameStack.peek();
      nextIterArgs.add(new Value(Turbine.LOCAL_STACK_NAME));

      for (Var v: newVals) {
        nextIterArgs.add(varToExpr(v));
      }
      for (Var v: usedVariables) {
        nextIterArgs.add(varToExpr(v));
      }
      ArrayList<Value> blockingVals = new ArrayList<Value>();
      assert(newVals.size() == blockingVars.size());
      for (int i = 0; i < newVals.size(); i++) {
        if (blockingVars.get(i)) {
          blockingVals.add(varToExpr(newVals.get(i)));
        }
      }
      pointStack.peek().append(incrementReaders(newVals, null));
      pointStack.peek().add(Turbine.loopRule(loopName,
          nextIterArgs, blockingVals));
    }

    @Override
    public void loopBreak(List<Var> loopUsedVars, List<Var> keepOpenVars) {
      decrementAllRefs(loopUsedVars, keepOpenVars);
    }

    @Override
    public void endLoop() {
      assert(pointStack.size() >= 2);
      assert(loopNameStack.size() > 0);
      pointStack.pop();
      loopNameStack.pop();
    }
}
