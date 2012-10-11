
package exm.stc.frontend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;

/**
 * Track context within a function.  New child contexts are created
 * for every new variable scope.
 *
 */
public class LocalContext extends Context {
  private Context parent = null;
  private GlobalContext globals = null;
  
  private final FunctionContext functionContext;

  public LocalContext(Context parent) {
    this(parent, null);
  }

  public LocalContext(Context parent, String functionName)
  {
    this.functionContext = functionName != null ?
          new FunctionContext(functionName) : null;

    this.parent = parent;
    this.level = parent.level + 1;
    this.logger = parent.getLogger();
    this.globals = parent.getGlobals();
    nested = true;
    line = parent.line;
  }
  
  @Override
  public Var getDeclaredVariable(String name)
  {
    Var result;
    result = variables.get(name);
    if (result != null)
      return result;
    return parent.getDeclaredVariable(name);
  }

  @Override
  public Var declareVariable(Type type, String name, VarStorage scope,
      DefType defType, Var mapping)
  throws UserException
  {
    logger.trace("context: declareVariable: " +
                 type.toString() + " " + name + "<" + scope.toString() + ">"
                 + "<" + defType.toString() + ">");

    Var variable = new Var(type, name, scope, defType, mapping);
    declareVariable(variable);
    return variable;
  }

  @Override
  public Var createTmpVar(Type type, boolean storeInStack) 
                                                      throws UserException {
      String name;
      do {
        int counter = getFunctionContext().getCounterVal("intermediate_var");
        name = Var.TMP_VAR_PREFIX + counter;
      } while (lookupDef(name) != null); // In case variable name in use

      VarStorage storage = storeInStack ? 
                  VarStorage.STACK : VarStorage.TEMP;
      return declareVariable(type, name, storage, DefType.LOCAL_COMPILER, null);
  }

  @Override
  public Var createAliasVariable(Type type) throws UserException {
    String name;
    do {
      int counter = getFunctionContext().getCounterVal("alias_var");
      name = Var.ALIAS_VAR_PREFIX + counter;
    } while (lookupDef(name) != null);

    Var v =  new Var(type, name, VarStorage.ALIAS,
                                          DefType.LOCAL_COMPILER);
    variables.put(name, v);
    return v;
  }

  /**
   *
   * @param type
   * @param varName the name of the variable this is the value of:
   *    try and work this into the generated name. Can be left as
   *    null
   * @return
   * @throws UserException
   */
  @Override
  public Var createLocalValueVariable(Type type, String varName)
      throws UserException {
    String name = chooseVariableName(Var.LOCAL_VALUE_VAR_PREFIX, varName,
                                    "value_var");
    Var v =  new Var(type, name, VarStorage.LOCAL,
                                          DefType.LOCAL_COMPILER);
    variables.put(name, v);
    return v;
  }

  /**
   * Helper to choose variable name.  
   * @param prefix Prefix that must be at start
   * @param preferredSuffix Preferred suffix
   * @param counterName name of counter to use to make unique if needed
   * @return
   */
  private String chooseVariableName(String prefix, String preferredSuffix,
      String counterName) {
    if (preferredSuffix != null) {
      prefix += preferredSuffix;
      // see if we can give it a nice name
      if (lookupDef(prefix) == null) {
        return prefix;
      }
    }

    String name = null;
    do {
      int counter = getFunctionContext().getCounterVal(counterName);
      name = prefix + counter;
    } while (lookupDef(name) != null);
    return name;
  }

  @Override
  public Var createFilenameAliasVariable(String fileVarName) {
    String name = chooseVariableName(Var.FILENAME_OF_PREFIX,
        fileVarName, "filename_of");
    Var v =  new Var(Types.F_STRING, name,
                               VarStorage.ALIAS,
                               DefType.LOCAL_COMPILER);
    variables.put(name, v);
    return v;
  }

  Var declareVariable(Var variable)
  throws DoubleDefineException
  {
    String name = variable.name();
    
    checkNotDefined(name);

    variables.put(name, variable);
    return variable;
  }

  @Override
  public void defineFunction(String name, FunctionType type)
                                    throws DoubleDefineException {
    throw new STCRuntimeError("Cannot define function in local context");
  }

  @Override
  public void setFunctionProperty(String name, FnProp prop) {
    throw new STCRuntimeError("Cannot define function in local context");
  }

  @Override
  public boolean hasFunctionProp(String name, FnProp prop) {
    return parent.hasFunctionProp(name, prop);
  }

  @Override
  public GlobalContext getGlobals()
  {
    return globals;
  }

  @Override
  public List<Var> getVisibleVariables() {
    List<Var> result = new ArrayList<Var>();

    // All variable from parent visible, plus variables defined in this scope
    result.addAll(parent.getVisibleVariables());
    result.addAll(variables.values());

    return result;
  }

  public void addDeclaredVariables(List<Var> variables)
  throws DoubleDefineException
  {
    for (Var v : variables)
      declareVariable(v);
  }

  @Override
  public void setInputFile(String file) {
    globals.setInputFile(file);
  }

  @Override
  public String getInputFile() {
    return globals.getInputFile();
  }

  @Override
  public String toString()
  {
    return getVisibleVariables().toString();
  }

  @Override
  public Type lookupType(String typeName) {
    Type t = types.get(typeName);
    if (t != null) {
      return t;
    } else {
      return parent.lookupType(typeName);
    }
  }

  @Override
  public void defineType(String typeName, Type newType)
    throws DoubleDefineException {
    checkNotDefined(typeName);
    types.put(typeName, newType);
  }

  /**
   * Called when we want to create a new alias for a structure filed
   */
  @Override
  protected Var createStructFieldTmp(Var struct,
      Type fieldType, String fieldPath,
      VarStorage storage) {
    // Should be unique in context
    String basename = Var.STRUCT_FIELD_VAR_PREFIX
        + struct.name() + "_" + fieldPath.replace('.', '_');
    String name = basename;
    int counter = 1;
    while (lookupDef(name) != null) {
      name = basename + "-" + counter;
      counter++;
    }
    Var tmp = new Var(fieldType, name, storage, DefType.LOCAL_COMPILER);
    variables.put(tmp.name(), tmp);
    return tmp;
  }
  
  @Override
  public FunctionContext getFunctionContext() {
    if (this.functionContext != null) {
      return this.functionContext;
    } else {
      return parent.getFunctionContext();
    }
  }
  
  private final ArrayList<Var> arraysToClose = 
        new ArrayList<Var>();
  
  @Override
  public void flagArrayForClosing(Var var) {
    assert(Types.isArray(var.type()));
    arraysToClose.add(var);
  }
  
  @Override
  public List<Var> getArraysToClose() {
    return Collections.unmodifiableList(arraysToClose);
  }
}
