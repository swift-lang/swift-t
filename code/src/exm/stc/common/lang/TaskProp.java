package exm.stc.common.lang;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.lang.Types.Type;
import exm.stc.frontend.Context;

/**
 * Model optional properties of task that can take on runtime values.
 */
public class TaskProp {
  public static class TaskProps extends TreeMap<TaskPropKey, Arg> {
    private static final long serialVersionUID = 1L;

    // Constants: should match definitions in builtins.swift
    public static final String LOC_STRICTNESS_HARD = "HARD";
    public static final Arg LOC_STRICTNESS_HARD_ARG =
                Arg.newString(LOC_STRICTNESS_HARD);
    public static final String LOC_STRICTNESS_SOFT = "SOFT";
    public static final Arg LOC_STRICTNESS_SOFT_ARG =
                Arg.newString(LOC_STRICTNESS_SOFT);

    public static final String LOC_ACCURACY_RANK = "RANK";
    public static final Arg LOC_ACCURACY_RANK_ARG =
                Arg.newString(LOC_ACCURACY_RANK);
    public static final String LOC_ACCURACY_NODE = "NODE";
    public static final Arg LOC_ACCURACY_NODE_ARG =
                Arg.newString(LOC_ACCURACY_NODE);

    /**
     * Called to assert that bad types didn't make it past typechecking
     */
    public void assertInternalTypesValid() {
      for (TaskPropKey key: this.keySet()) {
        Arg val = this.get(key);
        Type act = val.type();
        Type expected = internalTypeMap.get(key);
        if(!act.assignableTo(expected)) {
          throw new STCRuntimeError("Expected type " + expected + " for "
                  + key + " but got " + act);
        }
      }
    }

    @Override
    public TaskProps clone() {
      return (TaskProps)super.clone();
    }

    /**
     * Get value.  If key not present or null, return default
     * @param key
     * @return
     */
    public Arg getWithDefault(TaskPropKey key) {
      Arg val = this.get(key);
      if (val != null) {
        return val;
      }

      // Return default
      switch (key) {
        case LOC_RANK:
          return Location.ANY_LOCATION;
        case LOC_STRICTNESS:
          return LOC_STRICTNESS_HARD_ARG;
        case LOC_ACCURACY:
          return LOC_ACCURACY_RANK_ARG;
        default:
          throw new STCRuntimeError("Unknown default value for "
              + key);
      }
    }

    /**
     * Make copy with only specified keys
     * @param keys
     * @return
     */
    public TaskProps filter(TaskPropKey... keys) {
      TaskProps res = new TaskProps();
      for (TaskPropKey key: keys) {
        if (this.containsKey(key)) {
          res.put(key, this.get(key));
        }
      }
      return res;
    }
  }

  /**
   * Keys identifying the task properties
   */
  public static enum TaskPropKey {
    PRIORITY,
    PARALLELISM,
    LOC_RANK, /* Integer rank */
    /* Soft or hard location constraint, runtime value must be one of constants
     * hardcoded in file */
    LOC_STRICTNESS,
    /* Location accuracy, runtime value must be one of constants hardcoded
     * in file */
    LOC_ACCURACY,
  }

  /** Required types for properties at language level */
  private static final Map<TaskPropKey, Type> frontendTypeMap = initFrontendTypeMap();

  /** Required types for properties internally */
  private static final Map<TaskPropKey, Type> internalTypeMap = initInternalTypeMap();

  private static Map<TaskPropKey, Type> initFrontendTypeMap() {
    Map<TaskPropKey, Type> res = new HashMap<TaskPropKey, Type>();
    res.put(TaskPropKey.PRIORITY, Types.F_INT);
    res.put(TaskPropKey.PARALLELISM, Types.F_INT);
    res.put(TaskPropKey.LOC_RANK, Types.F_INT);
    res.put(TaskPropKey.LOC_ACCURACY, Types.F_LOC_ACCURACY);
    res.put(TaskPropKey.LOC_STRICTNESS, Types.F_LOC_STRICTNESS);
    return res;
  }

  private static Map<TaskPropKey, Type> initInternalTypeMap() {
    Map<TaskPropKey, Type> res = new HashMap<TaskPropKey, Type>();
    res.put(TaskPropKey.PRIORITY, Types.V_INT);
    res.put(TaskPropKey.PARALLELISM, Types.V_INT);
    res.put(TaskPropKey.LOC_RANK, Types.V_INT);
    res.put(TaskPropKey.LOC_ACCURACY, Types.V_LOC_ACCURACY);
    res.put(TaskPropKey.LOC_STRICTNESS, Types.V_LOC_STRICTNESS);
    return res;
  }

  public static Type checkFrontendType(Context context, TaskPropKey key, Type actual)
      throws TypeMismatchException {
    Type exp = frontendTypeMap.get(key);
    if (!actual.assignableTo(exp)) {
      String msg = "Expected task property " + key.toString().toLowerCase()
              + " to have type " + exp.typeName()
              + " but had type " + actual.typeName();
      throw new TypeMismatchException(context, msg);
    }
    return exp;
  }
}
