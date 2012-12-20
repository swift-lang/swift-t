/**
 * This module handles the higher-level logic of generating swig/c wrapper code.
 * More mechanical aspects of code generation are handled in TclTurbineTree
 * or the classes in the exm.tcl.tree module
 */
package exm.stc.swigcbackend;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.Settings;
import exm.stc.common.TclFunRef;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedTypeException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Arg.ArgKind;
import exm.stc.common.lang.Builtins;
import exm.stc.common.lang.Builtins.TclOpTemplate;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Operators.UpdateMode;
import exm.stc.common.lang.Redirects;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.PrimType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.swigcbackend.tree.Command;
import exm.stc.swigcbackend.tree.Comment;
import exm.stc.swigcbackend.tree.DictFor;
import exm.stc.swigcbackend.tree.Expression;
import exm.stc.swigcbackend.tree.ForEach;
import exm.stc.swigcbackend.tree.ForLoop;
import exm.stc.swigcbackend.tree.If;
import exm.stc.swigcbackend.tree.LiteralFloat;
import exm.stc.swigcbackend.tree.LiteralInt;
import exm.stc.swigcbackend.tree.PackageRequire;
import exm.stc.swigcbackend.tree.Proc;
import exm.stc.swigcbackend.tree.Sequence;
import exm.stc.swigcbackend.tree.SetVariable;
import exm.stc.swigcbackend.tree.Square;
import exm.stc.swigcbackend.tree.Switch;
import exm.stc.swigcbackend.tree.TclList;
import exm.stc.swigcbackend.tree.TclString;
import exm.stc.swigcbackend.tree.TclTree;
import exm.stc.swigcbackend.tree.Text;
import exm.stc.swigcbackend.tree.Token;
import exm.stc.swigcbackend.tree.Value;
import exm.stc.tclbackend.TclNamer;
import exm.stc.ui.ExitCode;

public class SwigcGenerator implements CompilerBackend
{
  

  /**
   * TODO Placeholder to stop compilation errors
   *
   */
  private static class StackFrameType {

    public static final String NESTED = null;
    public static final String COMPOSITE = null;
    public static String MAIN;
    
  }
  
  /**
   * TODO Placeholder to stop compilation errors.
   */
  private static class Turbine {

    public static final String LOCAL_STACK_NAME = null;
    public static final String STRING_TYPENAME = null;
    public static final String INTEGER_TYPENAME = null;
    public static final String BLOB_TYPENAME = null;
    public static final String VOID_TYPENAME = null;
    public static final String FLOAT_TYPENAME = null;

    public static Command storeInStack(String name, String tclName) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree containerSlotDrop(Value varToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree integerGet(String prefixVar, Value varToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Command integerSet(String prefixVar, Expression opargToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree structInsert(String prefixVar, String fieldName,
        String prefixVar2) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence
        dereferenceString(String prefixVar, String prefixVar2) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree containerCreateNested(String prefixVar,
        String prefixVar2, String prefixVar3) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence arrayRefStoreImmediate(String prefixVar,
        String prefixVar2, Expression opargToExpr, String prefixVar3) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence arrayRefStoreComputed(String prefixVar,
        String prefixVar2, String prefixVar3, String prefixVar4) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree arrayLookupImm(String prefixVar, String prefixVar2,
        Expression opargToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence arrayStoreImmediate(String prefixVar,
        String prefixVar2, Expression opargToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree turbineLog(String string) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree makeTCLGlobal(String tclName) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree allocateContainer(String tclName,
        String stringTypename) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree allocate(String tclName, String integerTypename) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Command stringSet(String prefixVar, Expression opargToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree stringGet(String prefixVar, Value varToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree setPriority(Expression opargToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree createDummyStackFrame() {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree containerSize(String tcltmpContainerSize,
        Value varToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree containerContents(String contentsVar,
        Value varToExpr, boolean haveKeys) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree loopRule(String uniqueLoopName,
        ArrayList<Value> firstIterArgs, ArrayList<Value> blockingVals) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree containerSlotCreate(Value varToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Command floatSet(String tclName, Expression expr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree rule(String outerProcName,
        ArrayList<Value> arrayList, TclList tclList, boolean b) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree containerRefCreateNestedImmIx(String prefixVar,
        String prefixVar2, Expression opargToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree resetPriority() {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence arrayRefDerefStore(String prefixVar,
        String prefixVar2, Expression opargToExpr, String prefixVar3) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence arrayStoreComputed(String prefixVar,
        String prefixVar2, String prefixVar3) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence arrayRefDerefStoreComputed(String prefixVar,
        String prefixVar2, String prefixVar3, String prefixVar4) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree[] createStackFrame(String nested) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree containerContents(String contentsVar,
        Value varToExpr, boolean haveKeys, Value value, Value tcltmpRangeLoV) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree floatGet(String prefixVar, Value varToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree allocateFile(Value mapExpr, String tclName) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree structRefLookupFieldID(String prefixVar,
        String structField, String prefixVar2) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence arrayLookupComputed(String prefixVar,
        String prefixVar2, String prefixVar3, boolean isArrayRef) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence dereferenceInteger(String prefixVar,
        String prefixVar2) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence
        dereferenceFloat(String prefixVar, String prefixVar2) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree containerRefCreateNested(String prefixVar,
        String prefixVar2, String prefixVar3) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree containerCreateNestedImmIx(String prefixVar,
        String prefixVar2, Expression opargToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence arrayDerefStore(String prefixVar, String prefixVar2,
        Expression opargToExpr) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree callComposite(String compFuncName, TclList oList,
        TclList iList, TclList tclListOfVariables) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree callCompositeSync(String compFuncName, TclList oList,
        TclList iList) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree structLookupFieldID(String prefixVar,
        String structField, String prefixVar2) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence arrayLookupImmIx(String prefixVar,
        String prefixVar2, Expression opargToExpr, boolean isArrayRef) {
      // TODO Auto-generated method stub
      return null;
    }

    public static Sequence arrayDerefStoreComputed(String prefixVar,
        String prefixVar2, String prefixVar3) {
      // TODO Auto-generated method stub
      return null;
    }

    public static TclTree rule(String uniqueName, List<Value> inputs,
        TclList action, boolean shareWork) {
      // TODO Auto-generated method stub
      return null;
    }
    
  }
  
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
  private int foreach_counter = 0;

  public SwigcGenerator(Logger logger, String timestamp)
  {
    this.logger = logger;
    this.timestamp = timestamp;
    pointStack.push(tree);

    if (Settings.get("DEBUGGER") == "COMMENTS")
      debuggerComments = true;
  }

  @Override
  public void header()
  {
    tree.add(new Text(""));
    tree.add(new Comment("generated by swig parser"));
    tree.add(new Comment("date: " + timestamp));
    tree.add(new Comment("File: example.i"));

    tree.add(new Text(""));

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
        point.add(Turbine.allocate(tclName, tprefix));
      }
    } else if (Types.isArray(t)) {
      point.add(Turbine.allocateContainer(tclName, Turbine.INTEGER_TYPENAME));
    } else if (Types.isRef(t)) {
      point.add(Turbine.allocate(tclName, Turbine.INTEGER_TYPENAME));
    } else if (Types.isStruct(t)) {
      point.add(Turbine.allocateContainer(tclName, Turbine.STRING_TYPENAME));
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
    pointStack.peek().add(Turbine.containerSlotDrop(varToExpr(arr)));
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
    pointStack.peek().add(Turbine.integerSet(
          prefixVar(target.name()), varToExpr(src)));
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
                              prefixVar(target.name()),
                              opargToExpr(src)));
  }

  @Override
  public void retrieveInt(Var target, Var source) {
    assert(target.type().equals(Types.V_INT));
    assert(source.type().equals(Types.F_INT));
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
                             prefixVar(target.name()),
                             opargToExpr(src)));
  }

  @Override
  public void retrieveBool(Var target, Var source) {
    assert(target.type().equals(Types.V_BOOL));
    assert(source.type().equals(Types.F_BOOL));
    pointStack.peek().add(Turbine.integerGet(prefixVar(target.name()),
        varToExpr(source)));
  }


  @Override
  public void assignFloat(Var target, Arg src) {
    assert(src.isImmediateFloat());
    if (!Types.isFloat(target.type())) {
      throw new STCRuntimeError("Expected variable to be float, "
          + " but was " + target.type().toString());
    }

    pointStack.peek().add(Turbine.floatSet(
                              prefixVar(target.name()),
                              opargToExpr(src)));
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

    pointStack.peek().add(Turbine.stringSet(prefixVar(target.name()),
                                                opargToExpr(src)));
  }

  @Override
  public void retrieveString(Var target, Var source) {
    assert(target.type().equals(Types.V_STRING));
    assert(source.type().equals(Types.F_STRING));
    pointStack.peek().add(Turbine.stringGet(prefixVar(target.name()),
                                                    varToExpr(source)));
  }

  @Override
  public void localOp(BuiltinOpcode op, Var out,
                                            List<Arg> in) {
    ArrayList<Expression> argExpr = new ArrayList<Expression>(in.size());
    for (Arg a: in) {
      argExpr.add(opargToExpr(a));
    }

    //pointStack.peek().add(BuiltinOps.genLocalOpTcl(op, out, in, argExpr));
  }
  
  @Override
  public void asyncOp(BuiltinOpcode op, Var out, List<Arg> in,
      Arg priority) {
    //TODO: for time being, share code with built-in function generation
    TclFunRef fn = null; //BuiltinOps.getBuiltinOpImpl(op);
    if (fn == null) {
      List<String> impls = Builtins.findOpImpl(op);
      
      // It should be impossible for there to be no implementation for a function
      // like this
      assert(impls != null);
      assert(impls.size() > 0);
      
      if (impls.size() > 1) {
        Logger.getLogger("").warn("Multiple implementations for operation " +
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
          new ArrayList<Var>(0) : Arrays.asList(out);
    builtinFunctionCall("operator: " + op.toString(), fn, 
                        inputs, outL, priority);
  }

  @Override
  public void dereferenceInt(Var target, Var src) {
    assert(target.type().equals(Types.F_INT));
    assert(src.type().equals(Types.R_INT));
    Sequence deref = Turbine.dereferenceInteger(prefixVar(target.name()),
        prefixVar(src.name()));
    pointStack.peek().add(deref);
  }

  @Override
  public void dereferenceBool(Var target, Var src) {
    assert(target.type().equals(Types.F_BOOL));
    assert(src.type().equals(Types.R_BOOL));
    Sequence deref = Turbine.dereferenceInteger(prefixVar(target.name()),
        prefixVar(src.name()));
    pointStack.peek().add(deref);
  }

  @Override
  public void dereferenceFloat(Var target, Var src) {
    assert(target.type().equals(Types.F_FLOAT));
    assert(src.type().equals(Types.R_FLOAT));
    Sequence deref = Turbine.dereferenceFloat(prefixVar(target.name()),
        prefixVar(src.name()));
    pointStack.peek().add(deref);
  }

  @Override
  public void dereferenceString(Var target, Var src) {
    assert(target.type().equals(Types.F_STRING));
    assert(src.type().equals(Types.R_STRING));
    Sequence deref = Turbine.dereferenceString(prefixVar(target.name()),
        prefixVar(src.name()));
    pointStack.peek().add(deref);
  }

  @Override
  public void dereferenceBlob(Var target, Var src) {
    assert(target.type().equals(Types.F_BLOB));
    assert(src.type().equals(Types.R_BLOB));
    Sequence deref = null;
    pointStack.peek().add(deref);
    //TODO
    throw new STCRuntimeError("TODO: dereferenceBlob");
  }

  @Override
  public void retrieveRef(Var target, Var src) {
    assert(Types.isRef(src.type()));
    assert(Types.isRefTo(src.type(), target.type()));
    TclTree deref = Turbine.integerGet(prefixVar(target.name()),
                                                   varToExpr(src));
    pointStack.peek().add(deref);
  }

  @Override
  public void arrayCreateNestedFuture(Var arrayResult,
      Var arrayVar, Var indexVar) {
    assert(Types.isArray(arrayVar.type()));
    assert(Types.isArrayRef(arrayResult.type()));
    assert(arrayResult.storage() == VarStorage.ALIAS);

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
        opargToExpr(arrIx));
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
        opargToExpr(arrIx));
    pointStack.peek().add(t);
  }

  @Override
  public void builtinFunctionCall(String function,
          List<Var> inputs, List<Var> outputs, Arg priority)
  {
    assert(priority == null || priority.isImmediateInt());
    logger.debug("call builtin: " + function);
    TclFunRef tclf = builtinSymbols.get(function);

    builtinFunctionCall(function, tclf, inputs, outputs, priority);
  }

  private void builtinFunctionCall(String function, TclFunRef tclf,
      List<Var> inputs, List<Var> outputs, Arg priority) {
    TclList iList = tclListOfVariables(inputs);
    TclList oList = tclListOfVariables(outputs);
    
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
    TclOpTemplate template = Builtins.getInlineTemplate(
                                                    functionName);
    HashMap<String, Expression> toks = new HashMap<String, Expression>();
    
    List<String> outNames = template.getOutNames();
    for (int i = 0; i < outputs.size(); i++) {
      Var out = outputs.get(i);
      String argName = outNames.get(i);
      toks.put(argName, new Token(prefixVar(out.name())));
    }
   
    //TODO: how to handle distinction between inputs and outputs
    // in general
    List<String> inNames = template.getInNames();
    assert(inNames.size() != inputs.size());
    for (int i = 0; i < inputs.size(); i++) {
      Arg in = inputs.get(i);
      String argName = inNames.get(i);
      toks.put(argName, opargToExpr(in));
    }
    
    assert(template != null);
    
  }
  
  @Override
  public void functionCall(String function,
              List<Var> inputs, List<Var> outputs,
              List<Boolean> blocking, TaskMode mode, Arg priority)  {
    assert(priority == null || priority.isImmediateInt());
    logger.debug("call composite: " + function);
    TclList iList = tclListOfVariables(inputs);
    TclList oList = tclListOfVariables(outputs);
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
    if (mode == TaskMode.CONTROL) {
      pointStack.peek().add(Turbine.callComposite(
                            TclNamer.swiftFuncName(function),
                            oList, iList, tclListOfVariables(blockOn)));
    } else {
      // Calling synchronously, can't guarantee anything blocks
      assert(blocking.size() == 0);
      pointStack.peek().add(Turbine.callCompositeSync(
          TclNamer.swiftFuncName(function),
          oList, iList));
    }
    clearPriority(priority);
  }

  private void clearPriority(Arg priority) {
    if (priority != null) {
      pointStack.peek().add(Turbine.resetPriority());
    }
  }

  private void setPriority(Arg priority) {
    if (priority != null) {
      logger.trace("priority: " + priority);
      pointStack.peek().add(Turbine.setPriority(opargToExpr(priority)));
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
    pointStack.peek().add(
        Turbine.containerSlotDrop(varToExpr(struct)));
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
    pointStack.peek().add(
        Turbine.structRefLookupFieldID(prefixVar(structVar.name()),
            structField, prefixVar(alias.name())));
  }


  @Override
  public void arrayLookupFuture(Var oVar, Var arrayVar, Var indexVar,
        boolean isArrayRef) {
    arrayLoadCheckTypes(oVar, arrayVar, isArrayRef);
    assert(indexVar.type().equals(Types.F_INT));
    assert(Types.isRef(oVar.type()));
    // Nested arrays - oVar should be a reference type
    Sequence getRef = Turbine.arrayLookupComputed(
        prefixVar(oVar.name()),
        prefixVar(arrayVar.name()), prefixVar(indexVar.name()), isArrayRef);
    pointStack.peek().add(getRef);
  }

  @Override
  public void arrayLookupRefImm(Var oVar, Var arrayVar, Arg arrIx,
        boolean isArrayRef) {
    assert(arrIx.isImmediateInt());
    arrayLoadCheckTypes(oVar, arrayVar, isArrayRef);
    Sequence getRef = Turbine.arrayLookupImmIx(
          prefixVar(oVar.name()),
          prefixVar(arrayVar.name()),
          opargToExpr(arrIx), isArrayRef);

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
         opargToExpr(arrIx)));
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
    Type memberType = arrayVar.type().memberType();
    if (Types.isRef(iVar.type())) {
      assert(iVar.type().memberType().equals(memberType));
      Sequence r = Turbine.arrayDerefStoreComputed(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          prefixVar(indexVar.name()));

      pointStack.peek().add(r);
    } else {
      assert(iVar.type().equals(memberType));
      Sequence r = Turbine.arrayStoreComputed(
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
    Type memberType = arrayVar.type().memberType().memberType();
    if (Types.isRef(iVar.type())) {
      assert(iVar.type().memberType().equals(memberType));
      Sequence r = Turbine.arrayRefDerefStoreComputed(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          prefixVar(indexVar.name()), prefixVar(outerArrayVar.name()));

      pointStack.peek().add(r);
    } else {
      assert(iVar.type().equals(memberType));
      Sequence r = Turbine.arrayRefStoreComputed(
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

    Type memberType = arrayVar.type().memberType();
    if (Types.isRef(iVar.type())) {
      // Check that we get the right thing when we dereference it
      if (!iVar.type().memberType().equals(memberType)) {
        throw new STCRuntimeError("Type mismatch when trying to store " +
            "from variable " + iVar.toString() + " into array " + arrayVar.toString());
      }
      Sequence r = Turbine.arrayDerefStore(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          opargToExpr(arrIx));
      pointStack.peek().add(r);
    } else {
      if (!iVar.type().equals(memberType)) {
        throw new STCRuntimeError("Type mismatch when trying to store " +
            "from variable " + iVar.toString() + " into array " + arrayVar.toString());
      }
      Sequence r = Turbine.arrayStoreImmediate(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          opargToExpr(arrIx));
      pointStack.peek().add(r);
    }
  }

  @Override
  public void arrayRefInsertImm(Var iVar, Var arrayVar,
        Arg arrIx, Var outerArrayVar) {
    assert(Types.isArrayRef(arrayVar.type()));
    assert(Types.isArray(outerArrayVar.type()));
    assert(arrIx.isImmediateInt());

    Type memberType = arrayVar.type().memberType().memberType();
    if (Types.isRef(iVar.type())) {
      // Check that we get the right thing when we dereference it
      if (!iVar.type().memberType().equals(memberType)) {
        throw new STCRuntimeError("Type mismatch when trying to store " +
            "from variable " + iVar.toString() + " into array " + arrayVar.toString());
      }
      Sequence r = Turbine.arrayRefDerefStore(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          opargToExpr(arrIx), prefixVar(outerArrayVar.name()));
      pointStack.peek().add(r);
    } else {
      if (!iVar.type().equals(memberType)) {
        throw new STCRuntimeError("Type mismatch when trying to store " +
            "from variable " + iVar.toString() + " into array " + arrayVar.toString());
      }
      Sequence r = Turbine.arrayRefStoreImmediate(
          prefixVar(iVar.name()), prefixVar(arrayVar.name()),
          opargToExpr(arrIx), prefixVar(outerArrayVar.name()));
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
    pointStack.peek().add(Turbine.floatSet(prefixVar(updateable.name()),
                                                         opargToExpr(val)));
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
    // just get the value the same as any other float future
    pointStack.peek().add(Turbine.floatGet(prefixVar(result.name()),
                                    varToExpr(updateable)));
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
        (Expression)varToExpr(updateable), opargToExpr(val))));
  }


  TclList tclListOfVariables(List<Var> inputs)
  {
    TclList result = new TclList();
    for (Var v : inputs)
      result.add(varToExpr(v));
    return result;
  }

  String stringOfVariables(List<Var> inputs, List<Var> outputs)
  {
    StringBuilder sb =
      new StringBuilder(inputs.size()+outputs.size()*32);

    Iterator<Var> it1 = inputs.iterator();
    Iterator<Var> it2 = outputs.iterator();
    while (it1.hasNext())
    {
      Var v = it1.next();
      Value val = varToExpr(v);
      val.appendTo(sb);
      if (it1.hasNext() || it2.hasNext())
        sb.append(' ');
    }
    while (it2.hasNext())
    {
      Var v = it2.next();
      Value val = varToExpr(v);
      val.appendTo(sb);
      if (it2.hasNext())
        sb.append(' ');
    }

    return sb.toString();
  }

  /** This prevents duplicate "package require" statements */
  private final Set<String> requiredPackages = new HashSet<String>();

  @Override
  public void defineBuiltinFunction(String name, FunctionType type,
                 TclFunRef impl)
  {
    String pv = impl.pkg+impl.version;
    if (!impl.pkg.equals("turbine")) {
      if (!requiredPackages.contains(pv))
      {
        PackageRequire pr = new PackageRequire(impl.pkg, impl.version);
        pointStack.peek().add(pr);
        requiredPackages.add(pv);
        pointStack.peek().add(new Command(""));
      }
    }
    builtinSymbols.put(name, new TclFunRef(impl.pkg, impl.symbol));
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
    s.add(Turbine.turbineLog("enter composite function: " +
                             functionName));

    if (noStack() && isMain) {
      s.add(Turbine.createDummyStackFrame());
    }

    if (!noStack()) {
      TclTree[] setupStack;
      if (isMain) {
        setupStack = Turbine.createStackFrame(StackFrameType.MAIN);
      } else {
        setupStack = Turbine.createStackFrame(StackFrameType.COMPOSITE);
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
    assert(condition.getKind() != ArgKind.VAR
        || condition.getVar().storage() == VarStorage.LOCAL);
    assert(condition.isImmediateBool()
        || condition.isImmediateInt());


    Sequence thenBlock = new Sequence();
    Sequence elseBlock = hasElse ? new Sequence() : null;
    if (!noStack()) {
      thenBlock.add(Turbine.createStackFrame(StackFrameType.NESTED));
      if (hasElse) {
        elseBlock.add(Turbine.createStackFrame(StackFrameType.NESTED));
      }
    }

    If i = new If(opargToExpr(condition),
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
                                                    false);
    }

    @Override
    public void endWaitStatement(List<Var> usedVars, List<Var> keepOpenVars) {
      logger.trace("endWaitStatement()...");
      endAsync(keepOpenVars);
    }

    /**
     * Internal helper to implement constructs that need to wait for
     * a number of variables, and then run some code
     * @param procName
     * @param waitVars
     * @param usedVariables
     * @param keepOpenVars
     * @param shareWork if true, work will be shared with other rule engines
     *                  at the cost of higher overhead
     */
    private void startAsync(String procName, List<Var> waitVars,
        List<Var> usedVariables, List<Var> keepOpenVars,
        boolean shareWork) {
      ArrayList<Var> toPassIn = new ArrayList<Var>();
      HashSet<String> alreadyInSet = new HashSet<String>();
      for (Var v: usedVariables) {
        toPassIn.add(v);
        alreadyInSet.add(v.name());
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

      // Build up the rule string
      List<Value> inputs = new ArrayList<Value>();
      for (Var w: waitVars) {
        inputs.add(varToExpr(w));
      }

      for (Var c: keepOpenVars) {
        pointStack.peek().add(
              Turbine.containerSlotCreate(varToExpr(c)));
      }

      TclList action = buildAction(uniqueName, toPassIn);
      pointStack.peek().add(
            Turbine.rule(uniqueName, inputs, action, shareWork));

      pointStack.push(constructProc);
    }

    private void endAsync(List<Var> keepOpenVars) {
      for (Var v: keepOpenVars) {
        pointStack.peek().add(Turbine.containerSlotDrop(varToExpr(v)));
      }
      pointStack.pop();
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
    assert(switchVar.getKind() != ArgKind.VAR ||
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

    Switch sw = new Switch(opargToExpr(switchVar),
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
                    boolean arrayClosed,
          List<Var> usedVariables, List<Var> keepOpenVars) {
    assert(Types.isArray(arrayVar.type()));
    assert(loopCountVar == null ||
              loopCountVar.type().equals(Types.V_INT));

    int foreach_num = foreach_counter++;
    String procName = "foreach:" + foreach_num;

    if (!arrayClosed) {
      ArrayList<Var> passIn = new ArrayList<Var>(usedVariables);
      if (!passIn.contains(arrayVar)) {
        passIn.add(arrayVar);
      }
      startAsync(procName, Arrays.asList(arrayVar), passIn,
                  keepOpenVars, false);
    }

    boolean haveKeys = loopCountVar != null;
    String contentsVar = TCLTMP_ARRAY_CONTENTS;

    if (splitDegree <= 0) {
      pointStack.peek().add(Turbine.containerContents(contentsVar,
                          varToExpr(arrayVar), haveKeys));
    } else {
      // load array size
      pointStack.peek().add(Turbine.containerSize(TCLTMP_CONTAINER_SIZE,
                                        varToExpr(arrayVar)));
      Expression lastIndex = Square.arithExpr(new Value(TCLTMP_CONTAINER_SIZE),
            new Token("-"), new LiteralInt(1));

      // recursively split the range
      ArrayList<Var> splitUsedVars = new ArrayList<Var>(
          usedVariables);
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
    startForeachInner(new Value(contentsVar), memberVar, loopCountVar,
        true, usedVariables, keepOpenVars, foreach_num);
  }

  private void startForeachInner(Value arrayContents,
      Var memberVar, Var loopCountVar,
      boolean isSync, List<Var> usedVariables,
      List<Var> keepOpenVars, int foreach_num) {
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

    if (!isSync) {
      // pass in the array member and loop count var along with other used vars
      ArrayList<Var> loopUsedVars = new ArrayList<Var>(usedVariables);
      loopUsedVars.add(memberVar);
      if (loopCountVar != null) loopUsedVars.add(loopCountVar);
      startAsync("foreach:" + foreach_num + ":body",
          new ArrayList<Var>(), loopUsedVars, keepOpenVars,
          true);
    }
  }


  @Override
  public void endForeachLoop(int splitDegree,
          boolean arrayClosed, List<Var> usedVars, List<Var> keepOpenVars) {
    assert(pointStack.size() >= 2);
    pointStack.pop(); // tclloop body
    if (splitDegree > 0) {
      endRangeSplit();
    }
    if (!arrayClosed) {
      endAsync(keepOpenVars); // outer wait for container
    }
  }

  @Override
  public void startRangeLoop(String loopName, Var loopVar, Var countVar,
      Arg start, Arg end, Arg increment, List<Var> usedVariables,
      List<Var> keepOpenVars, int desiredUnroll, int splitDegree) {
    assert(start.isIntVal() ||
        (start.isVar() &&
            start.getVar().type().equals(Types.V_INT)));
    assert(end.isIntVal() ||
        (end.isVar() &&
            end.getVar().type().equals(Types.V_INT)));
    assert(increment.isIntVal() ||
        (increment.isVar() &&
                    increment.getVar().type().equals(Types.V_INT)));
    assert(loopVar.type().equals(Types.V_INT));
    Expression startE = opargToExpr(start);
    Expression endE = opargToExpr(end);
    Expression incrE = opargToExpr(increment);

    if (splitDegree > 0) {
      startRangeSplit(loopName, usedVariables,
              keepOpenVars, splitDegree, startE, endE, incrE);
      startRangeLoopInner(loopName, loopVar, true, usedVariables,
              keepOpenVars, TCLTMP_RANGE_LO_V, TCLTMP_RANGE_HI_V,
                                                      TCLTMP_RANGE_INC_V);
    } else {
      startRangeLoopInner(loopName, loopVar, true, usedVariables,
              keepOpenVars, startE, endE, incrE);
    }
  }

  @Override
  public void endRangeLoop(List<Var> usedVars, List<Var> keepOpenVars,
                        int splitDegree) {
    assert(pointStack.size() >= 2);
    pointStack.pop(); // for loop body

    if (splitDegree > 0) {
      endRangeSplit();
    }
  }

  private void startRangeLoopInner(String loopName, Var loopVar,
          boolean isSync, List<Var> usedVariables,
          List<Var> keepOpenVars, Expression startE,
          Expression endE, Expression incrE) {
    Sequence loopBody = new Sequence();
    String loopVarName = prefixVar(loopVar.name());
    ForLoop tclLoop = new ForLoop(loopVarName, startE, endE, incrE, loopBody);
    pointStack.peek().add(tclLoop);
    pointStack.push(loopBody);


    ArrayList<Var> loopUsedVars = new ArrayList<Var>(usedVariables);
    loopUsedVars.add(loopVar);
    if (!isSync) {
      startAsync(loopName + ":body",
          new ArrayList<Var>(), loopUsedVars, keepOpenVars,
          true);
    }
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
    // Create two procedures that will be called: an outer procedure
    //  that recursively breaks up the foreach loop into chunks,
    //  and an inner procedure that actually runs the loop
    ArrayList<String> args = new ArrayList<String>();
    args.add(Turbine.LOCAL_STACK_NAME);
    for (Var uv: usedVariables) {
      args.add(prefixVar(uv.name()));
    }
    args.add(TCLTMP_RANGE_LO);
    args.add(TCLTMP_RANGE_HI);
    args.add(TCLTMP_RANGE_INC);

    Value loVal = new Value(TCLTMP_RANGE_LO);
    Value hiVal = new Value(TCLTMP_RANGE_HI);
    Value incVal = new Value(TCLTMP_RANGE_INC);

    List<Expression> commonArgs = new ArrayList<Expression>();
    commonArgs.add(new Value(Turbine.LOCAL_STACK_NAME));
    for (Var uv: usedVariables) {
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
    String outerProcName = loopName + ":outer";
    tree.add(new Proc(outerProcName,
            usedTclFunctionNames, args, outer));

    Sequence inner = new Sequence();
    String innerProcName = loopName + ":inner";
    tree.add(new Proc(innerProcName,
          usedTclFunctionNames, args, inner));

    // Call outer directly
    pointStack.peek().add(new Command(outerProcName,
            outerCallArgs));


    String itersLeft = "tcltmp:itersleft";
    // itersLeft = ceil( (hi - lo + 1) /(double) inc))
    // ==> itersLeft = ( (hi - lo) / inc ) + 1
    outer.add(new SetVariable(itersLeft, Square.arithExpr( new Token(
            String.format("((${%s} - ${%s}) / ${%s}) + 1",
                   TCLTMP_RANGE_HI, TCLTMP_RANGE_LO, TCLTMP_RANGE_INC)))));
    Expression doneSplitting = Square.arithExpr(
            new Value(itersLeft), new Token("<="),
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
          String.format("max(%d, ((${%s} - 1) / %d ) + 1)",
                  splitDegree, itersLeft, splitDegree)))));

    ForLoop splitLoop = new ForLoop(splitStart, loVal,
            hiVal, new Value(skip), splitBody);
    elseB.add(splitLoop);


    ArrayList<Expression> outerRecCall = new
                     ArrayList<Expression>();
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
                    new TclList(outerRecCall), true));

    pointStack.push(inner);
  }

  private void endRangeSplit() {
    pointStack.pop(); // inner proc body
  }

  @Override
  public void addGlobal(String name, Arg val) {
    String tclName = prefixVar(name);
    globInit.add(Turbine.makeTCLGlobal(tclName));
    String typePrefix;
    Expression expr;
    Command setCmd;
    switch (val.getKind()) {
    case INTVAL:
      typePrefix = Turbine.INTEGER_TYPENAME;
      expr = new LiteralInt(val.getIntLit());
      setCmd = Turbine.integerSet(tclName, expr);
      break;
    case FLOATVAL:
      typePrefix = Turbine.FLOAT_TYPENAME;
      expr = new LiteralFloat(val.getFloatLit());
      setCmd = Turbine.floatSet(tclName, expr);
      break;
    case STRINGVAL:
      typePrefix = Turbine.STRING_TYPENAME;
      expr = new TclString(val.getStringLit(), true);
      setCmd = Turbine.stringSet(tclName, expr);
      break;
    case BOOLVAL:
      typePrefix = Turbine.INTEGER_TYPENAME;
      expr = new LiteralInt(val.getBoolLit() ? 1 : 0);
      setCmd = Turbine.integerSet(tclName, expr);
      break;
    default:
      throw new STCRuntimeError("Non-constant oparg type "
          + val.getKind());
    }
    globInit.add(Turbine.allocate(tclName, typePrefix));
    globInit.add(setCmd);
  }

  /**
     Generate and return Tcl from our internal TclTree
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


    private Value varToExpr(Var v) {
    return new Value(prefixVar(v.name()));
  }

  private Expression opargToExpr(Arg in) {
    switch (in.getKind()) {
    case INTVAL:
      return new LiteralInt(in.getIntLit());
    case BOOLVAL:
      return new LiteralInt(in.getBoolLit() ? 1 : 0);
    case STRINGVAL:
      return new TclString(in.getStringLit(), true);
    case VAR:
      return new Value(prefixVar(in.getVar().name()));
    case FLOATVAL:
      return new LiteralFloat(in.getFloatLit());
    default:
      throw new STCRuntimeError("Unknown oparg type: "
          + in.getKind().toString());
    }
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

    @Override
    public void optimize() {
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


      // Keep containers open
      for (Var v: keepOpenVars) {
        pointStack.peek().add(Turbine.containerSlotCreate(varToExpr(v)));
      }

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
      pointStack.peek().add(Turbine.loopRule(loopName,
          nextIterArgs, blockingVals));
    }

    @Override
    public void loopBreak(List<Var> usedVars, List<Var> containersToClose) {
      for (Var arr: containersToClose) {
        pointStack.peek().add(
             Turbine.containerSlotDrop(varToExpr(arr)));
      }
    }

    @Override
    public void endLoop() {
      assert(pointStack.size() >= 2);
      assert(loopNameStack.size() > 0);
      pointStack.pop();
      loopNameStack.pop();
    }

    @Override
    public void dereferenceFile(Var dst, Var src) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void
        runExternal(String cmd, List<Arg> args,
            List<Var> outFiles, Redirects<Arg> redirects,
            boolean hasSideEffects, boolean deterministic) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void getFileName(Var filename, Var file, boolean initUnmapped) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void assignBlob(Var target, Arg src) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void retrieveBlob(Var target, Var src) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void decrBlobRef(Var blob) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void freeBlob(Var blobval) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void assignVoid(Var target, Arg src) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void retrieveVoid(Var target, Var source) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void decrRef(Var var) {
      // TODO Auto-generated method stub
      
    }

    @Override
    public void requirePackage(String pkg, String version) {
      // TODO Auto-generated method stub
      
    }
}
