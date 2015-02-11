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
import java.util.Collection;
import java.util.List;

import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedExecContextException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Constants;
import exm.stc.common.lang.DefaultVals;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.Intrinsics.IntrinsicFunction;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;

/**
 * Track context within a function.  New child contexts are created
 * for every new variable scope.
 *
 */
public class LocalContext extends Context {
  private final Context parent;
  private final GlobalContext globals;
  private final FunctionContext functionContext;
  private final boolean isTopLevel;

  public LocalContext(Context parent, String functionName, boolean isTopLevel) {
    super(parent.getLogger(), parent.getLevel() + 1);
    this.functionContext = functionName != null ?
          new FunctionContext(functionName) : null;
    this.parent = parent;
    this.globals = parent.getGlobals();
    this.isTopLevel = isTopLevel;
    inputFile = parent.inputFile;
    line = parent.line;
    col = parent.col;
  }

  /**
   * Subcontext of a function
   * @param parent
   * @return
   */
  public static LocalContext fnSubcontext(Context parent) {
    return new LocalContext(parent, null, false);
  }

  /**
   * Context for top level of function body
   * @param global
   * @param functionName
   * @return
   */
  public static LocalContext fnContext(Context global,
                                             String functionName) {
    return new LocalContext(global, functionName, false);
  }

  /**
   * Context for top-level code
   * @param global
   * @return
   */
  public static LocalContext topLevelContext(GlobalContext global) {
    return new LocalContext(global, Constants.ENTRY_FUNCTION, true);
  }

  @Override
  public boolean isTopLevel() {
    return isTopLevel;
  }

  @Override
  public DefInfo lookupDef(String name) {
    DefInfo result = allDefs.get(name);
    if (result != null) {
      return result;
    } else {
      return parent.lookupDef(name);
    }
  }

  @Override
  public Var lookupVarUnsafe(String name) {
    Var result;
    result = variables.get(name);
    if (result != null)
      return result;
    return parent.lookupVarUnsafe(name);
  }

  @Override
  public Var createTmpVar(Type type, boolean storeInStack)
                                                      throws UserException {
    String name = uniqueName(Var.TMP_VAR_PREFIX, null, "intermediate_var");
    Alloc storage = storeInStack ?
                Alloc.STACK : Alloc.TEMP;
    return declareVariable(type, name, storage, DefType.LOCAL_COMPILER,
                           VarProvenance.exprTmp(getSourceLoc()), false);
  }

  @Override
  public Var createTmpAliasVar(Type type) throws UserException {
    String name = uniqueName(Var.ALIAS_VAR_PREFIX, null, "alias_var");

    return declareVariable(type, name, Alloc.ALIAS, DefType.LOCAL_COMPILER,
                           VarProvenance.exprTmp(getSourceLoc()), false);
  }

  /**
   *
   * @param type
   * @param var the name of the variable this is the value of:
   *    try and work this into the generated name. Can be left as
   *    null
   * @return
   * @throws UserException
   */
  @Override
  public Var createLocalValueVariable(Type type, Var var)
      throws UserException {
    String varName;
    VarProvenance prov;
    if (var != null) {
      prov = VarProvenance.valueOf(var, getSourceLoc());
      varName = var.name();
    } else {
      prov = VarProvenance.exprTmp(getSourceLoc());
      varName = null;
    }
    Alloc storage;
    if (Types.isPrimValue(type) || Types.isContainerLocal(type)) {
      storage = Alloc.LOCAL;
    } else {
      storage = Alloc.ALIAS;
    }

    String name = uniqueName(Var.LOCAL_VALUE_VAR_PREFIX, varName,
                            "value_var");
    return declareVariable(type, name, storage, DefType.LOCAL_COMPILER,
                           prov, false);
  }

  @Override
  public Var createFilenameAliasVariable(Var fileVar) {
    String fileVarName = fileVar != null ? fileVar.name() : null;
    String name = uniqueName(Var.FILENAME_OF_PREFIX, fileVarName,
                             "filename_of");
    try {
      return declareVariable(Types.F_STRING, name,
          Alloc.ALIAS, DefType.LOCAL_COMPILER,
          VarProvenance.filenameOf(fileVar, getSourceLoc()), false);
    } catch (DoubleDefineException e) {
      e.printStackTrace();
      throw new STCRuntimeError("Should be possible to have double defn");
    }
  }

  @Override
  public FnID defineFunction(String name, FunctionType type,
              DefaultVals defaultVals) throws DoubleDefineException {
    throw new STCRuntimeError("Cannot define function in local context");
  }

  @Override
  public List<FnOverload> lookupFunction(String name) {
    return globals.lookupFunction(name);
  }

  @Override
  public void setFunctionProperty(FnID id, FnProp prop) {
    throw new STCRuntimeError("Cannot define function in local context");
  }

  @Override
  public boolean hasFunctionProp(FnID id, FnProp prop) {
    return parent.hasFunctionProp(id, prop);
  }

  @Override
  public List<FnProp> getFunctionProps(FnID id) {
    return parent.getFunctionProps(id);
  }

  @Override
  public void addIntrinsic(FnID id, IntrinsicFunction intrinsic) {
    throw new STCRuntimeError("Cannot add intrinsic in local context");
  }

  @Override
  public IntrinsicFunction lookupIntrinsic(FnID id) {
    return parent.lookupIntrinsic(id);
  }

  @Override
  public GlobalContext getGlobals() {
    return globals;
  }

  @Override
  public ForeignFunctions getForeignFunctions() {
    return globals.getForeignFunctions();
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
        throws DoubleDefineException {
    for (Var v : variables)
      declareVariable(v);
  }

  @Override
  public String toString() {
    return getVisibleVariables().toString();
  }

  @Override
  public Type lookupTypeUnsafe(String typeName) {
    Type t = types.get(typeName);
    if (t != null) {
      return t;
    } else {
      return parent.lookupTypeUnsafe(typeName);
    }
  }


  @Override
  public ExecContext lookupExecContext(String name)
              throws UndefinedExecContextException {
    return globals.lookupExecContext(name);
  }

  @Override
  public Collection<String> execTargetNames() {
    return globals.execTargetNames();
  }

  /**
   * Called when we want to create a new alias for a structure filed
   */
  @Override
  public Var createStructFieldTmp(Var struct,
      Type fieldType, List<String> fieldPath, Alloc storage) {
    // Should be unique in context
    String pathStr = buildPathStr(fieldPath);
    String basename = Var.structFieldName(struct, pathStr);
    String name = basename;
    int counter = 1;
    while (lookupDef(name) != null) {
      name = basename + "-" + counter;
      counter++;
    }
    try {
      VarProvenance prov =
           VarProvenance.structField(struct, fieldPath, getSourceLoc());
      return declareVariable(fieldType, name, storage, DefType.LOCAL_COMPILER,
                             prov, false);
    } catch (DoubleDefineException e) {
      e.printStackTrace();
      throw new STCRuntimeError("Shouldn't be possible to have double defn");
    }
  }

  @Override
  public FunctionContext getFunctionContext() {
    if (this.functionContext != null) {
      return this.functionContext;
    } else {
      return parent.getFunctionContext();
    }
  }
}
