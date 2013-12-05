package exm.stc.ic.tree;

import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;

public enum Opcode {
  FAKE, // Used for ComputedValue if there isn't a real opcode
  COMMENT, // Comment in IR
  
  // Load and store primitives from value to future
  STORE_SCALAR, STORE_FILE, STORE_REF,
  LOAD_SCALAR, LOAD_FILE, LOAD_REF,
  
  // Load and store container contents (TODO: recursively?)
  STORE_ARRAY, STORE_BAG,
  LOAD_ARRAY, LOAD_BAG, 
  STORE_RECURSIVE, LOAD_RECURSIVE,
  
  // Dereference *prim to prim
  DEREF_SCALAR, DEREF_FILE, 
  
  // Copy reference (i.e. create alias)
  COPY_REF,
  
  // Call Swift functions
  CALL_CONTROL, CALL_SYNC, CALL_LOCAL, CALL_LOCAL_CONTROL,
  
  // Call foreign functions
  CALL_FOREIGN, CALL_FOREIGN_LOCAL,
  
  // Builtin operations (e.g. arithmetic)
  LOCAL_OP, ASYNC_OP,
  
  // Run external program
  RUN_EXTERNAL,
  
  // Manage reference counts of datastore variables
  INCR_READERS, DECR_READERS, DECR_WRITERS, INCR_WRITERS,
  
  // Manage reference counts/free local variables
  DECR_LOCAL_FILE_REF, FREE_BLOB,
  
  // Init and update updateable variables
  INIT_UPDATEABLE_FLOAT, UPDATE_MIN, UPDATE_INCR, UPDATE_SCALE, LATEST_VALUE,
  UPDATE_MIN_IMM, UPDATE_INCR_IMM, UPDATE_SCALE_IMM,
  
  // Loop control flow
  LOOP_BREAK, LOOP_CONTINUE,
  
  // Lookup things in arrays
  ARRAYREF_LOOKUP_FUTURE, ARRAY_LOOKUP_FUTURE,
  ARRAYREF_LOOKUP_IMM, ARRAY_LOOKUP_REF_IMM, ARRAY_LOOKUP_IMM,
  
  // Create full array in one shot
  ARRAY_BUILD,
  
  // Insert things into arrays
  ARRAY_INSERT_FUTURE, ARRAY_DEREF_INSERT_FUTURE, 
  ARRAY_INSERT_IMM, ARRAY_DEREF_INSERT_IMM, 
  ARRAYREF_INSERT_FUTURE, ARRAYREF_DEREF_INSERT_FUTURE,
  ARRAYREF_INSERT_IMM, ARRAYREF_DEREF_INSERT_IMM,
  
  // Create nested arrays 
  ARRAY_CREATE_NESTED_FUTURE, ARRAYREF_CREATE_NESTED_FUTURE,
  ARRAY_CREATE_NESTED_IMM, ARRAYREF_CREATE_NESTED_IMM,
  
  // Insert into bag
  BAG_INSERT, 
  // Create new bag inside array
  ARRAY_CREATE_BAG,
  
  // Init field in struct
  STRUCT_INIT_FIELD,
  // Lookup in local struct
  STRUCT_LOOKUP,
  // Lookup in reference to struct
  STRUCTREF_LOOKUP,
  
  // Manipulate filenames
  GET_FILENAME, SET_FILENAME_VAL, CHOOSE_TMP_FILENAME, IS_MAPPED,
  // Dummy opcode to indicate value of file
  GET_FILENAME_VAL,
  // Manage local files
  INIT_LOCAL_OUTPUT_FILE, GET_LOCAL_FILENAME,
  // Physical copy of file
  COPY_FILE_CONTENTS,
  
  // Lookup and write checkpoints
  CHECKPOINT_WRITE_ENABLED, CHECKPOINT_LOOKUP_ENABLED,
  LOOKUP_CHECKPOINT, WRITE_CHECKPOINT,
  
  // Pack and unpack values into blob
  PACK_VALUES, UNPACK_VALUES,
  
  // Unpack array into flat representation
  UNPACK_ARRAY_TO_FLAT,
  ;
  
  public boolean isAssign() {
    switch (this) {
      case STORE_SCALAR:
      case STORE_FILE:
      case STORE_ARRAY:
      case STORE_BAG:
      case STORE_RECURSIVE:
        return true;
      default:
        return false;
    }
  }

  public static Opcode assignOpcode(Typed dstType) {
    return assignOpcode(dstType, false);
  }
  
  public static Opcode assignOpcode(Typed dstType, boolean recursive) {
    Opcode op = null;
    if (Types.isScalarFuture(dstType)) {
      op = Opcode.STORE_SCALAR;
    } else if (Types.isFile(dstType)) {
      op = Opcode.STORE_FILE;
    } else if (Types.isRef(dstType)) {
      op = Opcode.STORE_REF;
    } else if (Types.isArray(dstType)) {
      if (recursive) {
        op = STORE_RECURSIVE;
      } else {
        op = Opcode.STORE_ARRAY;
      }
    } else if (Types.isBag(dstType)) {
      if (recursive) {
        op = STORE_RECURSIVE;
      } else {
        op = Opcode.STORE_BAG;
      }
    }
    return op;
  }
  
  public boolean isRetrieve() {
    switch (this) {
    case LOAD_SCALAR:
    case LOAD_FILE:
    case LOAD_REF:
    case LOAD_ARRAY:
    case LOAD_BAG:
    case LOAD_RECURSIVE:
      return true;
    default:
      return false;
  }
  }

  public static Opcode retrieveOpcode(Typed srcType) {
    return retrieveOpcode(srcType, false);
  }
  
  public static Opcode retrieveOpcode(Typed srcType,
                                        boolean recursive) {
    Opcode op;
    if (Types.isScalarFuture(srcType)) {
      op = Opcode.LOAD_SCALAR;
    } else if (Types.isFile(srcType)) {
      op = Opcode.LOAD_FILE;
    } else if (Types.isRef(srcType)) {
      op = Opcode.LOAD_REF;
    } else if (Types.isArray(srcType)) {
      if (recursive) {
        op = Opcode.LOAD_RECURSIVE;
      } else {
        op = Opcode.LOAD_ARRAY;
      }
    } else if (Types.isBag(srcType)) {
      if (recursive) {
        op = Opcode.LOAD_RECURSIVE;
      } else {
        op = Opcode.LOAD_BAG;
      }
    } else {
      op = null;
    }
    return op;
  }
  

  public boolean isDeref() {
    switch (this) {
      case DEREF_SCALAR:
      case DEREF_FILE:
        return true;
      default:
        return false;
    }
  }

  public static Opcode derefOpCode(Typed type) {
    if (Types.isRef(type)) {
      Type refedType = type.type().memberType();
      if (Types.isScalarFuture(refedType)) {
        return Opcode.DEREF_SCALAR;
      } else if (Types.isFile(refedType)) {
        return Opcode.DEREF_FILE;
      }
    }
    return null;
  }

}