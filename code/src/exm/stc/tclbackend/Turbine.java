
package exm.stc.tclbackend;

import java.util.*;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.tclbackend.tree.*;

/**
 * Automates creation of Turbine-specific Tcl constructs
 * @author wozniak
 *
 * This class is package-private: only TurbineGenerator uses it
 * */
class Turbine
{
  /* Names of types used by Turbine */
  public static final String STRING_TYPENAME = "string";
  public static final String INTEGER_TYPENAME = "integer";
  public static final String VOID_TYPENAME = "void";
  public static final String FLOAT_TYPENAME = "float";
  public static final String BLOB_TYPENAME = "blob";

  // Commonly used things:
  private static final Token ALLOCATE_CONTAINER =
      new Token("turbine::allocate_container");
  private static final Token ALLOCATE_FILE = 
          new Token("turbine::allocate_file2");
  private static final Token CONTAINER_INSERT =
      new Token("turbine::container_insert");
  private static final Token CONTAINER_F_INSERT =
      new Token("turbine::container_f_insert");
  private static final Token CONTAINER_F_REFERENCE =
      new Token("turbine::f_reference");
  private static final Token CONTAINER_REFERENCE =
      new Token("adlb::container_reference");
  private static final Token CONTAINER_IMMEDIATE_INSERT =
      new Token("turbine::container_immediate_insert");
  private static final Token CREF_F_LOOKUP =
      new Token("turbine::f_cref_lookup");
  private static final TclTree CREF_LOOKUP_LITERAL =
      new Token("turbine::f_cref_lookup_literal");
  private static final Token F_CONTAINER_CREATE_NESTED =
      new Token("turbine::f_container_create_nested");
  private static final Token F_CREF_CREATE_NESTED =
      new Token("turbine::f_cref_create_nested");
  private static final Token F_CONTAINER_CREATE_NESTED_STATIC =
      new Token("turbine::container_create_nested");
  private static final Token F_CREF_CREATE_NESTED_STATIC =
      new Token("turbine::cref_create_nested");
  private static final Token CONTAINER_SLOT_DROP =
      new Token("adlb::slot_drop");
  private static final Token CONTAINER_SLOT_CREATE =
      new Token("adlb::slot_create");
  private static final Token CONTAINER_ENUMERATE =
      new Token("adlb::enumerate");
  private static final Token RETRIEVE_UNTYPED =
      new Token("turbine::retrieve");
  private static final Token RETRIEVE_INTEGER =
      new Token("turbine::retrieve_integer");
  private static final Token RETRIEVE_FLOAT =
      new Token("turbine::retrieve_float");
  private static final Token RETRIEVE_STRING =
      new Token("turbine::retrieve_string");
  private static final Token STACK_LOOKUP =
      new Token("turbine::stack_lookup");
  static final String LOCAL_STACK_NAME = "stack";
  static final String PARENT_STACK_NAME = "stack";
  private static final Value STACK = new Value(LOCAL_STACK_NAME);
  private static final Value PARENT_STACK =
      new Value(PARENT_STACK_NAME);
  private static final Token PARENT_STACK_ENTRY =
      new Token("_parent");
  private static final Token RULE = new Token("turbine::c::rule");
  private static final Token NO_STACK = new Token("no_stack");
  private static final Token DEREFERENCE_INTEGER =
      new Token("turbine::f_dereference_integer");
  private static final Token DEREFERENCE_FLOAT =
      new Token("turbine::f_dereference_float");
  private static final Token DEREFERENCE_STRING =
      new Token("turbine::f_dereference_string");
  private static final Token DEREFERENCE_FILE =
          new Token("turbine::f_dereference_file");
  private static final Token TURBINE_LOG =
      new Token("turbine::c::log");
  private static final Token ALLOCATE = new Token("turbine::allocate");
  private static final Token DIVIDE_INTEGER =
    new Token("turbine::divide_integer_impl");
  private static final Token MOD_INTEGER =
      new Token("turbine::mod_integer_impl");
  
  static final Token CONTAINER_LOOKUP =
      new Token("turbine::container_lookup");
  static final Token CONTAINER_LOOKUP_CHECKED =
      new Token("turbine::container_lookup_checked");
  private static final Token STORE_INTEGER =
      new Token("turbine::store_integer");
  private static final Token STORE_FLOAT =
      new Token("turbine::store_float");
  private static final Token STORE_STRING =
      new Token("turbine::store_string");
  private static final Token CONTAINER_DEREF_INSERT =
      new Token("turbine::container_deref_insert");
  private static final Token CONTAINER_F_DEREF_INSERT =
      new Token("turbine::container_f_deref_insert");
  
  private static final Token CALL_COMPOSITE =
      new Token("turbine::call_composite");

  private static final Token UNCACHED_MODE = new Token("UNCACHED");

  public enum StackFrameType {
    MAIN,
    COMPOSITE,
    NESTED
  }

  /**
   * Used to specify what caching is allowed for retrieve
   * @author tga
   */
  public enum CacheMode {
    CACHED,
    UNCACHED
  }
  
  public Turbine()
  {}

  public static TclTree[] createStackFrame(StackFrameType type)
  {
    TclTree[] result;
    // Index into result
    int index = 0;

    if (type == StackFrameType.MAIN)
      result = new TclTree[1];
    else
      result = new TclTree[3];


    if (type == StackFrameType.NESTED || type == StackFrameType.COMPOSITE) {
      // Make sure that there is a variable in scope called parent
      // (parent is passed in as an argument for composites)
      result[index++] = new SetVariable("parent", STACK);
    }

    result[index++] = allocateContainer(LOCAL_STACK_NAME, STRING_TYPENAME);

    if (type != StackFrameType.MAIN) {
      // main is the only procedure without a parent stack frame
      result[index++] = new Command(CONTAINER_INSERT, STACK,
                                    PARENT_STACK_ENTRY, PARENT_STACK);
    }
    return result;
  }

  public static TclTree createDummyStackFrame() {
    return new SetVariable(LOCAL_STACK_NAME, new LiteralInt(0));
  }


  public static Command storeInStack(String stackVarName, String tclVarName)
  {
    Token name = new Token(stackVarName);
    Value value = new Value(tclVarName);
    Command result =
      new Command(CONTAINER_INSERT, STACK, name, value);
    return result;
  }


  public static TclTree allocate(String tclName, String typePrefix) {
    return new Command(ALLOCATE,
                       new Token(tclName), new Token(typePrefix));
  }

  public static TclTree allocateContainer(String name,
                                          String indexType) {
    return new Command(ALLOCATE_CONTAINER,
                       new Token(name), new Token(indexType));
  }
  
  public static TclTree allocateFile(Value mapVar, String tclName) {
    if (mapVar != null) {
      return new Command(ALLOCATE_FILE,
            new Token(tclName), mapVar);
    } else {
      return new Command(ALLOCATE_FILE,
              new Token(tclName));
    }
  }

  public static SetVariable stackLookup(String stackName,
      String tclVarName, String containerVarName) {
    Token v = new Token(containerVarName);
    Square square = new Square(STACK_LOOKUP,
            new Value(stackName), v);
    SetVariable sv = new SetVariable(tclVarName, square);
    return sv;
  }

  public static SetVariable lookupParentStack(String parentScope,
      String childScope) {
    Square square = new Square(STACK_LOOKUP,
            new Value(childScope),
            PARENT_STACK_ENTRY);
    SetVariable sv = new SetVariable(parentScope, square);
    return sv;
  }

  /**
     Do a data get operation to load the value from the TD
   */
  public static SetVariable integerGet(String target, Value variable)
  {
    SetVariable result =
      new SetVariable(target,
                      new Square(RETRIEVE_INTEGER, variable));
    return result;
  }


  public static SetVariable refGet(String target, Value variable)
  {
   SetVariable result =
     new SetVariable(target,
                     new Square(RETRIEVE_UNTYPED, variable));
   return result;
  }

  public static Command stringSet(String turbineDstVar, Expression src) {
    // The TD is a Value
    Value t = new Value(turbineDstVar);
    Command c = new Command(STORE_STRING, t, src);
    return c;
  }

  public static Command integerSet(String turbineDstVar, Expression src) {
    // The TD is a Value
    Value t = new Value(turbineDstVar);
    // The value is a literal Token
    Command c = new Command(STORE_INTEGER, t, src);
    return c;
  }

  public static Command floatSet(String turbineDstVar, Expression src) {
    // The TD is a Value
    Value t = new Value(turbineDstVar);
    // The value is a literal Token
    Command c = new Command(STORE_FLOAT, t, src);
    return c;
  }

  public static SetVariable floatGet(String target, Value variable) {
    return floatGet(target, variable, CacheMode.CACHED);
    
  }
  public static SetVariable floatGet(String target, Value variable,
                                                    CacheMode caching) {
    if (caching == CacheMode.CACHED) {
      return new SetVariable(target,
            new Square(RETRIEVE_FLOAT, variable));
    } else {
      assert(caching == CacheMode.UNCACHED);
      return new SetVariable(target,
              new Square(RETRIEVE_FLOAT, variable, UNCACHED_MODE));
    }
  }

  /**
   * Do a data get operation to load the value from the TD
   */
  public static SetVariable stringGet(String target, Value variable)
  {
    SetVariable result =
      new SetVariable(target,
                     new Square(RETRIEVE_STRING, variable));
    return result;
  }

  static enum RuleType {
    LOCAL,
    CONTROL,
    WORK
  }

  private static Value tclRuleType (RuleType t) {
    switch (t) {
    case LOCAL:
      return new Value("turbine::LOCAL");
    case CONTROL:
      return new Value("turbine::CONTROL");
    case WORK:
      return new Value("turbine::WORK");
    default:
      throw new STCRuntimeError("Unexpected rule type: " + t);
    }
  }


  /**
   * Generate code for a rule
   * @param symbol
   * @param inputs
   * @param action the action, using a tcl list to ensure proper escaping
   * @param type
   * @return
   */
  private static Sequence ruleHelper(String symbol, List<Value> inputs,
      TclList action, RuleType type) {
    Sequence result = new Sequence();

    Token s = new Token(symbol);
    TclList i = new TclList(inputs);

    Command r = new Command(RULE, s, i, tclRuleType(type), action);
    result.add(r);

    return result;
  }

  /**
   * Same as rule, but store the rule ID into the TCL variable named by
   * ruleIDVarName so that it can be provided as an argument to the procedure
   * @param procName
   * @param inputs
   * @param action
   * @param shareWork
   * @return
   */
  public static Sequence rule(String symbol,
      List<Value> inputs, TclList action, boolean shareWork) {
    RuleType ruleType = shareWork ? RuleType.CONTROL : RuleType.LOCAL;
    return ruleHelper(symbol, inputs, action, ruleType);
  }

  public static Sequence loopRule(String symbol,
      List<Value> args, List<Value> blockOn) {
    List<Expression> actionElems = new ArrayList<Expression>();
    actionElems.add(new Token(symbol));
    for (Value arg: args) {
      actionElems.add(arg);
    }
    TclList action = new TclList(actionElems);
    return ruleHelper(symbol, blockOn, action, RuleType.CONTROL);
  }


  


  public static TclTree allocateStruct(String tclName) {
    Square createExpr = new Square(new Token("dict"), new Token("create"));
    return new SetVariable(tclName, createExpr);
  }
  
  /**
   * Insert src into struct at container.field
   * @param container
   * @param field
   * @param src
   */
  public static Sequence structInsert(String container, String field,
                    String src) {
    Sequence result = new Sequence();
    Command storeCmd = new Command(
        new Token("dict"), new Token("set"), 
        new Token(container), new TclString(field, true), new Value(src));
  
    result.add(storeCmd);
    return result;
  
  }

  public static Sequence structLookupFieldID(String structName, String structField,
      String resultVar) {
    Sequence result = new Sequence();

    Square containerGet = new Square(new Token("dict"), new Token("get"),
        new Value(structName), new TclString(structField, true));

    SetVariable loadCmd = new SetVariable(resultVar, containerGet);
    result.add(loadCmd);
    return result;
  }


  public static Command structRefLookupFieldID(String structName, String structField,
      String resultVar, String resultTypeName) { 
    Command lookup = new Command(new Token("turbine::struct_ref_lookup"),
            new Value(structName), new TclString(structField, true),
            new Value(resultVar), new TclString(resultTypeName, true));

    return lookup;
  }


  /**
   * Put reference to arrayVar[arrayIndex] into refVar once it is ready
   * @param refVar
   * @param arrayIndex
   * @param isArrayRef
   * @return
   */
  public static Sequence arrayLookupImmIx(String refVar, 
      boolean refIsString, String arrayVar,
      Expression arrayIndex, boolean isArrayRef) {
    Sequence result = new Sequence();
    
    Token refType = refIsString ?  new Token(STRING_TYPENAME) 
                                : new Token(INTEGER_TYPENAME); 
    
    // set up reference to point to array data
    Command loadCmd;
    if (isArrayRef) {
      loadCmd = new Command(CREF_LOOKUP_LITERAL, NO_STACK,
          new TclList(),  new TclList(new Value(arrayVar),
          arrayIndex, new Value(refVar), refType));
    } else {
      loadCmd = new Command(CONTAINER_REFERENCE, new Value(arrayVar),
        arrayIndex, new Value(refVar), refType);
    }
    result.add(loadCmd);

    return result;
  }

  /**
   * Lookup arrayVar[arrayIndex] right away, regardless of whether
   * it is closed
   * @param refVar
   * @param arrayIndex
   * @param isArrayRef
   * @return
   */
  public static SetVariable arrayLookupImm(String dst, String arrayVar,
      Expression arrayIndex) {
    return new SetVariable(dst,
        new Square(CONTAINER_LOOKUP_CHECKED, new Value(arrayVar), arrayIndex));
  }

  /**
   * Put a reference to arrayVar[indexVar] into refVar
   * @param refVar
   * @param arrayVar
   * @param indexVar
   * @param isArrayRef
   * @return
   */
  public static Sequence arrayLookupComputed(String refVar,
                          boolean refIsString,
                          String arrayVar,
                          String indexVar, boolean isArrayRef) {

    Sequence result = new Sequence();

    Command loadCmd;
    
    Token refType = refIsString ?  new Token(STRING_TYPENAME) 
                                : new Token(INTEGER_TYPENAME); 
    
    if (isArrayRef) {
      loadCmd = new Command(CREF_F_LOOKUP, NO_STACK,
          new TclList(), new TclList(new Value(arrayVar), new Value(indexVar),
              new Value(refVar), refType));
    } else {
      loadCmd = new Command(CONTAINER_F_REFERENCE, NO_STACK,
         new TclList(), new TclList(new Value(arrayVar), new Value(indexVar),
             new Value(refVar), refType));
    }

    result.add(loadCmd);
    return result;
   }

   public static Sequence dereferenceInteger(String dstVar, String refVar) {
     Sequence result = new Sequence();
     Command deref = new Command(DEREFERENCE_INTEGER, NO_STACK,
         new Value(dstVar), new Value(refVar));
     result.add(deref);
     return result;
   }

   public static Sequence dereferenceFloat(String dstVar, String refVar) {
     Sequence result = new Sequence();
     Command deref = new Command(DEREFERENCE_FLOAT, NO_STACK,
         new Value(dstVar), new Value(refVar));
     result.add(deref);
     return result;
   }

   public static Sequence dereferenceString(String dstVar, String refVar) {
     Sequence result = new Sequence();
     Command deref = new Command(DEREFERENCE_STRING, NO_STACK,
         new Value(dstVar), new Value(refVar));
     result.add(deref);
     return result;
   }
   
   public static Sequence dereferenceFile(String dstVar, String refVar) {
     Sequence result = new Sequence();
     Command deref = new Command(DEREFERENCE_FILE, NO_STACK,
         new Value(dstVar), new Value(refVar));
     result.add(deref);
     return result;
   }

  public static Sequence arrayStoreImmediate(String srcVar, String arrayVar,
                                                  Expression arrayIndex) {
    Sequence result = new Sequence();

    Command storeCmd = new Command(CONTAINER_IMMEDIATE_INSERT,
        new Value(arrayVar), arrayIndex, new Value(srcVar));

    result.add(storeCmd);
    return result;
  }

  public static Sequence arrayDerefStore(String srcRefVar, String arrayVar,
      Expression arrayIndex) {
    Sequence result = new Sequence();

    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar),
                      arrayIndex, new Value(srcRefVar));
    Command storeCmd = new Command(CONTAINER_DEREF_INSERT, NO_STACK, outputs, inputs);

    result.add(storeCmd);
    return result;
  }

  public static Sequence arrayDerefStoreComputed(String srcRefVar, String arrayVar,
      String indexVar) {
    Sequence result = new Sequence();
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar), new Value(indexVar),
    new Value(srcRefVar));
    Command storeCmd = new Command(CONTAINER_F_DEREF_INSERT,
                                           NO_STACK, outputs, inputs);
    result.add(storeCmd);
    return result;
  }

  public static Sequence arrayStoreComputed(String srcVar, String arrayVar,
                                                    String indexVar) {
    Sequence result = new Sequence();
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar), new Value(indexVar),
          new Value(srcVar));
    Command storeCmd = new Command(CONTAINER_F_INSERT, NO_STACK,
                                          outputs, inputs);
    result.add(storeCmd);
    return result;
  }

  public static Sequence arrayRefStoreImmediate(String srcVar, String arrayVar,
      Expression arrayIndex, String outerArray) {
    Sequence result = new Sequence();

    Command storeCmd = new Command(new Token("turbine::cref_insert"),
                    NO_STACK, new TclList(), new TclList(
                    new Value(arrayVar), arrayIndex, new Value(srcVar),
                    new Value(outerArray)));

    result.add(storeCmd);
    return result;
  }


  public static Sequence arrayRefStoreComputed(String srcVar, String arrayVar,
      String indexVar, String outerArray) {
    Sequence result = new Sequence();
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar), new Value(indexVar),
        new Value(srcVar), new Value(outerArray));
    Command storeCmd = new Command(new Token("turbine::f_cref_insert"),
                        NO_STACK, outputs, inputs);
    result.add(storeCmd);
    return result;
  }

  public static Sequence arrayRefDerefStore(String srcRefVar, String arrayVar,
      Expression arrayIndex, String outerArrayVar) {
    Sequence result = new Sequence();

    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar),
        arrayIndex, new Value(srcRefVar), new Value(outerArrayVar));
    Command storeCmd = new Command(new Token("turbine::cref_deref_insert"),
                                              NO_STACK, outputs, inputs);

    result.add(storeCmd);
    return result;
  }

  public static Sequence arrayRefDerefStoreComputed(String srcRefVar, String arrayVar,
      String indexVar, String outerArrayVar) {
    Sequence result = new Sequence();
    Square outputs = new TclList();
    Square inputs =  new TclList(new Value(arrayVar), new Value(indexVar),
        new Value(srcRefVar), new Value(outerArrayVar));
    Command storeCmd = new Command(new Token("turbine::cref_f_deref_insert"),
                                    NO_STACK, outputs, inputs);
    result.add(storeCmd);
    return result;
  }

  public static TclTree containerCreateNested(String resultVar,
        String containerVar, String indexVar) {
    return new Command(F_CONTAINER_CREATE_NESTED,
            new Token(resultVar), new Value(containerVar),
            new Value(indexVar), new Token(INTEGER_TYPENAME));
  }

  public static TclTree containerRefCreateNested(String resultVar,
      String containerVar, String indexVar) {
    return new Command(F_CREF_CREATE_NESTED,
          new Token(resultVar), new Value(containerVar),
          new Value(indexVar), new Token(INTEGER_TYPENAME));
  }

  public static TclTree containerRefCreateNestedImmIx(String resultVar,
      String containerVar, Expression arrIx) {
    return new Command(F_CREF_CREATE_NESTED_STATIC,
        new Token(resultVar), new Value(containerVar),
        arrIx, new Token(INTEGER_TYPENAME));
  }

  public static TclTree containerCreateNestedImmIx(String resultVar,
      String containerVar, Expression arrIx) {
    return new SetVariable(resultVar,
        new Square(F_CONTAINER_CREATE_NESTED_STATIC,
            new Value(containerVar), arrIx, new Token(INTEGER_TYPENAME)));
  }

  public static TclTree containerSlotCreate(Value arr) {
    return new Command(CONTAINER_SLOT_CREATE, arr);
  }

  public static Sequence containerSlotDrop(Value arr) {
    Sequence result = new Sequence();
    Command c = new Command(CONTAINER_SLOT_DROP, arr);
    result.add(c);
    return result;
  }

  /**
   * Get entire contents of container
   * @param resultVar
   * @param arr
   * @param includeKeys
   * @return
   */
  public static SetVariable containerContents(String resultVar,
                        Value arr, boolean includeKeys) {
    Token mode = includeKeys ? new Token("dict") : new Token("members");
    return new SetVariable(resultVar, new Square(
            CONTAINER_ENUMERATE, arr, mode, new Token("all"),
                                          new LiteralInt(0)));
  }

  /**
   * Return the size of a container
   * @param resultVar
   * @param arr
   * @return
   */
  public static SetVariable containerSize(String resultVar,
        Value arr) {
    return new SetVariable(resultVar, new Square(
        CONTAINER_ENUMERATE, arr, new Token("count"), new Token("all"),
                                      new LiteralInt(0)));
  }

  /**
   * Retrieve partial contents of container from start to end inclusive
   * start to end are not the logical array indices, but rather physical indices
   * @param resultVar
   * @param arr
   * @param includeKeys
   * @param start
   * @param len
   * @return
   */
  public static SetVariable containerContents(String resultVar,
          Value arr, boolean includeKeys, Expression start, Expression len) {
    Token mode = includeKeys ? new Token("dict") : new Token("members");
    return new SetVariable(resultVar, new Square(
              CONTAINER_ENUMERATE, arr, mode, start, len));
  }

  public static Command turbineLog(String msg) {
    return new Command(TURBINE_LOG, new TclString(msg, true));
  }

  public static Command turbineLog(String... tokens) {
    return new Command(TURBINE_LOG, new TclList(tokens));
  }

  public static TclTree declareReference(String refVarName) {
    return allocate(refVarName, INTEGER_TYPENAME);
  }

  public static TclTree callComposite(String function, TclList oList,
                                      TclList iList, TclList blockOn) {
    return new Command(CALL_COMPOSITE,
        new Value(Turbine.LOCAL_STACK_NAME), new Token(function),
                  oList, iList, blockOn);
  }


  public static TclTree callCompositeSync(String function, TclList oList,
      TclList iList) {
    return new Command(new Token(function), new Value(Turbine.LOCAL_STACK_NAME),
        oList, iList);
  }

  public static Command makeTCLGlobal(String tclName) {
    return new Command(new Token("global"), new Token(tclName));
  }

  public static Square modInteger(Expression a, Expression b) {
    return new Square(new Expression[] {MOD_INTEGER, a, b});
  }

  public static Square divideInteger(Expression a, Expression b) {
    return new Square(new Expression[] {DIVIDE_INTEGER, a, b});
  }

  public static TclTree resetPriority() {
    return new Command("turbine::reset_priority");
  }

  public static TclTree setPriority(Expression priority) {
    return new Command("turbine::set_priority", Arrays.asList(priority));
  }

}
