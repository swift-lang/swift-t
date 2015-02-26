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
package exm.stc.common.lang;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Operators.BuiltinOpcode;

/**
 * Static class to track info about semantics of foreign functions.
 *
 * Currently it is sufficient to have this as a static class.
 */
public class ForeignFunctions {


  /**
   * Functions that have special handling in STC, e.g. if the optimizer
   * can exploit the particular semantics of them.
   */
  public static enum SpecialFunction {
    INPUT_FILE, UNCACHED_INPUT_FILE, INPUT_URL,
    SIZE, CONTAINS, RANGE, RANGE_STEP, RANGE_FLOAT, RANGE_FLOAT_STEP, ARGV;

    /** List of functions that do not need initialized output mapping for
     * unmapped files (but will accept one if the file is mapped)*/
    public static final SpecialFunction CAN_INIT_OUTPUT_MAPPING[] = new
        SpecialFunction[] {UNCACHED_INPUT_FILE, INPUT_FILE, INPUT_URL};
  }

  private static enum Prop {
    IS_ALREADY_DEFINED, // If already defined
    IS_PURE, // If has no side-effects and is deterministic
    IS_COMMUTATIVE, // Order of arguments doesn't matter
    IS_COPY, // Functions which copy value of input to output
    IS_MINMAX,
    IS_ASSERT_VARIANT,
    ;
  }

  /**
   * Track all foreign functions used in the program
   */
  private final SetMultimap<FnID, Prop> props = HashMultimap.create();

  /**
   * Map from implementation function name to special function name
   */
  private final HashMap<FnID, SpecialFunction> specialImpls =
                                new HashMap<FnID, SpecialFunction>();

  // Inverse map
  private final ListMultimap<SpecialFunction, FnID> specialImplsInv =
                                         ArrayListMultimap.create();

  /** Names of built-ins which have a local equivalent operation */
  private HashMap<FnID, BuiltinOpcode>
            equivalentOps = new HashMap<FnID, BuiltinOpcode>();

  /** inverse of localEquivalents */
  private ListMultimap<BuiltinOpcode, FnID> equivalentOpsInv =
                                        ArrayListMultimap.create();

  /**
   * Functions that have a local implementation
   * Map from Swift function name to function name of local
   */
  private Map<FnID, FnID> localImpls =
                    new HashMap<FnID, FnID>();
  private ListMultimap<FnID, FnID> localImplsInv = ArrayListMultimap.create();

  private HashMap<FnID, ExecTarget> taskModes =
                    new HashMap<FnID, ExecTarget>();

  private void addProp(FnID id, Prop prop) {
    props.put(id, prop);
  }

  private boolean hasProp(FnID id, Prop prop) {
    return props.get(id).contains(prop);
  }

  public void copyProperties(FnID newID, FnID oldID) {
    addForeignFunction(newID);

    props.putAll(newID, props.get(oldID));
    SpecialFunction special = specialImpls.get(oldID);
    if (special != null) {
      addSpecialImpl(special, newID);;
    }

    BuiltinOpcode equiv = equivalentOps.get(oldID);
    if (equiv != null) {
      addOpEquiv(newID, equiv);
    }

    FnID localID = getLocalImpl(oldID);
    if (localID != null) {
      addLocalImpl(newID, localID);
    }

    ExecTarget taskMode = taskModes.get(oldID);
    if (taskMode != null) {
      addTaskMode(newID, taskMode);
    }
  }

  public void addForeignFunction(FnID id) {
    if (hasProp(id, Prop.IS_ALREADY_DEFINED)) {
      throw new STCRuntimeError("Tried to add foreign function "
                                      + id + " twice");
    }

    addProp(id, Prop.IS_ALREADY_DEFINED);
  }

  public boolean isForeignFunction(FnID id) {
    return hasProp(id, Prop.IS_ALREADY_DEFINED);
  }

  /**
   * @param id
   * @return true if the function expects inputs and outputs to be recursively
   *              unpacked for local version (i.e. no ADLB ids in input)
   */
  public boolean recursivelyUnpackedInOut(FnID id) {
    // For now, all foreign functions expect this
    return isForeignFunction(id);
  }

  /**
   * @param sourceName name of function in source
   * @return enum value if this is a valid name of a special function,
   *         otherwise null
   */
  public SpecialFunction findSpecialFunction(String sourceName) {
    try {
      return SpecialFunction.valueOf(sourceName.toUpperCase());
    } catch (IllegalArgumentException ex){
      return null;
    }
  }

  public void addSpecialImpl(SpecialFunction special, FnID implID) {
    specialImpls.put(implID, special);
    specialImplsInv.put(special, implID);
  }

  /**
   * Check if a given function is an implementation of any of the given
   *  special functions
   * @param function
   * @param specials
   * @return
   */
  public boolean isSpecialImpl(FnID id,
                          SpecialFunction ...specials) {
    SpecialFunction act = specialImpls.get(id);
    if (act != null) {
      for (SpecialFunction special: specials) {
        if (act == special) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean canInitOutputMapping(FnID id) {
    return isSpecialImpl(id, SpecialFunction.CAN_INIT_OUTPUT_MAPPING);
  }
  /**
   * True if it is a funciton that never will initialize an output file's mapping,
   * so generated code must do it for it
   */
  public boolean neverInitsOutputMapping(FnID id) {
    return !canInitOutputMapping(id);
  }

  /**
   * Find an implementation of the special operation
   * @param special
   * @return
   */
  public FnID findSpecialImpl(SpecialFunction special) {
    List<FnID> impls = specialImplsInv.get(special);
    if (impls.isEmpty()) {
      return null;
    } else {
      return impls.get(0);
    }
  }

  public void addPure(FnID id) {
    addProp(id, Prop.IS_PURE);
  }

  public boolean isPure(FnID id) {
    return hasProp(id, Prop.IS_PURE);
  }


  public void addOpEquiv(FnID id, BuiltinOpcode op) {
    equivalentOps.put(id, op);
    equivalentOpsInv.put(op, id);
  }

  public boolean hasOpEquiv(FnID builtinFunction) {
    return equivalentOps.containsKey(builtinFunction);
  }

  public BuiltinOpcode getOpEquiv(FnID builtinFunction) {
    return equivalentOps.get(builtinFunction);
  }

  /**
   * Find an implementation of a built-in op
   */
  public List<FnID> findOpImpl(BuiltinOpcode op) {
    return equivalentOpsInv.get(op);
  }

  public void addCommutative(FnID id) {
    addProp(id, Prop.IS_COMMUTATIVE);
  }

  public boolean isCommutative(FnID id) {
    return hasProp(id, Prop.IS_COMMUTATIVE);
  }

  public void addCopy(FnID id) {
    addProp(id, Prop.IS_COPY);
  }

  public boolean isCopyFunction(FnID id) {
    return hasProp(id, Prop.IS_COPY);
  }

  public void addMinMax(FnID id) {
    addProp(id, Prop.IS_MINMAX);
  }

  public boolean isMinMaxFunction(FnID id) {
    return hasProp(id, Prop.IS_MINMAX);
  }

  public void addAssertVariant(FnID id) {
    addProp(id, Prop.IS_ASSERT_VARIANT);
  }

  /**
   * @param id true if the named builtin is some kind of assert statemetn
   * @return
   */
  public boolean isAssertVariant(FnID id) {
    return hasProp(id, Prop.IS_ASSERT_VARIANT);
  }

  /**
   * Mark that there is a local version of function
   * @param swiftFunction
   * @param localFunction
   */
  public void addLocalImpl(FnID swiftFunction, FnID localFunction) {
    localImpls.put(swiftFunction, localFunction);
    localImplsInv.put(localFunction, swiftFunction);
  }

  public boolean hasLocalImpl(FnID swiftFunction) {
    return localImpls.containsKey(swiftFunction);
  }

  public FnID getLocalImpl(FnID swiftFunction) {
    return localImpls.get(swiftFunction);
  }

  public boolean isLocalImpl(FnID localFunction) {
    return localImplsInv.containsKey(localFunction);
  }

  public Set<FnID> getLocalImplKeys() {
    return Collections.unmodifiableSet(localImpls.keySet());
  }

  public void addTaskMode(FnID id, ExecTarget mode) {
    taskModes.put(id, mode);
  }

  /**
   * Return the intended task mode for the function (e.g. if it should run on worker
   * or locally)
   * @param id
   * @return non-null target
   */
  public ExecTarget getTaskMode(FnID id) {
    ExecTarget mode = taskModes.get(id);
    if (mode != null) {
      return mode;
    } else {
      return ExecTarget.DEFAULT_BUILTIN_MODE;
    }
  }
}
