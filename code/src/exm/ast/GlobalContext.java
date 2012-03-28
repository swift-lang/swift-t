
package exm.ast;

import java.util.*;

import org.apache.log4j.Logger;

import exm.ast.Types.FunctionType;
import exm.ast.Types.SwiftType;
import exm.ast.Variable.DefType;
import exm.ast.Variable.VariableStorage;
import exm.parser.util.*;

public class GlobalContext
extends Context
{
  /**
     Map from composite function name to function information
   */
  Map<String, FunctionType> composites = new HashMap<String, FunctionType>();

  /**
   * Which composites should be called synchronously  
   */
  Set<String> syncComposites = new HashSet<String>();
  
  /**
     Map from app function name to function information
   */
  Map<String, FunctionType> apps = new HashMap<String, FunctionType>();

  /**
   * Map from type name to the type object
   */
  Map<String, SwiftType> types = new HashMap<String, SwiftType>();
  
  /**
     The name of the original SwiftScript file
   */
  String inputFile = null;

  public GlobalContext(String inputFile, Logger logger)
  {
    this(inputFile);
    this.logger = logger;
  }

  GlobalContext(String inputFile)
  {
    this.inputFile = inputFile;
    // Add all predefined types into type name dict
    types.putAll(Builtins.getNativeTypes());
  }

  @Override
  public SwiftType getType(String name)
      throws UndefinedVariableException
  {
    Variable variable = variables.get(name);
    if (variable == null)
      throw new UndefinedVariableException(this, "undefined: " + name);
    return variable.getType();
  }

  @Override
  public void setInputFile(String file)
  {
    inputFile = file;
  }

  @Override
  public String getInputFile()
  {
    return inputFile;
  }

  @Override
  public boolean isAppFunction(String name)
  {
    return apps.containsKey(name);
  }

  @Override
  public boolean isBuiltinFunction(String name)
  {
    return Builtins.exists(name);
  }

  @Override
  public boolean isCompositeFunction(String name)
  {
    if (composites.containsKey(name))
      return true;
    return false;
  }
  
  @Override
  public boolean isSyncComposite(String name) {
    return syncComposites.contains(name);
  }

  @Override
  public FunctionType lookupFunction(String name) {
    FunctionType res;
    if ((res = composites.get(name)) != null) {
      return res;
    }
    else if ((res = apps.get(name)) != null) {
      return res;
    }
    else if ((res = Builtins.getBuiltinType(name)) != null) {
      return res;
    }
    else {
     return null;
    }
  }

  @Override
  public int getLevel()
  {
    return 0;
  }

  @Override
  public void defineCompositeFunction(String name, FunctionType ft,
        boolean async) throws DoubleDefineException {
    if (isFunction(name))
      throw new DoubleDefineException
      (this, "function: " + name + " is already defined");
    composites.put(name, ft);
    if (!async) {
      syncComposites.add(name);
    }
  }

  @Override
  public void defineAppFunction(String name, FunctionType ft)
              throws DoubleDefineException {
    if (isFunction(name)) {
      throw new DoubleDefineException(this, "Function called " + name +
                                      " already defined");
    }
    apps.put(name, ft);
  }
  
  /**
     Declare a global variable
   * @throws DoubleDefineException 
   */
  @Override
  public Variable declareVariable(SwiftType type, String name,
                       VariableStorage scope, DefType defType, String mapping)
                           throws DoubleDefineException
  {
    assert(defType == DefType.GLOBAL_CONST);
    assert(scope == VariableStorage.GLOBAL_CONST);
    if (variables.containsKey(name)) {
      throw new DoubleDefineException(this, "Variable called " + 
                name + " declared twice in global scope");
    }
    Variable v = new Variable(type, name, scope, defType, mapping);
    variables.put(name, v);
    return v;
  }

  @Override
  public Variable createIntermediateVariable(SwiftType type) {
	  throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Variable createLocalTmpVariable(SwiftType type) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Variable createAliasVariable(SwiftType type) throws UserException {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Variable createLocalValueVariable(SwiftType type, String varName)
      throws UserException {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public GlobalContext getGlobals()
  {
    return this;
  }

  @Override
  public Variable getDeclaredVariable(String variable)
  {
    return variables.get(variable);
  }

  @Override
  public Map<String, VisibleVariable> getVisibleVariables()
  {
    Map<String, VisibleVariable> result =
      new HashMap<String, VisibleVariable>(variables.size());
    for (Variable var: variables.values()) {
      result.put(var.getName(), new VisibleVariable(0, var));
    }
    return result;
  }

  @Override
  public SwiftType lookupType(String typeName) {
    return types.get(typeName);
  }

  @Override
  public void defineType(String typeName, SwiftType newType)
          throws DoubleDefineException {
    if (types.get(typeName) != null) {
      throw new DoubleDefineException(this, "Type name " + typeName +
          " is already in used");
    } else {
      types.put(typeName, newType);
    }
  }

  @Override
  public Map<String, SwiftType> getCurrentTypeMapping() {
    return Collections.unmodifiableMap(types);
  }

  @Override
  protected Variable createStructFieldTmp(Variable struct, SwiftType fieldType,
      String fieldPath, VariableStorage storage) {
    throw new UnsupportedOperationException("not yet implemented");
  }
  
  @Override
  public Collection<Variable> getCachedStructFields() {
    throw new UnsupportedOperationException("Not yet implemented");
  }
  
  @Override
  public Variable getStructFieldTmp(Variable struct, List<String> fieldPath) {
    throw new UnsupportedOperationException("not yet implemented"); 
  }

  @Override
  public FunctionContext getFunctionContext() {
    return null;
  }

  @Override
  public void flagArrayForClosing(Variable var) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public List<Variable> getArraysToClose() {
    throw new UnsupportedOperationException("Not yet implemented");
  }

}
