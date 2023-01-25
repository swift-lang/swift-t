package exm.stc.frontend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.WaitVar;

/**
 * This class contains logic for translating frontend variables to
 * middle/backend variables.  For example, frontend variables will have
 * additional logical type information that is not relevant to the
 * actual implementation or optimization.  We also have the option
 * of representing the same logical variable in different ways.
 */
public class VarRepr {

  /**
   * Cache results of conversions, to avoid recomputing.
   */
  private static HashMap<Type, Type> conversionCache
                          = new HashMap<Type, Type>();

  public static Var backendVar(Var frontendVar) {
    assert(frontendVar != null);
    Type backendT = backendType(frontendVar.type(), true);
    return frontendVar.substituteType(backendT);
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
                               boolean passThroughNulls)
  {
    Arg result = null;
    if (frontendArg == null)
      if (passThroughNulls)
        return null;
      else
        throw new STCRuntimeError("argument was null");

    if (frontendArg.isVar())
      result = backendArg(frontendArg.getVar());
    else
      // Constants don't change
      result = frontendArg;

    return result;
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
    TaskProps result = new TaskProps();
    for (Entry<TaskPropKey, Arg> e: props.entrySet()) {
      result.put(e.getKey(), backendArg(e.getValue()));
    }
    return result;
  }

  /**
   * Convert a frontend logical type used for typechecking and user-facing
   * messages to a backend type used for implementation
   * @param type
   * @param checkInstantiate if true, expect to be able to instantiate type
   * @return
   */
  public static Type backendType(Type type, boolean checkInstantiate) {
    // Remove any subtype info, etc
    return backendTypeInternal(type.getImplType(), checkInstantiate);
  }

  /**
   * Internal backend type
   * @param type a type that has had implType applied already
   * @param checkInstantiate if true, expect to be able to instantiate type
   * @return
   */
  private static Type backendTypeInternal(Type type,
                            boolean checkInstantiate) {
    Type originalType = type;

    Type lookup = conversionCache.get(type);
    if (lookup != null) {
      return lookup;
    }

    if (Types.isContainer(type) || Types.isContainerRef(type) ||
        Types.isContainerLocal(type)) {
      Type frontendElemType = Types.containerElemType(type);
      Type backendElemType = backendTypeInternal(frontendElemType,
                                                 checkInstantiate);
      if (storeRefInContainer(backendElemType)) {
        type = Types.substituteElemType(type, new RefType(backendElemType, true));
      }
    } else if (Types.isRef(type)) {
      Type frontendDerefT = type.memberType();
      Type backendDerefT = backendTypeInternal(frontendDerefT,
                                               checkInstantiate);
      if (!frontendDerefT.equals(backendDerefT)) {
        type = new RefType(backendDerefT, Types.isMutableRef(type));
      }
    } else if (Types.isStruct(type) || Types.isStructLocal(type)) {
      type = backendStructType((StructType)type, checkInstantiate);
    }

    assert(!checkInstantiate || type.isConcrete()) :
            "Cannot instantiate type " + type;

    Logging.getSTCLogger().trace("Type conversion frontend => backend: " +
                                    originalType + " to backend " + type);
    conversionCache.put(originalType, type);
    return type;
  }

  /**
   * Convert struct type.
   * Retain name, but convert types of fields
   * @param frontend
   * @return
   */
  private static Type backendStructType(StructType frontend,
                                boolean checkInstantiate) {
    List<StructField> backendFields = new ArrayList<StructField>();

    for (StructField frontendF: frontend.fields()) {
      Type fieldT = backendTypeInternal(frontendF.type().getImplType(),
                                        checkInstantiate);
      if (storeRefInStruct(fieldT)) {
        // Need to store as ref to separate data
        fieldT = new RefType(fieldT, true);
      }
      backendFields.add(new StructField(fieldT, frontendF.name()));
    }

    return new StructType(frontend.isLocal(), frontend.typeName(),
                            backendFields);
  }

  /**
   * Whether to store a container value as a reference to data elsewhere
   * @param type
   * @return
   */
  public static boolean storeRefInContainer(Typed type) {
    return storeRefInCompound(type, CompoundType.CONTAINER);
  }

  public static boolean storeRefInStruct(Typed type) {
    return storeRefInCompound(type, CompoundType.STRUCT);
  }

  private static enum CompoundType {
    STRUCT,
    CONTAINER,
  }

  private static boolean storeRefInCompound(Typed type,
                                 CompoundType compound) {
    if (Types.isFile(type) || Types.isStruct(type)) {
      if (compound == CompoundType.CONTAINER) {
        // TODO: would make sense to store directly, but don't have capacity to
        // init/modify individual fields currently in container
        return true;
      } else {
        assert(compound == CompoundType.STRUCT);
        // Store structs, etc directly in struct
        return false;
      }
    } else if (compound == CompoundType.CONTAINER && isBig(type)) {
      // Want to be able to distribute data if many copies stored
      return true;
    } else if (RefCounting.trackWriteRefCount(type.type(), DefType.LOCAL_USER)) {
      // Need to track write refcount separately to manage closing,
      // so must store as ref to separate datum
      return true;
    } else {
      return false;
    }
  }

  /**
   * Data that is likely to be big
   * @param type
   */
  private static boolean isBig(Typed type) {
    return Types.isBlob(type) || Types.isContainer(type);
  }

  public static FunctionType backendFnType(FunctionType frontendType) {

    Type lookup = conversionCache.get(frontendType);
    if (lookup != null) {
      return (FunctionType)lookup;
    }

    // translate input and output arg types
    List<Type> backendInputs = new ArrayList<Type>();
    List<Type> backendOutputs = new ArrayList<Type>();

    for (Type in: frontendType.getInputs()) {
      backendInputs.add(backendType(in, false));
    }

    for (Type out: frontendType.getOutputs()) {
      backendOutputs.add(backendType(out, false));
    }

    FunctionType result = new FunctionType(backendInputs, backendOutputs,
               frontendType.hasVarargs(), frontendType.getTypeVars());

    conversionCache.put(frontendType, result);

    return result;
  }


  public static Type elemRepr(Type memberType, CompoundType c,
                              boolean mutable) {
    if (storeRefInCompound(memberType, c)) {
      return new RefType(memberType, mutable);
    } else {
      return memberType;
    }
  }

  /**
   * The type of internal storage used for a Swift type when stored in a
   * container.
   *
   * @param memberType
   *          the type of array members for the array being dereferenced
   * @return
   */
  public static Type containerElemRepr(Type memberType, boolean mutable) {
    return elemRepr(memberType, CompoundType.CONTAINER, mutable);
  }

  /**
   * The type of internal storage used for a Swift type when stored in a
   * struct.
   *
   * @param memberType the type of struct member
   * @return
   */
  public static Type structElemRepr(Type memberType, boolean mutable) {
    return elemRepr(memberType, CompoundType.STRUCT, mutable);
  }

}
