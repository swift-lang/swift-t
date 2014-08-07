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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.MultiMap.ListFactory;

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
    SIZE, CONTAINS, RANGE, RANGE_STEP, ARGV;

    /** List of functions that will initialize output mapping */
    public static final SpecialFunction INITS_OUTPUT_MAPPING[] = new
        SpecialFunction[] {UNCACHED_INPUT_FILE, INPUT_FILE, INPUT_URL};
  }

  /**
   * Track all foreign functions used in the program
   */
  private final Set<String> foreignFunctions = new HashSet<String>();

  /**
   * Map from implementation function name to special function anme
   */
  private final HashMap<String, SpecialFunction> specialImpls =
                                        new HashMap<String, SpecialFunction>();

  // Inverse map
  private final MultiMap<SpecialFunction, String> specialImplsInv =
                                       new MultiMap<SpecialFunction, String>();

  /** Names of built-ins which don't have side effects */
  private HashSet<String> pure = new HashSet<String>();

  /** Names of built-ins which have a local equivalent operation */
  private HashMap<String, BuiltinOpcode>
            equivalentOps = new HashMap<String, BuiltinOpcode>();

  /** inverse of localEquivalents */
  private MultiMap<BuiltinOpcode, String> equivalentOpsInv
          = new MultiMap<BuiltinOpcode, String>(new ListFactory<String>() {
            @Override
            public List<String> make() { // Will only have one entry most of time
              return new ArrayList<String>(1);
            }
          });

  /** Built-ins which are known to be deterministic */
  private HashSet<String> commutative = new HashSet<String>();

  /**
   * Functions which just copy value of input to output
   */
  private HashSet<String> copyFunctions = new HashSet<String>();
  private HashSet<String> minMaxFunctions = new HashSet<String>();


  /**
   * Functions that have a local implementation
   * Map from Swift function name to function name of local
   */
  private Map<String, String> localImpls = new HashMap<String, String>();
  private MultiMap<String, String> localImplsInv = new MultiMap<String, String>();

  private HashMap<String, ExecTarget> taskModes
    = new HashMap<String, ExecTarget>();

  public void addForeignFunction(String functionName) {
    if (!foreignFunctions.add(functionName)) {
      throw new STCRuntimeError("Tried to add foreign function "
                                      + functionName + " twice");
    }
  }

  public boolean isForeignFunction(String functionName) {
    return foreignFunctions.contains(functionName);
  }

  /**
   * @param functionName
   * @return true if the function expects inputs and outputs to be recursively
   *              unpacked for local version (i.e. no ADLB ids in input)
   */
  public boolean recursivelyUnpackedInOut(String functionName) {
    // For now, all foreign functions expect this
    return isForeignFunction(functionName);
  }

  /**
   * @param specialName
   * @return enum value if this is a valid name of a special function,
   *         otherwise null
   */
  public SpecialFunction findSpecialFunction(String specialName) {
    try {
      return SpecialFunction.valueOf(specialName.toUpperCase());
    } catch (IllegalArgumentException ex){
      return null;
    }
  }

  public void addSpecialImpl(SpecialFunction special, String implName) {
    specialImpls.put(implName, special);
    specialImplsInv.put(special, implName);
  }

  /**
   * Check if a given function is an implementation of any of the given
   *  special functions
   * @param function
   * @param specials
   * @return
   */
  public boolean isSpecialImpl(String function,
                          SpecialFunction ...specials) {
    SpecialFunction act = specialImpls.get(function);
    if (act != null) {
      for (SpecialFunction special: specials) {
        if (act == special) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean initsOutputMapping(String function) {
    return isSpecialImpl(function, SpecialFunction.INITS_OUTPUT_MAPPING);
  }

  /**
   * Find an implementation of the special operation
   * @param special
   * @return
   */
  public String findSpecialImpl(SpecialFunction special) {
    List<String> impls = specialImplsInv.get(special);
    if (impls.isEmpty()) {
      return null;
    } else {
      return impls.get(0);
    }
  }

  public void addPure(String builtinFunction) {
    pure.add(builtinFunction);
  }

  public boolean isPure(String builtinFunction) {
    return pure.contains(builtinFunction);
  }


  public void addOpEquiv(String functionName, BuiltinOpcode op) {
    equivalentOps.put(functionName, op);
    equivalentOpsInv.put(op, functionName);
  }

  public boolean hasOpEquiv(String builtinFunction) {
    return equivalentOps.containsKey(builtinFunction);
  }

  public BuiltinOpcode getOpEquiv(String builtinFunction) {
    return equivalentOps.get(builtinFunction);
  }

  /**
   * Find an implementation of a built-in op
   */
  public List<String> findOpImpl(BuiltinOpcode op) {
    return equivalentOpsInv.get(op);
  }

  public void addCommutative(String builtInFunction) {
    commutative.add(builtInFunction);
  }

  public boolean isCommutative(String builtinFunction) {
    return commutative.contains(builtinFunction);
  }

  public void addCopy(String builtInFunction) {
    copyFunctions.add(builtInFunction);
  }

  public boolean isCopyFunction(String builtinFunction) {
    return copyFunctions.contains(builtinFunction);
  }

  public void addMinMax(String builtInFunction) {
    minMaxFunctions.add(builtInFunction);
  }

  public boolean isMinMaxFunction(String builtinFunction) {
    return minMaxFunctions.contains(builtinFunction);
  }

  public void addAssertVariable(String builtinFunction) {
    assertVariants.add(builtinFunction);
  }

  /** Keep track of assert variants so they can be disabled as an optimization */
  private final HashSet<String> assertVariants = new
                HashSet<String>();

  /**
   * @param fnName true if the named builtin is some kind of assert statemetn
   * @return
   */
  public boolean isAssertVariant(String fnName) {
    return assertVariants.contains(fnName);
  }

  /**
   * Mark that there is a local version of function
   * @param swiftFunction
   * @param localFunction
   */
  public void addLocalImpl(String swiftFunction, String localFunction) {
    localImpls.put(swiftFunction, localFunction);
    localImplsInv.put(localFunction, swiftFunction);
  }

  public boolean hasLocalImpl(String swiftFunction) {
    return localImpls.containsKey(swiftFunction);
  }

  public String getLocalImpl(String swiftFunction) {
    return localImpls.get(swiftFunction);
  }

  public boolean isLocalImpl(String localFunction) {
    return localImplsInv.containsKey(localFunction);
  }

  public Set<String> getLocalImplKeys() {
    return Collections.unmodifiableSet(localImpls.keySet());
  }

  public void addTaskMode(String functionName, ExecTarget mode) {
    taskModes.put(functionName, mode);
  }

  /**
   * Return the intended task mode for the function (e.g. if it should run on worker
   * or locally)
   * @param functionName
   * @return non-null target
   */
  public ExecTarget getTaskMode(String functionName) {
    ExecTarget mode = taskModes.get(functionName);
    if (mode != null) {
      return mode;
    } else {
      return ExecTarget.DEFAULT_BUILTIN_MODE;
    }
  }
}
