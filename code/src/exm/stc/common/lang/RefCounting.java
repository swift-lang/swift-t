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
import java.util.List;

import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Var.DefType;

public class RefCounting {
  public static enum RefCountType {
    READERS,
    WRITERS;
  }

  /**
   * Whether an input arg for an updateable var should be writable by
   * the callee
   * TODO: does this make sense from language design?
   */
  public static final boolean WRITABLE_UPDATEABLE_INARGS = true;

  /**
   * Returns true if type can carry a refcount.  Some particular
   * storage modes of the type might not have a refcount
   * @param type
   * @param rcType
   * @return
   */
  public static boolean mayHaveRefcount(Type type, RefCountType rcType) {
    if (rcType == RefCountType.READERS) {
      return mayHaveReadRefcount(type);
    } else {
      assert(rcType == RefCountType.WRITERS);
      return mayHaveWriteRefcount(type);
    }
  }

  public static boolean mayHaveReadRefcount(Type type) {
    if (Types.isPrimValue(type)) {
      return false;
    } else if (Types.isContainerLocal(type) ||
               Types.isStructLocal(type)) {
      return false;
    }
    return true;
  }

  public static boolean mayHaveWriteRefcount(Type type) {
    // Struct members may have write refcount 
    return Types.isArray(type) || Types.isPrimUpdateable(type) ||
           Types.isStruct(type) || Types.isBag(type);
  }

  /**
   * @param v
   * @return true if var has read refcount to be managed
   */
  public static boolean hasReadRefCount(Var v) {
    if (!mayHaveReadRefcount(v.type())) {
      return false;
    } else if (v.defType() == DefType.GLOBAL_CONST) {
      return false;
    }
    return true;
  }
  
  /**
   * Return true if writer count is tracked for type
   * @param v
   * @return
   */
  public static boolean hasWriteRefCount(Var v) {
    if (!mayHaveWriteRefcount(v.type())) {
      return false;
    } else if (v.defType() == DefType.GLOBAL_CONST) {
      return false;
    }
    return true;
  }
  
  public static boolean hasRefCount(Var var, RefCountType type) {
    if (type == RefCountType.READERS) {
      return hasReadRefCount(var);
    } else {
      assert(type == RefCountType.WRITERS);
      return hasWriteRefCount(var);
    }
  }

  /**
   * Filter vars to include only variables where writers count is tracked
   * @param vars
   * @return
   */
  public static List<Var> filterWriteRefcount(List<Var> vars) {
    assert(vars != null);
    List<Var> res = new ArrayList<Var>();
    for (Var var: vars) {
      if (hasWriteRefCount(var)) {
        res.add(var);
      }
    }
    return res;
  }
  
  
  /**
   * @param wv
   * @return true if there is a distinction between closing and recursive
   *              closing for variable.  Note: this errs on the side of saying
   *              true.
   */
  public static boolean recursiveClosePossible(Typed typed) {
    if (Types.isPrimFuture(typed) || Types.isPrimUpdateable(typed)) {
      return false;
    } else if (Types.isContainer(typed)) {
      // If it's a reference to another variable, recursive close is possible
      return Types.isRef(Types.containerElemType(typed));
    } else if (Types.isRef(typed)) {
      return true;
    }
    // Err on side of saying true
    return true;
  }

}
