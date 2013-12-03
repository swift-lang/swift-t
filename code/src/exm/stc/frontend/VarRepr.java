package exm.stc.frontend;

import java.util.ArrayList;
import java.util.List;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.common.lang.Types.RefType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Var;

/**
 * This class contains logic for translating frontend variables to
 * middle/backend variables.  For example, frontend variables will have
 * additional logical type information that is not relevant to the
 * actual implementation or optimisation.  We also have the option
 * of representing the same logical variable in different ways.
 */
public class VarRepr {

  public static Var backendVar(Var frontendVar) {
    return frontendVar.substituteType(backendType(frontendVar.type()));
  }

  public static List<Var> backendVars(List<Var> frontendVars) {
    ArrayList<Var> result = new ArrayList<Var>(frontendVars.size());
    for (Var v: frontendVars) {
      result.add(backendVar(v));
    }
    return result;
  }

  public static Arg backendArg(Var frontendVar) {
    return backendVar(frontendVar).asArg();
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
    type = type.getImplType();
    
    // TODO: also need to search recursively through structs, refs, etc
    if (Types.isContainer(type)) {
      Type elemType = Types.containerElemType(type);
      if (storeRefInContainer(elemType)) {
        type = Types.substituteElemType(type, new RefType(elemType));
      }
    }
    
    // Default is the same
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
    } else {
      return false;
    }
  }

  public static FunctionType backendFnType(FunctionType frontendType) {
    // TODO: translate input and output arg types
    return frontendType;
  }
}
