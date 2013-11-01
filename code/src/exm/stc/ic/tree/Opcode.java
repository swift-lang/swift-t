package exm.stc.ic.tree;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;

public enum Opcode {
  FAKE, // Used for ComputedValue if there isn't a real opcode
  COMMENT, // Comment in IR
  
  // Load and store primitives from value to future
  STORE_INT, STORE_STRING, STORE_FLOAT, STORE_BOOL, STORE_REF,
  STORE_BLOB, STORE_VOID, STORE_FILE,
  LOAD_INT, LOAD_STRING, LOAD_FLOAT, LOAD_BOOL, LOAD_REF,
  LOAD_BLOB, LOAD_VOID, LOAD_FILE,
  
  // Load and store container contents (TODO: recursively?)
  STORE_ARRAY, STORE_BAG,
  LOAD_ARRAY, LOAD_BAG, 
  LOAD_RECURSIVE,
  
  // Dereference *prim to prim
  DEREF_INT, DEREF_STRING, DEREF_FLOAT, DEREF_BOOL, DEREF_BLOB,
  DEREF_VOID, DEREF_FILE, 
  
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
  LOOKUP_CHECKPOINT, WRITE_CHECKPOINT,
  
  // Pack and unpack values into blob
  PACK_VALUES, UNPACK_VALUES;
  
  public boolean isAssign() {
    switch (this) {
      case STORE_BLOB:
      case STORE_BOOL:
      case STORE_FILE:
      case STORE_FLOAT: 
      case STORE_INT:
      case STORE_REF: 
      case STORE_STRING: 
      case STORE_VOID:
      case STORE_ARRAY:
      case STORE_BAG:
        return true;
      default:
        return false;
    }
  }

  public static Opcode assignOpcode(Typed dstType) {
    Opcode op = null;
    if (Types.isPrimFuture(dstType.type())) {
       switch(dstType.type().primType()) {
       case BOOL:
         op = Opcode.STORE_BOOL;
         break;
       case INT:
         op = Opcode.STORE_INT;
         break;
       case FLOAT:
         op = Opcode.STORE_FLOAT;
         break;
       case STRING:
         op = Opcode.STORE_STRING;
         break;
       case BLOB:
         op = Opcode.STORE_BLOB;
         break;
       case VOID:
         op = Opcode.STORE_VOID;
         break;
       case FILE:
         op = Opcode.STORE_FILE;
         break;
       default:
         throw new STCRuntimeError("don't know how to assign " + dstType);
       }
    } else if (Types.isRef(dstType)) {
      op = Opcode.STORE_REF;
    } else if (Types.isArray(dstType)) {
      op = Opcode.STORE_ARRAY;
    } else if (Types.isBag(dstType)) {
      op = Opcode.STORE_BAG;
    }
    return op;
  }
  
  public boolean isRetrieve() {
    switch (this) {
    case LOAD_BLOB:
    case LOAD_BOOL:
    case LOAD_FILE:
    case LOAD_FLOAT: 
    case LOAD_INT:
    case LOAD_REF:
    case LOAD_STRING:
    case LOAD_VOID:
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
    if (Types.isPrimFuture(srcType.type())) {
      switch(srcType.type().primType()) {
      case BOOL:
        op = Opcode.LOAD_BOOL;
        break;
      case INT:
        op = Opcode.LOAD_INT;
        break;
      case FLOAT:
        op = Opcode.LOAD_FLOAT;
        break;
      case STRING:
        op = Opcode.LOAD_STRING;
        break;
      case BLOB:
        op = Opcode.LOAD_BLOB;
        break;
      case VOID:
        op = Opcode.LOAD_VOID;
        break;
      case FILE:
        op = Opcode.LOAD_FILE;
        break;
      default:
        // Can't retrieve other types
        op = null;
      }

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
      case DEREF_BLOB:
      case DEREF_BOOL:
      case DEREF_FILE:
      case DEREF_FLOAT:
      case DEREF_INT:
      case DEREF_STRING:
      case DEREF_VOID:
        return true;
      default:
        return false;
    }
  }

  public static Opcode derefOpCode(Typed type) {
    if (Types.isRef(type)) {
      Type refedType = type.type().memberType();
      if (Types.isPrimFuture(refedType)) {
        switch (refedType.primType()) {
        case BLOB:
          return Opcode.DEREF_BLOB;
        case FILE:
          return Opcode.DEREF_FILE;
        case VOID:
          return Opcode.DEREF_VOID;
        case BOOL:
          return Opcode.DEREF_BOOL;
        case FLOAT:
          return Opcode.DEREF_FLOAT;
        case INT:
          return Opcode.DEREF_INT;
        case STRING:
          return Opcode.DEREF_STRING;
        }
      }
    }
    return null;
  }

}