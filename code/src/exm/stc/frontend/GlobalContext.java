
package exm.stc.frontend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.SwiftType;
import exm.stc.common.lang.Variable;
import exm.stc.common.lang.Variable.DefType;
import exm.stc.common.lang.Variable.VariableStorage;
import exm.stc.common.util.Pair;

/**
 * Global context for entire program
 *
 */
public class GlobalContext extends Context {
  /**
   * Which composites should be called synchronously  
   */
  private Set<Pair<String, FnProp>> functionProps =
                        new HashSet<Pair<String, FnProp>>();
  
  /**
     The name of the original SwiftScript file
   */
  private String inputFile = null;

  public GlobalContext(String inputFile, Logger logger)
  {
    this(inputFile);
    this.logger = logger;
  }

  GlobalContext(String inputFile)
  {
    this.inputFile = inputFile;
    // Add all predefined types into type name dict
    types.putAll(Types.getBuiltInTypes());
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
  public int getLevel()
  {
    return 0;
  }

  @Override
  public void defineFunction(String name, FunctionType type) 
      throws DoubleDefineException {
    checkNotDefined(name);
    declareVariable(type, name, VariableStorage.GLOBAL_CONST,
                    DefType.GLOBAL_CONST, null);
  }
  
  @Override
  public void setFunctionProperty(String name, FnProp prop) {
    functionProps.add(new Pair<String, FnProp>(name, prop));
  }
  
  @Override
  public boolean hasFunctionProp(String name, FnProp prop) {
    return functionProps.contains(new Pair<String, FnProp>(name, prop));
  }
  
  /**
     Declare a global variable
   * @throws DoubleDefineException 
   */
  @Override
  public Variable declareVariable(SwiftType type, String name,
                       VariableStorage scope, DefType defType, Variable mapping)
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
  public Variable createTmpVar(SwiftType type, boolean storeInStack) {
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
  public Variable createFilenameAliasVariable(String name) {
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
  public List<Variable> getVisibleVariables() {
    return new ArrayList<Variable>(variables.values());
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
  protected Variable createStructFieldTmp(Variable struct, SwiftType fieldType,
      String fieldPath, VariableStorage storage) {
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
