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

  /**
   * String to add to path to indicate that it's a dereferenced value.
   * Note: we assume that this isn't a valid struct field name
   */
  public static final String DEREF_MARKER = "*";

  /**
   * Filename of a file
   */
  public static final String FILENAME = "filename";
  public static final List<String> FILENAME_PATH = 
                          Collections.singletonList(FILENAME);

  public static final String UNKNOWN = null;
  public static final String ARRAY_SUBSCRIPT = UNKNOWN;
  
  public final Var parent;
  
  /**
   * Path of fields
   * null == UNKNOWN
   */
  public final List<String> fieldPath;
  public final AliasTransform transform;
  public final Var child;

  public Alias(Var parent, List<String> fieldPath,
               AliasTransform transform, Var child) {
    super();
    this.parent = parent;
    this.fieldPath = fieldPath;
    this.transform = transform;
    this.child = child;
  }

  public List<Alias> asList() {
    return Collections.singletonList(this);
  }

  public static enum AliasTransform {
    IDENTITY, // Just a plain alias
    RETRIEVE, // Retrieved value of field
    COPY, // Copied field
  }

  public static List<Alias> makeArrayAlias(Var arr, Arg ix, Var alias,
      AliasTransform transform) {
    if (transform == AliasTransform.RETRIEVE ||
        transform == AliasTransform.COPY) {
      // Don't handle
      return NONE;
    } else {
      // Straightforward alias of field, not sensitive to actual subscript
      // TODO: could factor in constant subscripts
      return new Alias(arr, Collections.singletonList(ARRAY_SUBSCRIPT),
                       transform, alias).asList();
    }
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
      Var val, AliasTransform transform) {
    if (transform == AliasTransform.RETRIEVE ||
        transform == AliasTransform.COPY) {
      // Value or copy of field - only relevant if field is a reference
      if (fieldIsRef(struct, fieldPath)) {
        return new Alias(struct, fieldPath, transform, val).asList();
      } else {
        // Value of field - not an alias
        return Alias.NONE;
      }
    } else {
      // Straightforward alias of field
      return new Alias(struct, fieldPath, transform, val).asList();
    }
  }

  public static List<Alias> makeStructAliases2(Var struct, List<Arg> fieldPath,
      Var val, AliasTransform transform) {
    return makeStructAliases(struct, Arg.extractStrings(fieldPath), val,
                             transform);
  }

  /**
   * Return true if field of struct is a reference
   * 
   * @param struct
   * @param fieldPath
   * @return
   */
  public static boolean fieldIsRef(Typed struct, List<String> fieldPath) {
    assert(Types.isStruct(struct) || Types.isStructRef(struct));
    if (Types.isStructRef(struct)) {
      struct = Types.retrievedType(struct);
    }
    StructType type = (StructType) struct.type().getImplType();
    Type fieldType;
    try {
      fieldType = type.fieldTypeByPath(fieldPath);
    } catch (TypeMismatchException e) {
      // Should only happen if we allowed bad IR to get generated
      throw new STCRuntimeError(e.getMessage());
    }
    assert (fieldType != null);
    return Types.isRef(fieldType);
  }

}