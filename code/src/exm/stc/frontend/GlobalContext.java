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
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
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
    declareVariable(type, name, VarStorage.GLOBAL_CONST,
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
  
  @Override
  public List<FnProp> getFunctionProps(String function) {
    List<FnProp> props = new ArrayList<FnProp>();
    for (Pair<String, FnProp> pair: functionProps) {
      if (pair.val1.equals(function)) {
        props.add(pair.val2);
      }
    }
    return props;
  }
  
  /**
     Declare a global variable
   * @throws DoubleDefineException 
   */
  @Override
  public Var declareVariable(Type type, String name,
                       VarStorage scope, DefType defType, Var mapping)
                           throws DoubleDefineException
  {
    assert(defType == DefType.GLOBAL_CONST);
    assert(scope == VarStorage.GLOBAL_CONST);
    if (variables.containsKey(name)) {
      throw new DoubleDefineException(this, "Variable called " + 
                name + " declared twice in global scope");
    }
    Var v = new Var(type, name, scope, defType, mapping);
    variables.put(name, v);
    return v;
  }

  @Override
  public Var createTmpVar(Type type, boolean storeInStack) {
      throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Var createAliasVariable(Type type) throws UserException {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Var createLocalValueVariable(Type type, String varName)
      throws UserException {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Var createFilenameAliasVariable(String name) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public GlobalContext getGlobals()
  {
    return this;
  }

  @Override
  public Var lookupVarUnsafe(String variable)
  {
    return variables.get(variable);
  }

  @Override
  public List<Var> getVisibleVariables() {
    return new ArrayList<Var>(variables.values());
  }

  @Override
  public Type lookupTypeUnsafe(String typeName) {
    return types.get(typeName);
  }

  @Override
  public void defineType(String typeName, Type newType)
          throws DoubleDefineException {
    if (types.get(typeName) != null) {
      throw new DoubleDefineException(this, "Type name " + typeName +
          " is already in used");
    } else {
      types.put(typeName, newType);
    }
  }

  @Override
  protected Var createStructFieldTmp(Var struct, Type fieldType,
      String fieldPath, VarStorage storage) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public FunctionContext getFunctionContext() {
    return null;
  }
}
