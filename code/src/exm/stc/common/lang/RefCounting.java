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

import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Var.DefType;

/**
 * Define how types should be refcounting
 *
 * There are two dimensions in which we define reference counts:
 * - READ/WRITE - whether the reference is only read through or if it can be
 *                read or written through
 * - TRACKED/UNTRACKED - a tracked reference count is explicitly managed by
 *           the compiler, and is generally used for structures like arrays
 *           that can be written a non-fixed number of times.  An untracked
 *           reference count is automatically decremented for each assign, so
 *           is suited for structures that receive a fixed number of writes,
 *           e.g. a single-assignment variable
 */
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
  public static boolean mayHaveTrackedRefcount(Type type, RefCountType rcType) {
    if (rcType == RefCountType.READERS) {
      return mayHaveTrackedReadRefcount(type);
    } else {
      assert(rcType == RefCountType.WRITERS);
      return mayHaveTrackedWriteRefcount(type);
    }
  }

  public static boolean mayHaveTrackedReadRefcount(Type type) {
    if (Types.isPrimValue(type)) {
      return false;
    } else if (Types.isContainerLocal(type) ||
               Types.isStructLocal(type)) {
      return false;
    }
    return true;
  }

  public static boolean mayHaveTrackedWriteRefcount(Type type) {
    // Struct members may have write refcount
    if (Types.isArray(type) || Types.isPrimUpdateable(type) ||
           Types.isBag(type)) {
      return true;
    } else if (Types.isStruct(type)) {
      // Depends on field types: if we have any tracked fields, then
      // track write refcount
      return baseRefCount(type, DefType.LOCAL_COMPILER,
                          RefCountType.WRITERS, true, false) > 0;
    } else {
      return false;
    }
  }

  /**
   * @param v
   * @return true if var has read refcount to be tracked/managed by
   *              STC-emitted code
   */
  public static boolean trackReadRefCount(Var v) {
    return trackReadRefCount(v.type(), v.defType());
  }

  public static boolean trackReadRefCount(Type type, DefType defType) {
    if (!mayHaveTrackedReadRefcount(type)) {
      return false;
    } else if (defType == DefType.GLOBAL_CONST) {
      return false;
    }
    return true;
  }

  /**
   * @param v
   * @return true if var has write refcount to be tracked/managed by
   *              STC-emitted code
   */
  public static boolean trackWriteRefCount(Var v) {
    return trackWriteRefCount(v.type(), v.defType());
  }

  public static boolean trackWriteRefCount(Type type, DefType defType) {
    if (!mayHaveTrackedWriteRefcount(type)) {
      return false;
    } else if (defType == DefType.GLOBAL_CONST) {
      return false;
    }
    return true;
  }

  /**
   * @param v
   * @return true if var has refcount to be tracked/managed by
   *              STC-emitted code
   */
  public static boolean trackRefCount(Var var, RefCountType rcType) {
    return trackRefCount(var.type(), var.defType(), rcType);
  }

  public static boolean trackRefCount(Type type, DefType defType,
                                      RefCountType rcType) {
    if (rcType == RefCountType.READERS) {
      return trackReadRefCount(type, defType);
    } else {
      assert(rcType == RefCountType.WRITERS);
      return trackWriteRefCount(type, defType);
    }
  }

  /**
   * Filter vars to include only variables where writers count is tracked
   * @param vars
   * @return
   */
  public static List<Var> filterTrackedWriteRefcount(List<Var> vars) {
    assert(vars != null);
    List<Var> res = new ArrayList<Var>();
    for (Var var: vars) {
      if (trackWriteRefCount(var)) {
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

  public static long baseRefCount(Var v, RefCountType rcType,
            boolean includeTracked, boolean includeUntracked) {
    return baseRefCount(v.type(), v.defType(), rcType, includeTracked,
                       includeUntracked);
  }

  /**
   * Return base reference count for type, i.e. what it's initialized to
   * by default
   * @param rcType
   * @param blockVar
   * @param includeTracked
   * @param includeUntracked
   * @return
   */
  public static long baseRefCount(Type type, DefType defType,
       RefCountType rcType, boolean includeTracked, boolean includeUntracked) {
    if (Types.isStruct(type) && rcType == RefCountType.WRITERS) {
      return baseStructWriteRefCount(type, defType, includeTracked,
                                     includeUntracked);
    } else if (Types.isPrimValue(type) || Types.isContainerLocal(type) ||
               Types.isStructLocal(type)) {
      // No refcount
      return 0;
    } else {
      // Data store variables generally have one refcount to start with
      // In some cases (e.g. decrement-on-assign), it's not tracked
      if (trackRefCount(type, defType, rcType)) {
        return includeTracked ? 1 : 0;
      } else {
        return includeUntracked ? 1 : 0;
      }
    }
  }

  public static long baseReadRefCount(Var var, boolean includeTracked,
                                      boolean includeUntracked) {
    return baseRefCount(var.type(), var.defType(), RefCountType.READERS,
                        includeTracked, includeUntracked);
  }

  public static long baseWriteRefCount(Var var, boolean includeTracked,
                                       boolean includeUntracked) {
    return baseRefCount(var.type(), var.defType(), RefCountType.WRITERS,
        includeTracked, includeUntracked);
  }

  public static long baseStructWriteRefCount(Type type, DefType defType,
      boolean includeTracked, boolean includeUntracked) {
    assert(Types.isStruct(type));

    // Sum of field refcounts
    StructType structT = (StructType)type.getImplType();
    long trackedSum = 0;
    long untrackedSum = 0;
    for (StructField field: structT.fields()) {

      long fieldUntracked = baseRefCount(field.type(), defType,
                              RefCountType.WRITERS, false, true);
      if (Types.isMutableRef(field.type())) {
        // Need to have tracked refcount as proxy
        Type referencedType = field.type().getImplType().memberType();
        trackedSum += baseRefCount(referencedType, defType,
                              RefCountType.WRITERS, true, false);
      } else {
        untrackedSum += fieldUntracked;
        trackedSum += baseRefCount(field.type(), defType,
                              RefCountType.WRITERS, true, false);
      }
    }

    long structCount = 0;
    if (includeTracked && trackedSum > 0) {
      // At least one tracked field
      // Only start off with one tracked count for all fields, can
      // increment if more needed
      structCount += 1;
    }

    if (includeUntracked) {
      // Each field is managed seperately in this case, since
      // each assign will decrement the count
      structCount += untrackedSum;
    }
    return structCount;
  }

}
