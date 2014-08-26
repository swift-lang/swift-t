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
        case LOCATION:
          return Location.ANY_LOCATION;
        case SOFT_LOCATION:
          return Arg.FALSE; // Default is hard location targeting
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
    LOCATION,
    SOFT_LOCATION, /* Boolean flag for soft or hard location constraint */
  }

  /** Required types for properties at language level */
  private static final Map<TaskPropKey, Type> frontendTypeMap = initFrontendTypeMap();

  /** Required types for properties internally */
  private static final Map<TaskPropKey, Type> internalTypeMap = initInternalTypeMap();

  private static Map<TaskPropKey, Type> initFrontendTypeMap() {
    Map<TaskPropKey, Type> res = new HashMap<TaskPropKey, Type>();
    res.put(TaskPropKey.PRIORITY, Types.F_INT);
    res.put(TaskPropKey.PARALLELISM, Types.F_INT);
    res.put(TaskPropKey.LOCATION, Types.F_LOCATION);
    res.put(TaskPropKey.SOFT_LOCATION, Types.F_BOOL);
    return res;
  }

  private static Map<TaskPropKey, Type> initInternalTypeMap() {
    Map<TaskPropKey, Type> res = new HashMap<TaskPropKey, Type>();
    res.put(TaskPropKey.PRIORITY, Types.V_INT);
    res.put(TaskPropKey.PARALLELISM, Types.V_INT);
    res.put(TaskPropKey.LOCATION, Types.V_LOCATION);
    res.put(TaskPropKey.SOFT_LOCATION, Types.V_BOOL);
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
