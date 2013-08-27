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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UndefinedVarError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.ForeignFunctions.TemplateElem.ElemKind;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.MultiMap.ListFactory;
import exm.stc.frontend.Context;

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
    SIZE, RANGE, RANGE_STEP, ARGV;
    
    /** List of functions that will initialize output mapping */
    public static final SpecialFunction INITS_OUTPUT_MAPPING[] = new
        SpecialFunction[] {UNCACHED_INPUT_FILE, INPUT_FILE, INPUT_URL};
  }
  
  /**
   * Map from implementation function name to special function anme
   */
  private static final HashMap<String, SpecialFunction> specialImpls =
                                        new HashMap<String, SpecialFunction>();
  
  // Inverse map
  private static final MultiMap<SpecialFunction, String> specialImplsInv =
                                       new MultiMap<SpecialFunction, String>();
 
  /** Names of built-ins which don't have side effects */
  private static HashSet<String> pure = new HashSet<String>();
  
  /** Names of built-ins which have a local equivalent operation */
  private static HashMap<String, BuiltinOpcode>
            equivalentOps = new HashMap<String, BuiltinOpcode>();

  /** inverse of localEquivalents */
  private static MultiMap<BuiltinOpcode, String> equivalentOpsInv
          = new MultiMap<BuiltinOpcode, String>(new ListFactory<String>() {
            public List<String> make() { // Will only have one entry most of time
              return new ArrayList<String>(1);
            }
          });
  
  /** Built-ins which are known to be deterministic */
  private static HashSet<String> commutative = 
                                      new HashSet<String>();

  /**
   * Functions which just copy value of input to output
   */
  private static HashSet<String> copyFunctions = new HashSet<String>();
  private static HashSet<String> minMaxFunctions = new HashSet<String>();
  
  
  /**
   * Templates for inline tcl code that can be used to generate
   * local implementation of function
   */
  private static HashMap<String, TclOpTemplate> inlineTemplates
    = new HashMap<String, TclOpTemplate>();
  
  private static HashMap<String, TaskMode> taskModes
    = new HashMap<String, TaskMode>();

  /**
   * @param specialName
   * @return enum value if this is a valid name of a special function,
   *         otherwise null
   */
  public static SpecialFunction findSpecialFunction(String specialName) {
    try {
      return SpecialFunction.valueOf(specialName.toUpperCase());
    } catch (IllegalArgumentException ex){
      return null;
    }
  }
  
  public static void addSpecialImpl(SpecialFunction special, String implName) {
    specialImpls.put(implName, special);
    specialImplsInv.put(special, implName);
  }
  
  /**
   * Check if a given function is an implementation of any of the given
   *  special functions
   * @param function
   * @param special
   * @return
   */
  public static boolean isSpecialImpl(String function,
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
  
  public static boolean initsOutputMapping(String function) {
    return isSpecialImpl(function, SpecialFunction.INITS_OUTPUT_MAPPING);
  }

  /**
   * Find an implementation of the special operation
   * @param special
   * @return
   */
  public static String findSpecialImpl(SpecialFunction special) {
    List<String> impls = specialImplsInv.get(special);
    if (impls.isEmpty()) {
      return null;
    } else {
      return impls.get(0);
    }
  }
  
  public static void addPure(String builtinFunction) {
    pure.add(builtinFunction);
  }
  
  public static boolean isPure(String builtinFunction) {
    return pure.contains(builtinFunction);
  }
  

  public static void addOpEquiv(String builtinFunction, BuiltinOpcode op) {
    equivalentOps.put(builtinFunction, op);
    equivalentOpsInv.put(op, builtinFunction);
  }
  
  public static boolean hasOpEquiv(String builtinFunction) {
    return equivalentOps.containsKey(builtinFunction);
  }
  
  public static BuiltinOpcode getOpEquiv(String builtinFunction) {
    return equivalentOps.get(builtinFunction);
  }
  
  /**
   * Find an implementation of a built-in op
   */
  public static List<String> findOpImpl(BuiltinOpcode op) {
    return equivalentOpsInv.get(op);
  }
  
  public static void addCommutative(String builtInFunction) {
    commutative.add(builtInFunction);
  }
  
  public static boolean isCommutative(String builtinFunction) {
    return commutative.contains(builtinFunction);
  }
  
  public static void addCopy(String builtInFunction) {
    copyFunctions.add(builtInFunction);
  }
  
  public static boolean isCopyFunction(String builtinFunction) {
    return copyFunctions.contains(builtinFunction);
  }
  
  public static void addMinMax(String builtInFunction) {
    minMaxFunctions.add(builtInFunction);
  }
  
  public static boolean isMinMaxFunction(String builtinFunction) {
    return minMaxFunctions.contains(builtinFunction);
  }
  
  public static void addAssertVariable(String builtinFunction) {
    assertVariants.add(builtinFunction);
  }
  
  /** Keep track of assert variants so they can be disabled as an optimization */
  private static final HashSet<String> assertVariants = new
                HashSet<String>();
  
  /**
   * @param fnName true if the named builtin is some kind of assert statemetn
   * @return
   */
  public static boolean isAssertVariant(String fnName) {
    return assertVariants.contains(fnName);
  }
  
  
  public static void addInlineTemplate(String fnName, TclOpTemplate tmp) {
    inlineTemplates.put(fnName, tmp);
  }
  
  public static boolean hasInlineVersion(String fnName) {
    return inlineTemplates.containsKey(fnName);
  }
  
  public static TclOpTemplate getInlineTemplate(String fnName) {
    return inlineTemplates.get(fnName);
  }
  
  public static void addTaskMode(String functionName, TaskMode mode) {
    taskModes.put(functionName, mode);
  }
  
  /**
   * Return the intended task mode for the function (e.g. if it should run on worker
   * or locally)
   * @param functionName
   * @return
   */
  public static TaskMode getTaskMode(String functionName) {
    TaskMode mode = taskModes.get(functionName);
    if (mode != null) {
      return mode;
    } else {
      return TaskMode.DEFAULT_BUILTIN_MODE;
    }
  }

  public static class TemplateElem {
    public static enum ElemKind {
      TEXT,
      VARIABLE
    }
    
    private final ElemKind kind;
    private final String contents;
    
    private TemplateElem(ElemKind kind, String contents) {
      super();
      this.kind = kind;
      this.contents = contents;
    }
    
    public static TemplateElem createTok(String text) {
      return new TemplateElem(ElemKind.TEXT, text);
    }
    
    public static TemplateElem createVar(String varName) {
      return new TemplateElem(ElemKind.VARIABLE, varName);
    }
    
    public ElemKind getKind() {
      return kind;
    }
    
    public String getText() {
      if (kind == ElemKind.TEXT) {
        return contents;
      } else {
        throw new STCRuntimeError("not text, was: " + kind); 
      }
    }
    
    public String getVarName() {
      if (kind == ElemKind.VARIABLE) {
        return contents;
      } else {
        throw new STCRuntimeError("not var, was: " + kind); 
      }
    }
    
    public String toString() {
      if (kind == ElemKind.VARIABLE) {
        return contents;
      } else {
        assert(kind == ElemKind.TEXT);
        return "\"" + contents + "\"";
      }
    }
  }
  
  public static class TclOpTemplate {
    private final ArrayList<TemplateElem> elems = 
                              new ArrayList<TemplateElem>();
    
    /**
     * Names of positional input variables for template
     */
    private final ArrayList<String> outNames =
                              new ArrayList<String>();
    
    /**
     * Names of positional output variables for template
     */
    private final ArrayList<String> inNames =
                              new ArrayList<String>();
    
    /**
     * Name of varargs (null if no varargs)
     */
    private String varArgIn = null;
    
    public boolean addInName(String e) {
      return inNames.add(e);
    }

    public boolean addInNames(Collection<? extends String> c) {
      return inNames.addAll(c);
    }

    public boolean addOutName(String e) {
      return outNames.add(e);
    }

    public boolean addOutNames(Collection<? extends String> c) {
      return outNames.addAll(c);
    }
    
    public void setVarArgIn(String varArgIn) {
      this.varArgIn = varArgIn;
    }

    public List<String> getInNames() {
      return Collections.unmodifiableList(inNames);
    }
    
    public List<String> getOutNames() {
      return Collections.unmodifiableList(outNames);
    }

    public String getVarArgIn() {
      return varArgIn;
    }

    public boolean hasVarArgs() {
      return varArgIn != null;
    }

    public void addElem(TemplateElem elem) {
      elems.add(elem);
    }
    
    public List<TemplateElem> getElems() {
      return Collections.unmodifiableList(elems);
    }

    public String toString() {
      return elems.toString();
    }

    /**
     * Check all variables reference in template are in names or out names
     * @throws UserException 
     */
    public void verifyNames(Context context) throws UserException {
      List<String> badNames = new ArrayList<String>();
      for (TemplateElem elem: elems) {
        if (elem.getKind() == ElemKind.VARIABLE) {
          String varName = elem.getVarName();
          if (!outNames.contains(varName) && 
              !inNames.contains(varName)) {
            badNames.add(varName);
          }
        }
      }
      if (badNames.size() > 0) {
        throw UndefinedVarError.fromNames(context, badNames);
      }
    }
  }
}
