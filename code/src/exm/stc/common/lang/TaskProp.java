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
        Type act = this.get(key).type();
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
  }
  
  /**
   * Keys identifying the task properties
   */
  public static enum TaskPropKey {
    PRIORITY,
    PARALLELISM,
    TARGET,
  }
  
  /** Required types for properties at language level */
  private static final Map<TaskPropKey, Type> frontendTypeMap = initFrontendTypeMap();
  
  /** Required types for properties internally */
  private static final Map<TaskPropKey, Type> internalTypeMap = initInternalTypeMap();
  
  private static Map<TaskPropKey, Type> initFrontendTypeMap() {
    Map<TaskPropKey, Type> res = new HashMap<TaskPropKey, Type>();
    res.put(TaskPropKey.PRIORITY, Types.F_INT);
    res.put(TaskPropKey.PARALLELISM, Types.F_INT);
    res.put(TaskPropKey.TARGET, Types.F_LOCATION);
    return res;
  }
  
  private static Map<TaskPropKey, Type> initInternalTypeMap() {
    Map<TaskPropKey, Type> res = new HashMap<TaskPropKey, Type>();
    res.put(TaskPropKey.PRIORITY, Types.V_INT);
    res.put(TaskPropKey.PARALLELISM, Types.V_INT);
    res.put(TaskPropKey.TARGET, Types.V_LOCATION);
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
