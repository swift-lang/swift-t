package exm.stc.ic.aliases;

import java.util.Collections;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Var;

/**
 * Data class to store alias info about an alias that was created
 */
public class Alias {

  public static final List<Alias> NONE = Collections.emptyList();

  public final Var parent;
  public final List<String> fieldPath;
  public final boolean derefed;
  public final Var child;

  public Alias(Var parent, List<String> fieldPath, boolean derefed, Var child) {
    super();
    this.parent = parent;
    this.fieldPath = fieldPath;
    this.derefed = derefed;
    this.child = child;
  }

  public List<Alias> asList() {
    return Collections.singletonList(this);
  }

  /**
   * Helper to build appropriate alias for struct
   * 
   * @param struct
   * @param fieldPath
   * @param val
   * @param derefed
   *          if val is dereferenced type for struct field
   * @return
   */
  public static List<Alias> makeStructAliases(Var struct, List<String> fieldPath,
      Var val, boolean derefed) {
    if (derefed) {
      // Value of field - only relevant if field is a reference
      if (fieldIsRef(struct, fieldPath)) {
        // val is the value of the reference
        return new Alias(struct, fieldPath, true, val).asList();
      } else {
        // Value of field - not an alias
        return Alias.NONE;
      }
  
    } else {
      // Straightforward alias of field
      return new Alias(struct, fieldPath, false, val).asList();
    }
  }

  public static List<Alias> makeStructAliases2(Var struct, List<Arg> fieldPath,
      Var val, boolean derefed) {
    return makeStructAliases(struct, Arg.extractStrings(fieldPath), val,
                             derefed);
  }

  /**
   * Return true if field of struct is a reference
   * 
   * @param struct
   * @param fieldPath
   * @return
   */
  private static boolean fieldIsRef(Typed struct, List<String> fieldPath) {
    StructType type = (StructType) struct.type().getImplType();
    Type fieldType;
    try {
      fieldType = type.getFieldTypeByPath(fieldPath);
    } catch (TypeMismatchException e) {
      // Should only happen if we allowed bad IR to get generated
      throw new STCRuntimeError(e.getMessage());
    }
    assert (fieldType != null);
    return Types.isRef(fieldType);
  }

}