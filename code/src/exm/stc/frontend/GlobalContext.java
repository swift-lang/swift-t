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

import static exm.stc.frontend.typecheck.FunctionTypeChecker.checkOverloadAllowed;
import static exm.stc.frontend.typecheck.FunctionTypeChecker.checkOverloadsAmbiguity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.DoubleDefineException;
import exm.stc.common.exceptions.InvalidOverloadException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedExecContextException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.DefaultVals;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.Intrinsics.IntrinsicFunction;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.UnionType;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.util.Counters;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;

/**
 * Global context for entire program
 *
 */
public class GlobalContext extends Context {

  private final MultiMap<String, FnOverload> functionOverloads =
                              new MultiMap<String, FnOverload>();

  /**
   * Properties of functions
   */
  private final Set<Pair<FnID, FnProp>> functionProps =
                        new HashSet<Pair<FnID, FnProp>>();

  /**
   * Track function name to intrinsic op mapping
   */
  private final Map<FnID, IntrinsicFunction> intrinsics =
                        new HashMap<FnID, IntrinsicFunction>();

  /**
   * Track info about foreign functions
   */
  private final ForeignFunctions foreignFuncs;

  /**
   * Track name to exec target mapping
   * @param inputFile
   * @param logger
   */
  private final Map<String, ExecContext> execContexts =
                              new HashMap<String, ExecContext>();

  private final Counters<String> globalCounters = new Counters<String>();

  public GlobalContext(String inputFile, Logger logger,
                        ForeignFunctions foreignFuncs) {
    super(logger, 0);
    this.inputFile = inputFile;
    this.foreignFuncs = foreignFuncs;

    // Add all predefined types into type name dict
    types.putAll(Types.getBuiltInTypes());
    try {
      addExecContexts(ExecContext.builtinExecContexts());
    } catch (DoubleDefineException e) {
      throw new STCRuntimeError("Unexpected exception while initializing exec " +
      		"targets", e);
    }
  }

  @Override
  public boolean isTopLevel() {
    return true;
  }

  @Override
  public DefInfo lookupDef(String name) {
    return allDefs.get(name);
  }

  @Override
  public List<FnOverload> lookupFunction(String name) {
    return functionOverloads.get(name);
  }

  @Override
  public FnID defineFunction(String name, FunctionType type,
      DefaultVals<Var> defaultVals) throws UserException {
    FnID fnID;
    DefInfo def = lookupDef(name);
    if (def != null && def.kind == DefKind.FUNCTION) {
      // Function already exists, need to add overload
      fnID = overloadFunction(name, type, defaultVals);

      // Need to ensure name isn't reused
      declareVariable(type, fnID.uniqueName(), Alloc.GLOBAL_CONST,
          DefType.GLOBAL_CONST,  VarProvenance.userVar(getSourceLoc()), false);

    } else {
      declareVariable(type, name, Alloc.GLOBAL_CONST, DefType.GLOBAL_CONST,
                      VarProvenance.userVar(getSourceLoc()), false);
      fnID = new FnID(name, name);
      addFunctionOverload(name, fnID, type, defaultVals);
    }
    return fnID;
  }

  private FnID overloadFunction(String name, FunctionType type,
      DefaultVals<Var> defaultVals) throws InvalidOverloadException {

    String uniqueName = uniqueName(Var.OVERLOAD_PREFIX, name,
                                   Var.OVERLOAD_PREFIX + name);
    FnID overloadID = new FnID(uniqueName, name);

    List<FnOverload> overloads = functionOverloads.get(name);
    if (overloads.size() == 1) {
      // First overload wasn't checked
      checkOverloadAllowed(this, overloads.get(0).id, overloads.get(0).type,
                           overloads.get(0).defaultVals.hasAnyDefaults());
    }

    checkOverloadAllowed(this, overloadID, type,
                         defaultVals.hasAnyDefaults());

    // Pairwise checks for ambiguity
    for (FnOverload overload: overloads) {
      checkOverloadsAmbiguity(this, name, type, overload.type);
    }

    if (overloads.size() > 0) {
      addOverloadToType(name, type);
    }

    addFunctionOverload(name, overloadID, type, defaultVals);

    return overloadID;
  }

  private void addFunctionOverload(String name, FnID fnID,
                 FunctionType type, DefaultVals<Var> defaultVals) {
    functionOverloads.put(name, new FnOverload(fnID, type, defaultVals));
  }

  /**
   * We represent overloaded functions in variable map as a function
   * variable with a union type.  This adds a new type to the union.
   *
   * @param name
   * @param type
   */
  private void addOverloadToType(String name, FunctionType type) {
    Var existing = lookupVarUnsafe(name);
    List<Type> alts = new ArrayList<Type>();
    alts.addAll(UnionType.getAlternatives(existing.type()));
    alts.add(type);
    replaceVarUnsafe(name, existing.substituteType(
                        UnionType.makeUnion(alts)));
  }

  private void replaceVarUnsafe(String name, Var newVar) {
    this.variables.put(name, newVar);
  }

  @Override
  public void setFunctionProperty(FnID id, FnProp prop) {
    functionProps.add(new Pair<FnID, FnProp>(id, prop));
  }

  @Override
  public boolean hasFunctionProp(FnID id, FnProp prop) {
    return functionProps.contains(new Pair<FnID, FnProp>(id, prop));
  }

  @Override
  public List<FnProp> getFunctionProps(FnID id) {
    List<FnProp> props = new ArrayList<FnProp>();
    for (Pair<FnID, FnProp> pair: functionProps) {
      if (pair.val1.equals(id)) {
        props.add(pair.val2);
      }
    }
    return props;
  }


  @Override
  public void addIntrinsic(FnID function,
                           IntrinsicFunction intrinsic) {
    intrinsics.put(function, intrinsic);
  }

  @Override
  public IntrinsicFunction lookupIntrinsic(FnID function) {
    return intrinsics.get(function);
  }

  /**
     Declare a global variable
   * @throws DoubleDefineException
   */
  @Override
  public Var declareVariable(Type type, String name, Alloc scope,
          DefType defType, VarProvenance provenance, boolean mapped)
                           throws DoubleDefineException {
    // Sanity checks for global scope
    assert(defType == DefType.GLOBAL_CONST);
    assert(scope == Alloc.GLOBAL_CONST);
    return super.declareVariable(type, name, scope, defType, provenance,
                                 mapped);
  }

  @Override
  public Var createTmpVar(Type type, boolean storeInStack) {
      throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Var createTmpAliasVar(Type type) throws UserException {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Var createLocalValueVariable(Type type, Var var)
      throws UserException {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Var createFilenameAliasVariable(Var fileVar) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public Var createGlobalConst(String name, Type type, boolean makeUnique)
      throws DoubleDefineException {
    assert(name != null);
    if (makeUnique) {
      name = uniqueName(Var.GLOBAL_CONST_VAR_PREFIX, name, "global_const");
    }

    return declareVariable(type, name,
                   Alloc.GLOBAL_CONST, DefType.GLOBAL_CONST,
                   VarProvenance.userVar(getSourceLoc()), false);
  }

  @Override
  public GlobalContext getGlobals() {
    return this;
  }

  @Override
  public ForeignFunctions getForeignFunctions() {
    return foreignFuncs;
  }

  @Override
  public Var lookupVarUnsafe(String variable) {
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

  public ExecContext.WorkContext declareWorkType(String name) throws DoubleDefineException {
    // Should be case insensitive
    String canonicalName = name.toUpperCase();
    LogHelper.debug(this, "Defined work type " + canonicalName);
    ExecContext.WorkContext workCx = new ExecContext.WorkContext(canonicalName);
    addExecContext(canonicalName, ExecContext.worker(workCx));
    return workCx;
  }

  public void addExecContexts(List<Pair<String, ExecContext>> contexts)
      throws DoubleDefineException {
    for (Pair<String, ExecContext> cx: contexts) {
      addExecContext(cx.val1, cx.val2);
    }
  }

  public void addExecContext(String name, ExecContext context)
      throws DoubleDefineException {
    String canonicalName = name.toUpperCase();
    ExecContext oldContext = execContexts.get(canonicalName);
    if (oldContext != null) {
      throw new DoubleDefineException(this,
          "Redefined execution target " + canonicalName);
    }
    execContexts.put(canonicalName, context);
  }

  @Override
  public ExecContext lookupExecContext(String name)
      throws UndefinedExecContextException {
    String canonicalName = name.toUpperCase();
    ExecContext cx = execContexts.get(canonicalName);
    if (cx == null) {
      throw new UndefinedExecContextException(this, canonicalName);
    }

    return cx;
  }

  @Override
  public Collection<String> execTargetNames() {
    return Collections.unmodifiableSet(execContexts.keySet());
  }

  @Override
  public Var createStructFieldTmp(Var struct, Type fieldType,
      List<String> fieldPath, Alloc storage) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  @Override
  public FunctionContext getFunctionContext() {
    return null;
  }

  @Override
  public long nextCounterVal(String counterName) {
    return globalCounters.increment(counterName);
  }

}
