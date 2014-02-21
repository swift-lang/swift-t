package exm.stc.frontend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.WaitVar;

/**
 * This class contains logic for translating frontend variables to
 * middle/backend variables.  For example, frontend variables will have
 * additional logical type information that is not relevant to the
 * actual implementation or optimisation.  We also have the option
 * of representing the same logical variable in different ways.
 */
public class VarRepr {

  public static Var backendVar(Var frontendVar) {
    assert(frontendVar != null);
    return frontendVar.substituteType(backendType(frontendVar.type()));
  }
  
  public static List<Var> backendVars(Var ...frontendVars) {
    return backendVars(Arrays.asList(frontendVars));
  }
  
  public static List<Var> backendVars(List<Var> frontendVars) {
    ArrayList<Var> result = new ArrayList<Var>(frontendVars.size());
    for (Var v: frontendVars) {
      result.add(backendVar(v));
    }
    return result;
  }
  
  public static List<WaitVar> backendWaitVars(List<WaitVar> frontendVars) {
    ArrayList<WaitVar> result = new ArrayList<WaitVar>(frontendVars.size());
    for (WaitVar v: frontendVars) {
      result.add(backendWaitVar(v));
    }
    return result;
  }

  public static Arg backendArg(Var frontendVar) {
    return backendVar(frontendVar).asArg();
  }

  public static WaitVar backendWaitVar(WaitVar frontendVar) {
    return new WaitVar(backendVar(frontendVar.var),
                        frontendVar.explicit);
  }
  
  public static Arg backendArg(Arg frontendArg) {
    return backendArg(frontendArg, false);
  }
  
  public static Arg backendArg(Arg frontendArg,
            boolean passThroughNulls) {
    if (frontendArg == null) {
      if (passThroughNulls) {
        return null;
      } else {
        throw new STCRuntimeError("argument was null");
      }
    }
    if (frontendArg.isVar()) {
      return backendArg(frontendArg.getVar());
    } else {
      // Constants don't change
      return frontendArg;
    }
  }
  
  public static List<Arg> backendArgs(Arg ...frontendArgs) {
    return backendArgs(Arrays.asList(frontendArgs));
  }
  
  public static List<Arg> backendArgs(List<Arg> frontendArgs) {
    ArrayList<Arg> result = new ArrayList<Arg>(frontendArgs.size());
    for (Arg v: frontendArgs) {
      result.add(backendArg(v));
    }
    return result;
  }
  
  public static TaskProps backendProps(TaskProps props) {
    TaskProps res = new TaskProps();
    for (Entry<TaskPropKey, Arg> e: props.entrySet()) {
      res.put(e.getKey(), backendArg(e.getValue()));
    }
    return res;
  }
  
  /**
   * Convert a frontend logical type used for typechecking and user-facing
   * messages to a backend type used for implementation 
   * @param type
   * @return
   */
  public static Type backendType(Type type) {
    assert(type.isConcrete()) : type;
    
    // Remove any subtype info, etc
    return backendTypeInternal(type.getImplType());
  }

  /**
   * Internal backend type
   * @param type a type that has had implType applied already
   * @return
   */
  private static Type backendTypeInternal(Type type) {
    // TODO: also need to search recursively through structs, etc
    if (Types.isContainer(type) || Types.isContainerRef(type)) {
      Type frontendElemType = Types.containerElemType(type);
      Type backendElemType = backendTypeInternal(frontendElemType);
      if (storeRefInContainer(backendElemType)) {
        type = Types.substituteElemType(type, new RefType(backendElemType));
      }
    } else if (Types.isRef(type)) {
      Type frontendDerefT = type.memberType();
      Type backendDerefT = backendType(frontendDerefT);
      if (!frontendDerefT.equals(backendDerefT)) {
        return new RefType(backendDerefT);
      }
    }
    return type;
  }
  
  
  /**
   * Whether to store a container value as a reference to data elsewhere
   * @param type
   * @return
   */
  public static boolean storeRefInContainer(Typed type) {
    if (Types.isContainer(type) || Types.isBlob(type)) {
      // Typically large types are stored separately
      return true;
    } else if (Types.isFile(type)) {
      // File is actually reference to two things
      return true;
    } else {
      return false;
    }
  }

  public static FunctionType backendFnType(FunctionType frontendType) {
    // TODO: translate input and output arg types
    return frontendType;
  }
  

  /**
   * The type of internal storage used for a Swift type when stored in a
   * container.
   *
   * @param memberType
   *          the type of array members for the array being dereferenced
   * @return
   */
  public static Type fieldRepr(Type memberType) {
    if (storeRefInContainer(memberType)) {
      return new RefType(memberType);
    } else {
      return memberType;
    }
  }
}