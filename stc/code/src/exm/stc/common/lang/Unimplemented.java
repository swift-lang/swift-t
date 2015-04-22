package exm.stc.common.lang;

import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;

/**
 * Track unimplemented features in a single place
 */
public class Unimplemented {


  /**
   * Check if an array supports subscript aliasing
   * @return
   */
  public static boolean subscriptAliasSupported(Typed arr) {
    Type keyType = Types.arrayKeyType(arr);
    if (Types.isInt(keyType) || Types.isBool(keyType) ||
        Types.isFloat(keyType)) {
      // Types with a representation that can be serialized as Tcl variable
      // handle correctly
      return true;
    } else {
      return false;
    }
  }

  public static FnID makeFunctionID(String originalName) {
    // TODO: need to implement proper mapping mechanisms
    return new FnID(originalName, originalName);
  }
}
