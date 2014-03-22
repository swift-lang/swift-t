package exm.stc.ic.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Arg.ArgKind;
import exm.stc.common.lang.Operators.UpdateMode;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.VarCount;
import exm.stc.common.util.Counters;
import exm.stc.common.util.Out;
import exm.stc.common.util.Pair;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.ICUtil;
import exm.stc.ic.aliases.Alias;
import exm.stc.ic.aliases.Alias.AliasTransform;
import exm.stc.ic.componentaliases.ComponentAlias;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ValLoc;
import exm.stc.ic.opt.valuenumber.ValLoc.Closed;
import exm.stc.ic.opt.valuenumber.ValLoc.IsAssign;
import exm.stc.ic.opt.valuenumber.ValLoc.IsValCopy;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GenInfo;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;

/**
 * Class to represent builtin Turbine operations with fixed number
 * of arguments
 */
public class TurbineOp extends Instruction {
  
  /** Private constructor: use static methods to create */
  private TurbineOp(Opcode op, List<Var> outputs, List<Arg> inputs) {
    super(op);
    this.outputs = initArgList(outputs);
    this.inputs = initArgList(inputs);
  }
  
  
  private static Class<? extends Object> SINGLETON_LIST =
                           Collections.singletonList(null).getClass();
  /**
   * Initialize args as list that support .set() operation.
   * @param args
   * @return
   */
  private static <T> List<T> initArgList(List<T> args) {
    if (args.isEmpty()) {
      // Nothing will be mutated in list, so use placeholder
      return Collections.emptyList();
    } else if (SINGLETON_LIST.isInstance(args)) {
      // Avoid known-bad list classes
      return new ArrayList<T>(args);
    } else {
      return args;
    }
  }

  private TurbineOp(Opcode op, Var output, Arg ...inputs) {
    this(op, Arrays.asList(output), Arrays.asList(inputs));
  }
  
  private TurbineOp(Opcode op, List<Var> outputs, Arg ...inputs) {
    this(op, outputs, Arrays.asList(inputs));
  }

  private List<Var> outputs; /** Variables that are modified by this instruction */
  private List<Arg> inputs; /** Variables that are read-only */
      
  @Override
  public String toString() {
    String result = op.toString().toLowerCase();
    for (Var o: outputs) {
      result += " " + o.name();
    }
    for (Arg i: inputs) {
      result += " " + i.toString();
    }
    return result;
  }

  @Override
  public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
    // Recreate calls that were used to generate this instruction
    switch (op) {
    case STORE_SCALAR:
      gen.assignScalar(getOutput(0), getInput(0));
      break;
    case STORE_FILE:
      gen.assignFile(getOutput(0), getInput(0), getInput(1));
      break;
    case STORE_REF:
      gen.assignReference(getOutput(0), getInput(0).getVar());
      break;
    case STORE_ARRAY:
      gen.assignArray(getOutput(0), getInput(0));
      break;
    case STORE_BAG:
      gen.assignBag(getOutput(0), getInput(0));
      break;
    case STORE_STRUCT:
      gen.assignStruct(getOutput(0), getInput(0));
      break;
    case STORE_RECURSIVE:
      gen.assignRecursive(getOutput(0), getInput(0));
      break;
    case ARR_RETRIEVE:
      gen.arrayRetrieve(getOutput(0), getInput(0).getVar(),
                         getInput(1));
      break;
    case ARR_COPY_OUT_IMM:
      gen.arrayCopyOutImm(getOutput(0), getInput(0).getVar(), getInput(1));
      break;
    case ARR_COPY_OUT_FUTURE:
      gen.arrayCopyOutFuture(getOutput(0), getInput(0).getVar(),
                             getInput(1).getVar());
      break;
    case AREF_COPY_OUT_IMM:
      gen.arrayRefCopyOutImm(getOutput(0), getInput(0).getVar(), getInput(1));
      break;
    case AREF_COPY_OUT_FUTURE:
      gen.arrayRefCopyOutFuture(getOutput(0), getInput(0).getVar(),
                             getInput(1).getVar());
      break;
    case ARR_CONTAINS:
      gen.arrayContains(getOutput(0), getInput(0).getVar(), getInput(1));
      break;
    case CONTAINER_SIZE:
      gen.containerSize(getOutput(0), getInput(0).getVar());
      break;
    case ARR_LOCAL_CONTAINS:
      gen.arrayLocalContains(getOutput(0), getInput(0).getVar(), getInput(1));
      break;
    case CONTAINER_LOCAL_SIZE:
      gen.containerLocalSize(getOutput(0), getInput(0).getVar());
      break;
    case ARR_STORE:
      gen.arrayStore(getOutput(0), getInput(0), getInput(1),
          getInputs().size() == 3 ? getInput(2) : Arg.ZERO);
      break;
    case ARR_STORE_FUTURE:
      gen.arrayStoreFuture(getOutput(0), getInput(0).getVar(),
                            getInput(1),
                            getInputs().size() == 3 ? getInput(2) : Arg.ONE);
      break;
    case AREF_STORE_IMM:
      gen.arrayRefStoreImm(getOutput(0), getInput(0), getInput(1));
      break;
    case AREF_STORE_FUTURE:
      gen.arrayRefStoreFuture(getOutput(0), getInput(0).getVar(), getInput(1));
      break;
    case ARR_COPY_IN_IMM:
      gen.arrayCopyInImm(getOutput(0), getInput(0), getInput(1).getVar(),
          getInputs().size() == 3 ? getInput(2) : Arg.ONE);
      break;
    case ARR_COPY_IN_FUTURE:
      gen.arrayCopyInFuture(getOutput(0), getInput(0).getVar(),
                            getInput(1).getVar(),
                            getInputs().size() == 3 ? getInput(2) : Arg.ONE);
      break;
    case AREF_COPY_IN_IMM:
      gen.arrayRefCopyInImm(getOutput(0), getInput(0), getInput(1).getVar());
      break;
    case AREF_COPY_IN_FUTURE:
      gen.arrayRefCopyInFuture(getOutput(0),
            getInput(0).getVar(), getInput(1).getVar());
      break;
    case ARRAY_BUILD: {
      assert (getInputs().size() % 2 == 0);
      int elemCount = getInputs().size() / 2;
      List<Arg> keys = new ArrayList<Arg>(elemCount);
      List<Arg> vals = new ArrayList<Arg>(elemCount);
      for (int i = 0; i < elemCount; i++) {
        keys.add(getInput(i * 2));
        vals.add(getInput(i * 2 + 1));
      }
      gen.arrayBuild(getOutput(0), keys, vals);
      break;
    }
    case ASYNC_COPY_CONTAINER: {
      gen.asyncCopyContainer(getOutput(0), getInput(0).getVar());
      break;
    }
    case SYNC_COPY_CONTAINER: {
      gen.syncCopyContainer(getOutput(0), getInput(0).getVar());
      break;
    }
    case ASYNC_COPY_STRUCT: {
      gen.asyncCopyStruct(getOutput(0), getInput(0).getVar());
      break;
    }
    case SYNC_COPY_STRUCT: {
      gen.syncCopyStruct(getOutput(0), getInput(0).getVar());
      break;
    }
    case BAG_INSERT:
      gen.bagInsert(getOutput(0), getInput(0), getInput(1));
      break;
    case STRUCT_CREATE_ALIAS:
      gen.structCreateAlias(getOutput(0), getInput(0).getVar(),
                            Arg.extractStrings(getInputsTail(1)));
      break;
    case STRUCT_RETRIEVE_SUB:
      gen.structRetrieveSub(getOutput(0), getInput(0).getVar(),
                         Arg.extractStrings(getInputsTail(1)));
      break;
    case STRUCT_COPY_OUT:
      gen.structCopyOut(getOutput(0), getInput(0).getVar(),
                         Arg.extractStrings(getInputsTail(1)));
      break;
    case STRUCTREF_COPY_OUT:
      gen.structRefCopyOut(getOutput(0), getInput(0).getVar(),
                          Arg.extractStrings(getInputsTail(1)));
      break;
    case STRUCT_INIT_FIELDS: {
      // Need to unpack variables from flat input list
      Out<List<List<String>>> fieldPaths = new Out<List<List<String>>>();
      Out<List<Arg>> fieldVals = new Out<List<Arg>>();
      Arg writeDecr = unpackStructInitArgs(fieldPaths, null, fieldVals);
      
      gen.structInitFields(getOutput(0), fieldPaths.val, fieldVals.val, writeDecr);
    }
      break;
    case STRUCT_STORE_SUB:
      gen.structStore(getOutput(0), Arg.extractStrings(getInputsTail(1)),
                                    getInput(0));
      break;
    case STRUCT_COPY_IN:
      gen.structCopyIn(getOutput(0), Arg.extractStrings(getInputsTail(1)),
                       getInput(0).getVar());
      break;
    case STRUCTREF_STORE_SUB:
      gen.structRefStoreSub(getOutput(0), Arg.extractStrings(getInputsTail(1)),
                         getInput(0));
      break;
    case STRUCTREF_COPY_IN:
      gen.structRefCopyIn(getOutput(0), Arg.extractStrings(getInputsTail(1)),
                          getInput(0).getVar());
      break;
    case DEREF_SCALAR:
      gen.dereferenceScalar(getOutput(0), getInput(0).getVar());
      break;
    case DEREF_FILE:
      gen.dereferenceFile(getOutput(0), getInput(0).getVar());
      break;
    case LOAD_REF:
      gen.retrieveRef(getOutput(0), getInput(0).getVar(), getInput(1),
            getInput(2), getInputs().size() == 4 ? getInput(3) : Arg.ZERO);
      break;
    case COPY_REF:
      gen.makeAlias(getOutput(0), getInput(0).getVar());
      break;
    case ARR_CREATE_NESTED_FUTURE:
      gen.arrayCreateNestedFuture(getOutput(0), getOutput(1), 
                                  getInput(0).getVar());
      break;
    case AREF_CREATE_NESTED_FUTURE:
      gen.arrayRefCreateNestedFuture(getOutput(0), getOutput(1),
                                     getInput(0).getVar());
      break;
    case AREF_CREATE_NESTED_IMM:
      gen.arrayRefCreateNestedImm(getOutput(0), getOutput(1), getInput(0));
      break;
    case ARR_CREATE_NESTED_IMM:
      gen.arrayCreateNestedImm(getOutput(0), getOutput(1), getInput(0),
                               getInput(1), getInput(2));
      break;
    case ARRAY_CREATE_BAG:
      gen.arrayCreateBag(getOutput(0), getOutput(1), getInput(0),
                         getInput(1), getInput(2));
      break;
    case LOAD_SCALAR:
      gen.retrieveScalar(getOutput(0), getInput(0).getVar(),
          getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case LOAD_FILE:
      gen.retrieveFile(getOutput(0), getInput(0).getVar(),
          getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case LOAD_ARRAY:
      gen.retrieveArray(getOutput(0), getInput(0).getVar(),
              getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case LOAD_STRUCT:
      gen.retrieveStruct(getOutput(0), getInput(0).getVar(),
              getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case LOAD_BAG:
      gen.retrieveBag(getOutput(0), getInput(0).getVar(),
              getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case LOAD_RECURSIVE:
      gen.retrieveRecursive(getOutput(0), getInput(0).getVar(),
              getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case FREE_BLOB:
      gen.freeBlob(getOutput(0));
      break;
    case DECR_LOCAL_FILE_REF:
      gen.decrLocalFileRef(getInput(0).getVar());
      break;
    case INIT_UPDATEABLE_FLOAT:
      gen.initUpdateable(getOutput(0), getInput(0));
      break;
    case LATEST_VALUE:
      gen.latestValue(getOutput(0), getInput(0).getVar());
      break;
    case UPDATE_INCR:
      gen.update(getOutput(0), UpdateMode.INCR, getInput(0).getVar());
      break;
    case UPDATE_MIN:
      gen.update(getOutput(0), UpdateMode.MIN, getInput(0).getVar());
      break;
    case UPDATE_SCALE:
      gen.update(getOutput(0), UpdateMode.SCALE, getInput(0).getVar());
      break;
    case UPDATE_INCR_IMM:
      gen.updateImm(getOutput(0), UpdateMode.INCR, getInput(0));
      break;
    case UPDATE_MIN_IMM:
      gen.updateImm(getOutput(0), UpdateMode.MIN, getInput(0));
      break;
    case UPDATE_SCALE_IMM:
      gen.updateImm(getOutput(0), UpdateMode.SCALE, getInput(0));
      break;
    case GET_FILENAME_ALIAS:
      gen.getFileNameAlias(getOutput(0), getInput(0).getVar());
      break;
    case COPY_IN_FILENAME:
      gen.copyInFilename(getOutput(0), getInput(0).getVar());
      break;
    case GET_LOCAL_FILENAME:
      gen.getLocalFileName(getOutput(0), getInput(0).getVar());
      break;
    case IS_MAPPED:
      gen.isMapped(getOutput(0), getInput(0).getVar());
      break;
    case GET_FILENAME_VAL:
      gen.getFilenameVal(getOutput(0), getInput(0).getVar());
      break;
    case SET_FILENAME_VAL:
      gen.setFilenameVal(getOutput(0), getInput(0));
      break;
    case CHOOSE_TMP_FILENAME:
      gen.chooseTmpFilename(getOutput(0));
      break;
    case INIT_LOCAL_OUTPUT_FILE:
      gen.initLocalOutputFile(getOutput(0), getInput(0), getInput(1));
      break;
    case COPY_FILE_CONTENTS:
      gen.copyFileContents(getOutput(0), getInput(0).getVar());
      break;
    case CHECKPOINT_WRITE_ENABLED:
      gen.checkpointWriteEnabled(getOutput(0));
      break;
    case CHECKPOINT_LOOKUP_ENABLED:
      gen.checkpointLookupEnabled(getOutput(0));
      break;
    case WRITE_CHECKPOINT:
      gen.writeCheckpoint(getInput(0), getInput(1));
      break;
    case LOOKUP_CHECKPOINT:
      gen.lookupCheckpoint(getOutput(0), getOutput(1), getInput(0));
      break;
    case PACK_VALUES:
      gen.packValues(getOutput(0), getInputs());
      break;
    case UNPACK_VALUES:
      gen.unpackValues(getOutputs(), getInput(0));
      break;
    case UNPACK_ARRAY_TO_FLAT:
      gen.unpackArrayToFlat(getOutput(0), getInput(0));
      break;
    default:
      throw new STCRuntimeError("didn't expect to see op " +
                op.toString() + " here");
    }

  }

  /**
   * Look up value of array index immediately
   * @param dst
   * @param arrayVar
   * @param arrIx
   * @return
   */
  public static Instruction arrayRetrieve(Var dst, Var arrayVar,
                                            Arg arrIx) {
    assert(dst.storage() == Alloc.LOCAL || dst.storage() == Alloc.ALIAS);
    assert(Types.isArray(arrayVar));
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(Types.isElemValType(arrayVar, dst));
    
    return new TurbineOp(Opcode.ARR_RETRIEVE,
        dst, arrayVar.asArg(), arrIx);
  }

  /**
   * Copy out value of array once set
   * @param dst
   * @param arrayRefVar
   * @param arrIx
   * @return
   */
  public static Instruction arrayRefCopyOutImm(Var dst,
      Var arrayRefVar, Arg arrIx) {
    assert(Types.isArrayRef(arrayRefVar));
    assert(Types.isArrayKeyVal(arrayRefVar, arrIx));
    assert(Types.isElemType(arrayRefVar, dst));
    assert(!Types.isMutableRef(dst)); // Doesn't acquire write ref
    return new TurbineOp(Opcode.AREF_COPY_OUT_IMM,
        dst, arrayRefVar.asArg(), arrIx);
  }

  /**
   * Copy out value of field from array once set
   * @param dst
   * @param arrayVar
   * @param arrIx
   * @return
   */
  public static Instruction arrayCopyOutImm(Var dst, Var arrayVar,
      Arg arrIx) {
    assert(Types.isArray(arrayVar));
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(Types.isElemType(arrayVar, dst)) : arrayVar + " " + dst;
    assert(!Types.isMutableRef(dst)) : dst; // Doesn't acquire write ref
    return new TurbineOp(Opcode.ARR_COPY_OUT_IMM,
        dst, arrayVar.asArg(), arrIx);
  }

  /**
   * Copy out value of field from array once set
   * @param dst
   * @param arrayVar
   * @param indexVar
   * @return
   */
  public static TurbineOp arrayCopyOutFuture(Var dst, Var arrayVar,
      Var indexVar) {
    assert(Types.isArray(arrayVar));
    assert(Types.isArrayKeyFuture(arrayVar, indexVar));
    assert(Types.isElemType(arrayVar, dst));
    assert(!Types.isMutableRef(dst)); // Doesn't acquire write ref
    return new TurbineOp(Opcode.ARR_COPY_OUT_FUTURE,
        dst, arrayVar.asArg(), indexVar.asArg());
  }

  /**
   * Copy out value of field from array once set
   * @param dst
   * @param arrayRefVar
   * @param indexVar
   * @return
   */
  public static TurbineOp arrayRefCopyOutFuture(Var dst, Var arrayRefVar,
      Var indexVar) {
    assert(Types.isArrayRef(arrayRefVar));
    assert(Types.isArrayKeyFuture(arrayRefVar, indexVar));
    assert(Types.isElemType(arrayRefVar, dst));
    assert(!Types.isMutableRef(dst)); // Doesn't acquire write ref
    return new TurbineOp(Opcode.AREF_COPY_OUT_FUTURE, dst,
                          arrayRefVar.asArg(), indexVar.asArg());
  }
  
  public static Instruction arrayContains(Var out, Var array, Arg ix) {
    assert(Types.isBoolVal(out));
    assert(Types.isArray(array));
    assert(Types.isArrayKeyVal(array, ix));
    return new TurbineOp(Opcode.ARR_CONTAINS, out, array.asArg(), ix);
  }
  
  public static Instruction containerSize(Var out, Var container) {
    assert(Types.isIntVal(out));
    assert(Types.isContainer(container));
    return new TurbineOp(Opcode.CONTAINER_SIZE, out, container.asArg());
  }
  
  public static Instruction arrayLocalContains(Var out, Var array, Arg ix) {
    assert(Types.isBoolVal(out));
    assert(Types.isArrayLocal(array));
    assert(Types.isArrayKeyVal(array, ix));
    return new TurbineOp(Opcode.ARR_LOCAL_CONTAINS, out, array.asArg(), ix);
  }
  
  public static Instruction containerLocalSize(Var out, Var container) {
    assert(Types.isIntVal(out));
    assert(Types.isContainerLocal(container));
    return new TurbineOp(Opcode.CONTAINER_LOCAL_SIZE, out, container.asArg());
  }

  public static Instruction arrayStore(Var array,
      Arg ix, Arg member) {
    assert(Types.isArray(array.type()));
    assert(Types.isArrayKeyVal(array, ix));
    assert(Types.isElemValType(array, member)) :
            member.toStringTyped() + " " + array;
    return new TurbineOp(Opcode.ARR_STORE, array, ix, member);
  }

  public static Instruction arrayStoreFuture(Var array,
      Var ix, Arg member) {
    assert(Types.isArray(array));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(Types.isElemValType(array, member));
    return new TurbineOp(Opcode.ARR_STORE_FUTURE,
            array, ix.asArg(), member);
  }

  /**
   * Store via a mutable array reference
   * @param array
   * @param ix
   * @param member
   * @return
   */
  public static Instruction arrayRefStoreImm(Var array, Arg ix, Arg member) {
    assert(Types.isArrayRef(array, true));
    assert(Types.isArrayKeyVal(array, ix));
    assert(Types.isElemValType(array, member));
    return new TurbineOp(Opcode.AREF_STORE_IMM,
        array, ix, member);
  }

  /**
   * Store via a mutable array reference
   * @param array
   * @param ix
   * @param member
   * @return
   */
  public static Instruction arrayRefStoreFuture(Var array, Var ix, Arg member) {
    assert(Types.isArrayRef(array, true));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(Types.isElemValType(array, member));
    return new TurbineOp(Opcode.AREF_STORE_FUTURE,
        array, ix.asArg(), member);
  }

  /**
   * Copy a value into an array member
   * @param array
   * @param ix
   * @param member
   * @return
   */
  public static Instruction arrayCopyInImm(Var array,
      Arg ix, Var member) {
    assert(Types.isArray(array));
    assert(Types.isArrayKeyVal(array, ix));
    assert(Types.isElemType(array, member));
    
    return new TurbineOp(Opcode.ARR_COPY_IN_IMM,
                         array, ix, member.asArg());
  }

  /**
   * Copy a value into an array member
   * @param array
   * @param ix
   * @param member
   * @return
   */
  public static Instruction arrayCopyInFuture(Var array, Var ix, Var member) {
    assert(Types.isArray(array.type()));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(Types.isElemType(array, member));
    return new TurbineOp(Opcode.ARR_COPY_IN_FUTURE, array, ix.asArg(),
                          member.asArg());
  }

  /**
   * Copy in a value to an array reference
   * @param outerArray
   * @param array
   * @param ix
   * @param member
   * @return
   */
  public static Instruction arrayRefCopyInImm(Var array, Arg ix, Var member) {
    assert(Types.isArrayKeyVal(array, ix));
    assert(Types.isArrayRef(array, true));
    assert(Types.isElemType(array, member));
    return new TurbineOp(Opcode.AREF_COPY_IN_IMM,
                         array, ix, member.asArg());
  }
  
  public static Instruction arrayRefCopyInFuture(Var array, Var ix,
                                                  Var member) {
    assert(Types.isArrayKeyFuture(array, ix));
    assert(Types.isArrayRef(array, true));
    assert(Types.isElemType(array, member));
    return new TurbineOp(Opcode.AREF_COPY_IN_FUTURE,
                         array, ix.asArg(), member.asArg());
  }

  /**
   * Build an array in one hit.
   * @param array
   * @param keys key values for array (NOT futures)
   * @param vals
   */
  public static Instruction arrayBuild(Var array, List<Arg> keys, List<Arg> vals) {
    assert(Types.isArray(array.type()));    
    int elemCount = keys.size();
    assert(vals.size() == elemCount);
    
    ArrayList<Arg> inputs = new ArrayList<Arg>(elemCount * 2);
    for (int i = 0; i < elemCount; i++) {
      Arg key = keys.get(i);
      Arg val = vals.get(i);
      assert(Types.isArrayKeyVal(array, key));
      assert(Types.isElemValType(array, val));
      
      inputs.add(key);
      inputs.add(val);
    }
    return new TurbineOp(Opcode.ARRAY_BUILD, array.asList(), inputs);
  }
  
  public static Instruction asyncCopyContainer(Var dst, Var src) {
    assert(Types.isContainer(dst));
    assert(src.type().assignableTo(dst.type()));
    return new TurbineOp(Opcode.ASYNC_COPY_CONTAINER, dst, src.asArg());
  }
  
  public static Instruction syncCopyContainer(Var dst, Var src) {
    assert(Types.isContainer(dst));
    assert(src.type().assignableTo(dst.type()));

    return new TurbineOp(Opcode.SYNC_COPY_CONTAINER, dst, src.asArg());
  }
  
  public static Instruction asyncCopyStruct(Var dst, Var src) {
    assert(Types.isStruct(dst));
    assert(src.type().assignableTo(dst.type()));
    return new TurbineOp(Opcode.ASYNC_COPY_STRUCT, dst, src.asArg());
  }
  
  public static Instruction syncCopyStruct(Var dst, Var src) {
    assert(Types.isStruct(dst));
    assert(src.type().assignableTo(dst.type()));

    return new TurbineOp(Opcode.SYNC_COPY_STRUCT, dst, src.asArg());
  }

  /**
   * Add something to a bag
   * @param bag
   * @param elem
   * @param writersDecr
   * @return
   */
  public static Instruction bagInsert(Var bag, Arg elem, Arg writersDecr) {
    assert(Types.isBag(bag));
    assert(Types.isElemValType(bag, elem)) : bag + " " + elem + ":" + elem.type();
    assert(writersDecr.isImmediateInt());
    return new TurbineOp(Opcode.BAG_INSERT, bag, elem, writersDecr);
  }

  public static Instruction structRetrieveSub(Var dst, Var structVar,
                                           List<String> fields) {
    assert(Types.isStruct(structVar));
    assert(Types.isStructFieldVal(structVar, fields, dst)) :
          "(" + structVar.name()  + ":" + structVar.type()  + ")." + fields
          + " => " + dst;
    
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);
    
    in.add(structVar.asArg());
    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    return new TurbineOp(Opcode.STRUCT_RETRIEVE_SUB, dst.asList(), in);
  }

  public static Instruction structCreateAlias(Var fieldAlias, Var structVar,
                                              List<String> fields) {
    assert(Types.isStruct(structVar));
    assert(Types.isStructField(structVar, fields, fieldAlias)):
      structVar + " " + fields + " " + fieldAlias;
    assert(fieldAlias.storage() == Alloc.ALIAS) : fieldAlias;
    
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);
    
    in.add(structVar.asArg());
    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    return new TurbineOp(Opcode.STRUCT_CREATE_ALIAS, fieldAlias.asList(), in);
  }
  
  /**
   * Copy out value of field from a struct to a destination variable
   * @param dst
   * @param struct
   * @param fields
   * @return
   */
  public static Instruction structCopyOut(Var dst, Var struct,
                                          List<String> fields) {
    // TODO: support piggybacked refcount ops for this and other struct operations
    assert(Types.isStruct(struct));
    assert(Types.isStructField(struct, fields, dst));
    
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);
    
    in.add(struct.asArg());
    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    return new TurbineOp(Opcode.STRUCT_COPY_OUT, dst.asList(), in);
  }

  /**
   * Copy out value of field from a struct to a destination variable
   * @param dst
   * @param struct
   * @param fields
   * @return
   */
  public static Instruction structRefCopyOut(Var dst, Var struct,
                                          List<String> fields) {
    assert(Types.isStructRef(struct));
    assert(Types.isStructField(struct, fields, dst));
    
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    in.add(struct.asArg());
    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    return new TurbineOp(Opcode.STRUCTREF_COPY_OUT, dst.asList(), in);
  }

  /**
   * Store directly to a field of a struct
   * @param structVar
   * @param fields
   * @param fieldVal
   * @return
   */
  public static Instruction structStoreSub(Var structVar,
        List<String> fields, Arg fieldVal) {
    assert(Types.isStruct(structVar));
    assert(Types.isStructFieldVal(structVar, fields, fieldVal));
    
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    in.add(fieldVal);
    return new TurbineOp(Opcode.STRUCT_STORE_SUB, structVar.asList(), in);
  }
  
  /**
   * Copy a value into a field of a struct
   * @param structVar
   * @param fields
   * @param fieldVar
   * @return
   */
  public static Instruction structCopyIn(Var structVar,
      List<String> fields, Var fieldVar) {
    assert(Types.isStruct(structVar));
    assert(Types.isStructField(structVar, fields, fieldVar));
    
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    in.add(fieldVar.asArg());
    return new TurbineOp(Opcode.STRUCT_COPY_IN, structVar.asList(), in);
  }
  
  public static Instruction structRefStoreSub(Var structVar,
      List<String> fields, Arg fieldVal) {
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    in.add(fieldVal);
    return new TurbineOp(Opcode.STRUCTREF_STORE_SUB, structVar.asList(), in);
  }
  
  /**
   * Copy a value into a field of the struct referenced by structRef
   * @param structRef
   * @param fieldVar
   * @return
   */
  public static Instruction structRefCopyIn(Var structRef,
                        List<String> fields, Var fieldVar) {
    assert(Types.isStructRef(structRef, true));
    assert(Types.isStructField(structRef, fields, fieldVar));
    
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    in.add(fieldVar.asArg());
    return new TurbineOp(Opcode.STRUCTREF_COPY_IN, structRef.asList(), in);
  }
  
  /**
   * Assign any scalar data type
   * @param dst shared scalar
   * @param src local scalar value
   * @return
   */
  public static Instruction assignScalar(Var dst, Arg src) {
    assert(Types.isScalarFuture(dst)) : dst;
    assert(Types.isScalarValue(src));
    assert(src.type().assignableTo(Types.retrievedType(dst)));
    
    return new TurbineOp(Opcode.STORE_SCALAR, dst, src);
  }

  /**
   * Assign a file future from a file value
   * 
   * NOTE: the setFilename parameter is not strictly necessary: at runtime
   *       we could set the filename conditionally on the file not being
   *       mapped.  However, making it explicit simplifies correct optimisation 
   * @param dst
   * @param src
   * @param setFilename if true, set filename, if false assume already
   *                 has filename, just close the file.
   * @return
   */
  public static Instruction assignFile(Var dst, Arg src, Arg setFilename) {
    assert(Types.isFile(dst.type()));
    assert(src.isVar());
    assert(Types.isFileVal(src.getVar()));
    assert(setFilename.isImmediateBool());
    if (setFilename.isBoolVal() && setFilename.getBoolLit()) {
      // Sanity check that we're not setting mapped file
      assert(dst.isMapped() != Ternary.TRUE);
    }
    return new TurbineOp(Opcode.STORE_FILE, dst, src,
                          setFilename);
  }
  
  /**
   * Store array directly from local array representation to shared.
   * Does not follow refs, e.g. if it is an array of refs, dst must
   * be a local array of refs
   * @param dst
   * @param src
   * @return
   */
  public static Instruction assignArray(Var dst, Arg src) {
    assert(Types.isArray(dst.type())) : dst;
    assert(Types.isArrayLocal(src.type())) : src + " " + src.type();
    assert(Types.arrayKeyType(src).assignableTo(Types.arrayKeyType(dst)));
    assert(Types.containerElemType(src.type()).assignableTo(
              Types.containerElemType(dst)));
    return new TurbineOp(Opcode.STORE_ARRAY, dst, src);
  }
  
  /**
   * Store bag directly from local bag representation to shared.
   * Does not follow refs, e.g. if it is a bag of refs, dst must
   * be a local bag of refs
   * @param dst
   * @param src
   * @return
   */
  public static Instruction assignBag(Var dst, Arg src) {
    assert(Types.isBag(dst)) : dst;
    assert(Types.isBagLocal(src.type())) : src.type();
    assert(Types.containerElemType(src.type()).assignableTo(
              Types.containerElemType(dst)));
    return new TurbineOp(Opcode.STORE_BAG, dst, src);
  }
  
  
  /**
   * Initialize all struct fields that need initialization,
   * e.g. references to other data.
   * Should be called only once on each struct that needs
   * initialization. 
   * @param struct
   * @param fields
   * @param writeDecr
   */
  public static TurbineOp structInitFields(Var struct,
      List<List<String>> fieldPaths, List<Arg> fieldVals, Arg writeDecr) {
    assert(Types.isStruct(struct));
    assert(fieldPaths.size() == fieldVals.size());
    assert(writeDecr.isImmediateInt());
    
    List<Arg> inputs = new ArrayList<Arg>();
    for (int i = 0; i < fieldPaths.size(); i++) {
      List<String> fieldPath = fieldPaths.get(i);
      Arg fieldVal = fieldVals.get(i);
      assert(Types.isStructFieldVal(struct, fieldPath, fieldVal))
            : struct + " " + fieldPath + " " + fieldVal.getVar() + "\n"
              + struct.type();
      // encode lists with length prefixed
      inputs.add(Arg.createIntLit(fieldPath.size()));
      for (String field: fieldPath) {
        inputs.add(Arg.createStringLit(field));
      }
      inputs.add(fieldVal);
    }
    inputs.add(writeDecr);
    
    return new TurbineOp(Opcode.STRUCT_INIT_FIELDS, struct.asList(), inputs);
  }
  
  /**
   * 
   * @param fieldPaths if null, not filled
   * @param fieldPathsArgs if null, not filled
   * @param fieldVals if null, not filled
   * @return writeDecr
   */
  public Arg unpackStructInitArgs(Out<List<List<String>>> fieldPaths,
                                   Out<List<List<Arg>>> fieldPathsArgs,
                                   Out<List<Arg>> fieldVals) {
    assert(op == Opcode.STRUCT_INIT_FIELDS) : op;
    if (fieldPaths != null) {
      fieldPaths.val = new ArrayList<List<String>>();
    }
    
    if (fieldPathsArgs != null) {
      fieldPathsArgs.val = new ArrayList<List<Arg>>();
    }
    
    if (fieldVals != null) {
      fieldVals.val = new ArrayList<Arg>();
    }
    
    int pos = 0;
    while (pos < inputs.size() - 1) {
      long pathLength = inputs.get(pos).getIntLit();
      assert(pathLength > 0 && pathLength <= Integer.MAX_VALUE);
      pos++;
      
      List<String> fieldPath = (fieldPaths == null) ? null:
                            new ArrayList<String>((int)pathLength);
      
      List<Arg> fieldPathArgs = (fieldPathsArgs == null) ? null:
                            new ArrayList<Arg>((int)pathLength);

      
      for (int i = 0; i < pathLength; i++) {
        if (fieldPath != null) {
          fieldPath.add(inputs.get(pos).getStringLit());
        }
        
        if (fieldPathArgs != null) {
          fieldPathArgs.add(inputs.get(pos));
        }
        pos++;
      }
      
      Arg fieldVal = inputs.get(pos); 
      pos++;
      
      if (fieldPaths != null) {
        fieldPaths.val.add(fieldPath);
      }
      
      if (fieldPathsArgs != null) {
        fieldPathsArgs.val.add(fieldPathArgs);
      }
      
      if (fieldVals != null) {
        fieldVals.val.add(fieldVal);
      }
    }
    
    Arg writeDecr = getInput(pos);
    return writeDecr;
  }

  /**
   * Store struct directly from local struct representation to shared.
   * Does not follow refs.
   * @param dst
   * @param src
   * @return
   */
  public static Instruction assignStruct(Var dst, Arg src) {
    assert(Types.isStruct(dst)) : dst;
    assert(Types.isStructLocal(src)) : src.type();
    assert(StructType.sharedStruct((StructType)src.type().getImplType())
                                            .assignableTo(dst.type()));
    
    return new TurbineOp(Opcode.STORE_STRUCT, dst, src);
  }

  /**
   * Retrieve any scalar type to local value
   * @param dst
   * @param src closed scalar value
   * @return
   */
  public static Instruction retrieveScalar(Var dst, Var src) {
    assert(Types.isScalarValue(dst));
    assert(Types.isScalarFuture(src.type()));
    assert(Types.retrievedType(src).assignableTo(dst.type()));
    return new TurbineOp(Opcode.LOAD_SCALAR, dst, src.asArg());
  }
  
  /**
   * Retrieve a file value from a file future
   * @param target
   * @param src
   * @return
   */
  public static Instruction retrieveFile(Var target, Var src) {
    assert(Types.isFile(src.type()));
    assert(Types.isFileVal(target));
    return new TurbineOp(Opcode.LOAD_FILE, target, src.asArg());
  }
  
  /**
   * Retrieve an array directly to a local array, without following
   * any references
   * @param dst
   * @param src non-recursively closed array
   * @return
   */
  public static Instruction retrieveArray(Var dst, Var src) {
    assert(Types.isArray(src.type()));
    assert(Types.isArrayLocal(dst));
    assert(Types.containerElemType(src.type()).assignableTo(
              Types.containerElemType(dst)));
    return new TurbineOp(Opcode.LOAD_ARRAY, dst, src.asArg());
  }
  
  /**
   * Retrieve a bag directly to a local bag, without following
   * any references
   * @param dst
   * @param src non-recursively closed bag
   * @return
   */
  public static Instruction retrieveBag(Var target, Var src) {
    assert(Types.isBag(src.type()));
    assert(Types.isBagLocal(target));
    assert(Types.containerElemType(src.type()).assignableTo(
              Types.containerElemType(target)));
    return new TurbineOp(Opcode.LOAD_BAG, target, src.asArg());
  }
  
  /**
   * Retrieve a struct directly to a local struct, without following
   * any references
   * @param dst
   * @param src non-recursively closed struct
   * @return
   */
  public static Instruction retrieveStruct(Var dst, Var src) {
    assert(Types.isStruct(src.type()));
    assert(Types.isStructLocal(dst));
    assert(StructType.localStruct((StructType)src.type().getImplType())
                                            .assignableTo(dst.type()));
    return new TurbineOp(Opcode.LOAD_STRUCT, dst, src.asArg());
  }
  
  /**
   * Store a completely unpacked array/bag/etc to the standard shared
   * representation
   * @param target
   * @param src
   * @return
   */
  public static Instruction storeRecursive(Var target, Arg src) {
    assert(Types.isContainer(target));
    assert(Types.isContainerLocal(src.type()));
    assert(src.type().assignableTo(
            Types.unpackedContainerType(target)));
    return new TurbineOp(Opcode.STORE_RECURSIVE, target, src);
  }
  
  /**
   * Retrieve an array/bag/etc, following all references to included.
   * src must be recursively closed
   * @param target
   * @param src
   * @return
   */
  public static Instruction retrieveRecursive(Var target, Var src) {
    assert(Types.isContainer(src));
    assert(Types.isContainerLocal(target));
    Type unpackedSrcType = Types.unpackedContainerType(src);
    assert(unpackedSrcType.assignableTo(target.type())) :
            unpackedSrcType + " => " + target;

    return new TurbineOp(Opcode.LOAD_RECURSIVE, target, src.asArg());
  }
  
  public static Instruction freeBlob(Var blobVal) {
    // View refcounted var as output
    return new TurbineOp(Opcode.FREE_BLOB, blobVal);
  }

  public static Instruction decrLocalFileRef(Var fileVal) {
    assert(Types.isFileVal(fileVal));
    // We should only be freeing local file refs if we allocated a temporary 
    assert(fileVal.type().fileKind().supportsTmpImmediate());
    // View all as inputs: only used in cleanupaction context
    return new TurbineOp(Opcode.DECR_LOCAL_FILE_REF, Collections.<Var>emptyList(),
                                                     fileVal.asArg());
  }

  /**
   * Store a reference
   * @param dst reference to store to
   * @param src some datastore object
   * @return
   */
  public static Instruction storeRef(Var dst, Var src) {
    // TODO: refcounts to transfer.  Implied by output type?
    assert(Types.isRef(dst));
    assert(src.type().assignableTo(Types.retrievedType(dst)));
    
    return new TurbineOp(Opcode.STORE_REF, dst, src.asArg());
  }

  /**
   * Helper to generate appropriate store instruction for any type
   * if possible
   * @param dst
   * @param src
   * @return
   */
  public static Instruction storeAny(Var dst, Arg src) {
    assert(src.type().assignableTo(Types.retrievedType(dst)));
    if (Types.isRef(dst)) {
      assert(src.isVar());
      return storeRef(dst, src.getVar());
    } else if (Types.isPrimFuture(dst)) {
      // Regular store?
      return storePrim(dst, src);
    } else if (Types.isArray(dst)) {
      assert(src.isVar());
      return assignArray(dst, src);
    } else if (Types.isBag(dst)) {
      assert(src.isVar());
      return assignBag(dst, src);
    } else if (Types.isStruct(dst)) {
      assert(src.isVar());
      return assignStruct(dst, src);
    } else {
      throw new STCRuntimeError("Don't know how to store to " + dst);
    }
  }
  
  /**
   * Helper to generate appropriate instruction for primitive type
   * @param dst
   * @param src
   * @return
   */
  public static Instruction storePrim(Var dst, Arg src) {
    assert(Types.isPrimFuture(dst));
    assert(src.type().assignableTo(Types.retrievedType(dst)));
    if (Types.isScalarFuture(dst)) {
      return assignScalar(dst, src);
    } else if (Types.isFile(dst)) {
      // TODO: is this right to always close?
      return assignFile(dst, src, Arg.TRUE);
    } else {
      throw new STCRuntimeError("method to set " +
          dst.type().typeName() + " is not known yet");
    }
  }
  
  public static Instruction derefScalar(Var target, Var src) {
    return new TurbineOp(Opcode.DEREF_SCALAR, target, src.asArg());
  }
  
  public static Instruction derefFile(Var target, Var src) {
    return new TurbineOp(Opcode.DEREF_FILE, target, src.asArg());
  }
  
  /**
   * Retrieve a reference to a local handle 
   * @param dst alias variable to hold handle to referenced data
   * @param src Closed reference
   * @param acquireRead num of read refcounts to acquire
   * @param acquireWrite num of write refcounts to acquire
   * @return
   */
  public static Instruction retrieveRef(Var dst, Var src,
                     long acquireRead, long acquireWrite) {
    assert(Types.isRef(src.type()));
    assert(acquireRead >= 0);
    assert(acquireWrite >= 0);
    
    if (acquireWrite > 0) {
      assert(Types.isAssignableRefTo(src.type(), dst.type(), true));
    } else {
      assert(Types.isAssignableRefTo(src.type(), dst.type()));
    }
    assert(dst.storage() == Alloc.ALIAS);
    return new TurbineOp(Opcode.LOAD_REF, dst, src.asArg(),
          Arg.createIntLit(acquireRead), Arg.createIntLit(acquireWrite));
  }
  
  public static Instruction copyRef(Var dst, Var src) {
    return new TurbineOp(Opcode.COPY_REF, dst, src.asArg());
  }

  /**
   * Create a nested array and assign result id to output reference.
   * Read and write refcount is passed to output reference.
   * @param arrayResult
   * @param array
   * @param ix
   * @return
   */
  public static Instruction arrayCreateNestedFuture(Var arrayResult,
                                                    Var array, Var ix) {
    assert(Types.isArrayRef(arrayResult.type(), true));
    assert(arrayResult.storage() != Alloc.ALIAS);
    assert(Types.isArray(array.type()));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(!Types.isConstRef(arrayResult)); // Should be mutable if ref
    // Both arrays are modified, so outputs
    return new TurbineOp(Opcode.ARR_CREATE_NESTED_FUTURE,
        Arrays.asList(arrayResult, array), ix.asArg());
  }

  /**
   * Create a nested array inside the current one, or return current
   * nested array if not present.  Acquire read + write reference
   * to nested array. (TODO)
   * @param arrayResult
   * @param arrayVar
   * @param arrIx
   * @return
   */
  public static Instruction arrayCreateNestedImm(Var arrayResult,
      Var arrayVar, Arg arrIx) {
    assert(Types.isArray(arrayResult.type()));
    assert(Types.isArray(arrayVar.type()));
    assert(arrayResult.storage() == Alloc.ALIAS);
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    // Both arrays are modified, so outputs
    return new TurbineOp(Opcode.ARR_CREATE_NESTED_IMM,
        Arrays.asList(arrayResult, arrayVar),
        arrIx, Arg.ZERO, Arg.ZERO);
  }

  public static Instruction arrayRefCreateNestedComputed(Var arrayResult,
                                                       Var array, Var ix) {
    assert(Types.isArrayRef(arrayResult.type(), true)): arrayResult;
    assert(arrayResult.storage() != Alloc.ALIAS);
    assert(Types.isArrayRef(array.type(), true)): array;
    assert(Types.isArrayKeyFuture(array, ix));
    assert(!Types.isConstRef(arrayResult)); // Should be mutable if ref
    // Returns nested array, modifies outer array and
    // reference counts outmost array
    return new TurbineOp(Opcode.AREF_CREATE_NESTED_FUTURE,
        Arrays.asList(arrayResult, array),
        ix.asArg());
  }

  /**
   * 
   * @param arrayResult
   * @param outerArray
   * @param array
   * @param ix
   * @return
   */
  public static Instruction arrayRefCreateNestedImmIx(Var arrayResult,
                                                   Var array, Arg ix) {
    assert(Types.isArrayRef(arrayResult.type(), true)): arrayResult;
    assert(arrayResult.storage() != Alloc.ALIAS);
    assert(Types.isArrayRef(array.type(), true)): array;
    assert(Types.isArrayKeyVal(array, ix));
    assert(!Types.isConstRef(arrayResult)); // Should be mutable if ref
    return new TurbineOp(Opcode.AREF_CREATE_NESTED_IMM,
        // Returns nested array, modifies outer array and
        // reference counts outmost array
        Arrays.asList(arrayResult, array),
        ix);
  }
  

  /**
   * Create a nested bag inside an array
   * @param bag
   * @param arr
   * @param key
   * @return
   */
  public static Instruction arrayCreateBag(Var bag,
      Var arr, Arg key) {
    assert(Types.isBag(bag));
    assert(Types.isArray(arr));
    assert(Types.isArrayKeyVal(arr, key));
    assert(bag.storage() == Alloc.ALIAS);
    // Both arrays are modified, so outputs
    return new TurbineOp(Opcode.ARRAY_CREATE_BAG,
        Arrays.asList(bag, arr),
        key, Arg.ZERO, Arg.ZERO);
  }

  public static Instruction initUpdateableFloat(Var updateable, Arg val) {
    return new TurbineOp(Opcode.INIT_UPDATEABLE_FLOAT, updateable, val);
    
  }

  public static Instruction latestValue(Var result, Var updateable) {
    return new TurbineOp(Opcode.LATEST_VALUE, result, updateable.asArg());
  }
  
  public static Instruction update(Var updateable,
      UpdateMode updateMode, Var val) {
    Opcode op;
    switch (updateMode) {
    case MIN:
      op = Opcode.UPDATE_MIN;
      break;
    case INCR:
      op = Opcode.UPDATE_INCR;
      break;
    case SCALE:
      op = Opcode.UPDATE_SCALE;
      break;
    default:
      throw new STCRuntimeError("Unknown UpdateMode" + updateMode);
    }
    return new TurbineOp(op, updateable, val.asArg());
  }

  public static Instruction updateImm(Var updateable,
      UpdateMode updateMode, Arg val) {
    Opcode op;
    switch (updateMode) {
    case MIN:
      op = Opcode.UPDATE_MIN_IMM;
      break;
    case INCR:
      op = Opcode.UPDATE_INCR_IMM;
      break;
    case SCALE:
      op = Opcode.UPDATE_SCALE_IMM;
      break;
    default:
      throw new STCRuntimeError("Unknown UpdateMode"
          + updateMode);
    }
    return new TurbineOp(op, updateable, val);
  }
  
  public static Instruction getFileNameAlias(Var filename, Var file) {
    return new TurbineOp(Opcode.GET_FILENAME_ALIAS, filename, file.asArg());
  }
  
  public static Instruction copyInFilename(Var file, Var filename) {
    return new TurbineOp(Opcode.COPY_IN_FILENAME, file, filename.asArg());
  }
  
  public static Instruction getLocalFileName(Var filename, Var file) {
    assert(Types.isFileVal(file));
    assert(Types.isStringVal(filename));
    return new TurbineOp(Opcode.GET_LOCAL_FILENAME, filename, file.asArg());
  }

  public static Instruction getFilenameVal(Var filenameVal, Var file) {
    assert(Types.isStringVal(filenameVal));
    assert(Types.isFile(file));
    return new TurbineOp(Opcode.GET_FILENAME_VAL, filenameVal, file.asArg());
  }

  /**
   * Set the filename of a file
   * TODO: take additional disable variable that avoids setting if not
   * mapped, to aid optimiser
   * @param file
   * @param filenameVal
   * @return
   */
  public static Instruction setFilenameVal(Var file, Arg filenameVal) {
    assert(Types.isFile(file.type()));
    assert(filenameVal.isImmediateString());
    return new TurbineOp(Opcode.SET_FILENAME_VAL, file, filenameVal);
  }

  public static Instruction copyFileContents(Var target, Var src) {
    return new TurbineOp(Opcode.COPY_FILE_CONTENTS, target, src.asArg());
  }
  
  /**
   * Check if file is mapped
   * @param isMapped
   * @param file
   * @return
   */
  public static Instruction isMapped(Var isMapped, Var file) {
    assert(Types.isBoolVal(isMapped));
    assert(Types.isFile(file));
    return new TurbineOp(Opcode.IS_MAPPED, isMapped, file.asArg());
  }

  public static Instruction chooseTmpFilename(Var filenameVal) {
    return new TurbineOp(Opcode.CHOOSE_TMP_FILENAME, filenameVal);
  }
  
  public static Instruction initLocalOutFile(Var localOutFile,
                                Arg outFilename, Arg isMapped) {
    assert(Types.isFileVal(localOutFile));
    assert(Types.isStringVal(outFilename.type()));
    assert(Types.isBoolVal(isMapped.type()));
    return new TurbineOp(Opcode.INIT_LOCAL_OUTPUT_FILE, localOutFile.asList(),
                         outFilename, isMapped);
  }
  
  public static Instruction checkpointLookupEnabled(Var v) {
    return new TurbineOp(Opcode.CHECKPOINT_LOOKUP_ENABLED, v);
  }
  
  public static Instruction checkpointWriteEnabled(Var v) {
    return new TurbineOp(Opcode.CHECKPOINT_WRITE_ENABLED, v);
  }

  public static Instruction writeCheckpoint(Arg key, Arg value) {
    assert(Types.isBlobVal(key.type()));
    assert(Types.isBlobVal(value.type()));
    return new TurbineOp(Opcode.WRITE_CHECKPOINT, Var.NONE, key, value);
  }

  public static Instruction lookupCheckpoint(Var checkpointExists, Var value,
      Arg key) {
    assert(Types.isBoolVal(checkpointExists));
    assert(Types.isBlobVal(value));
    assert(Types.isBlobVal(key.type()));
    return new TurbineOp(Opcode.LOOKUP_CHECKPOINT,
        Arrays.asList(checkpointExists, value), key);
  }

  public static Instruction packValues(Var packedValues, List<Arg> values) {
    for (Arg val: values) {
      assert(val.isConstant() || val.getVar().storage() == Alloc.LOCAL);
    }
    return new TurbineOp(Opcode.PACK_VALUES, packedValues.asList(), values); 
  }
  
  public static Instruction unpackValues(List<Var> values, Arg packedValues) {
    for (Var val: values) {
      assert(val.storage() == Alloc.LOCAL);
    }
    return new TurbineOp(Opcode.UNPACK_VALUES, values, packedValues); 
  }

  public static Instruction unpackArrayToFlat(Var flatLocalArray, Arg inputArray) {
    return new TurbineOp(Opcode.UNPACK_ARRAY_TO_FLAT, flatLocalArray, inputArray); 
  }
  
  @Override
  public void renameVars(Map<Var, Arg> renames, RenameMode mode) {
    if (mode == RenameMode.VALUE) {
      // Fall through
    } else if (mode == RenameMode.REPLACE_VAR) {
      // Straightforward replacement
      ICUtil.replaceVarsInList(renames, outputs, false);
    } else {
      assert(mode == RenameMode.REFERENCE);
      for (int i = 0; i < outputs.size(); i++) {
        Var output = outputs.get(i);
        if (renames.containsKey(output)) {
          // Avoid replacing aliases that were initialized
          boolean isInit = false;
          for (Pair<Var, Instruction.InitType> p: getInitialized()) {
            if (output.equals(p.val1)) {
              isInit = true;
              break;
            }
          }
          if (!isInit) {
            Arg repl = renames.get(output);
            if (repl.isVar()) {
              outputs.set(i, repl.getVar());
            }
          }
        }
      }
    }
    renameInputs(renames);
  }

  public void renameInputs(Map<Var, Arg> renames) {     
     ICUtil.replaceArgsInList(renames, inputs);
  }

  @Override
  public boolean hasSideEffects() {
    switch (op) {
    // The direct container write functions only mutate their output argument
    // so effect can be tracked back to output var
    case STRUCT_INIT_FIELDS:
    case STRUCT_STORE_SUB:
    case STRUCT_COPY_IN:
    case STRUCTREF_STORE_SUB:
    case STRUCTREF_COPY_IN:
    case ARRAY_BUILD:
    case ARR_STORE_FUTURE:
    case ARR_COPY_IN_FUTURE:
    case ARR_STORE:
    case ARR_COPY_IN_IMM:
    case AREF_STORE_FUTURE:
    case AREF_COPY_IN_FUTURE:
    case AREF_STORE_IMM:
    case AREF_COPY_IN_IMM:
    case SYNC_COPY_CONTAINER:
    case ASYNC_COPY_CONTAINER:
    case SYNC_COPY_STRUCT:
    case ASYNC_COPY_STRUCT:
      return false;

    case BAG_INSERT:
      return false;
    
    case UPDATE_INCR:
    case UPDATE_MIN:
    case UPDATE_SCALE:
    case UPDATE_INCR_IMM:
    case UPDATE_MIN_IMM:
    case UPDATE_SCALE_IMM:
    case INIT_UPDATEABLE_FLOAT:
      return true;
    
    case STORE_SCALAR:
    case STORE_FILE:
    case STORE_ARRAY:
    case STORE_BAG:
    case STORE_STRUCT:
    case STORE_RECURSIVE:
    case DEREF_SCALAR:
    case DEREF_FILE:
    case LOAD_SCALAR:
    case LOAD_FILE:
    case LOAD_ARRAY:
    case LOAD_BAG:
    case LOAD_STRUCT:
    case LOAD_RECURSIVE:
      return false;
      
    case ARR_COPY_OUT_IMM:
    case ARR_COPY_OUT_FUTURE:
    case AREF_COPY_OUT_FUTURE:
    case AREF_COPY_OUT_IMM:
    case ARR_CONTAINS:
    case CONTAINER_SIZE:
    case ARR_LOCAL_CONTAINS:
    case CONTAINER_LOCAL_SIZE:
      return false;

    case GET_FILENAME_ALIAS:
      // Only effect is setting alias var
      return false;
    case GET_LOCAL_FILENAME:
      return false;
    case GET_FILENAME_VAL:
      return false;
    case IS_MAPPED:
      // will always returns same result for same var
      return false;
    case CHOOSE_TMP_FILENAME:
      // Non-deterministic
      return true;
    case SET_FILENAME_VAL:
    case COPY_IN_FILENAME:
      // Only effect is in file output var
      return false;
    case COPY_FILE_CONTENTS:
      // Only effect is to modify file represented by output var
      return false;

    case INIT_LOCAL_OUTPUT_FILE:
      // If the output is mapped, we want to retain the file,
      // so we treat this as having side-effects
      if (getInput(1).isBoolVal() && getInput(1).getBoolLit() == false) {
        // Definitely unmapped
        return false;
      } else {
        // Maybe mapped
        return true;
      }

    case LOAD_REF:
    case STORE_REF:
    case COPY_REF:
    case STRUCT_CREATE_ALIAS:
    case STRUCT_RETRIEVE_SUB:
    case STRUCT_COPY_OUT:
    case STRUCTREF_COPY_OUT:
    case ARR_RETRIEVE:
    case LATEST_VALUE:
        // Always has alias as output because the instructions initialises
        // the aliases
        return false;
        
    case ARR_CREATE_NESTED_FUTURE:
    case AREF_CREATE_NESTED_FUTURE:
    case ARR_CREATE_NESTED_IMM:
    case AREF_CREATE_NESTED_IMM:
    case ARRAY_CREATE_BAG:
        /* It might seem like these nested creation primitives have a 
         * side-effect, but for optimisation purposes they can be treated as 
         * side-effect free, as the side-effect is only relevant if the array 
         * created is subsequently used in a store operation
         */ 
      return false;
    case FREE_BLOB:
    case DECR_LOCAL_FILE_REF:
      /*
       * Reference counting ops can have side-effect
       */
      return true;
    case WRITE_CHECKPOINT:
      // Writing checkpoint is a side-effect
      return true;
    case LOOKUP_CHECKPOINT:
    case PACK_VALUES:
    case UNPACK_VALUES:
    case UNPACK_ARRAY_TO_FLAT:
    case CHECKPOINT_WRITE_ENABLED:
    case CHECKPOINT_LOOKUP_ENABLED:
      return false;
    default:
      throw new STCRuntimeError("Need to add opcode " + op.toString()
          + " to hasSideEffects");
    }
  }
  

  public boolean canChangeTiming() {
    return !hasSideEffects() && op != Opcode.LATEST_VALUE;
  }

  @Override
  public List<Var> getOutputs() {
    return Collections.unmodifiableList(outputs);
  }
  
  @Override
  public Arg getInput(int i) {
    return inputs.get(i);
  }
  
  @Override
  public Var getOutput(int i) {
    return outputs.get(i);
  }

  @Override
  public List<Arg> getInputs() {
    return Collections.unmodifiableList(inputs);
  }
  
  public List<Arg> getInputsTail(int start) {
    return Collections.unmodifiableList(inputs.subList(start, inputs.size()));
  }

  public void setInput(int i, Arg arg) {
    this.inputs.set(i, arg);
  }

  @Override
  public MakeImmRequest canMakeImmediate(Set<Var> closedVars,
      Set<ArgCV> closedLocations, Set<Var> valueAvail, boolean waitForClose) {
    boolean insertRefWaitForClose = waitForClose;
    // Try to take advantage of closed variables 
    switch (op) {
    case ARR_COPY_OUT_IMM: {
      // If array is closed or this index already inserted,
      // don't need to block on array.  
      // NOTE: could try to reduce other forms to this in one step,
      //      but its probably just easier to do it in multiple steps
      //      on subsequent passes
      Var arr = getInput(0).getVar();
      if (closedVars.contains(arr)) {
        // Don't request to wait for close - whole array doesn't need to be
        // closed
        return new MakeImmRequest(null, Collections.<Var>emptyList());
      }
      break;
    }
    case ARR_COPY_OUT_FUTURE: {
      Var index = getInput(1).getVar();
      if (waitForClose || closedVars.contains(index)) {
        return new MakeImmRequest(null, Arrays.asList(index));
      }
      break;
    }
    case AREF_COPY_OUT_FUTURE: {
      Var arr = getInput(0).getVar();
      Var ix = getInput(1).getVar();
      // We will take either the index or the dereferenced array
      List<Var> req = mkImmVarList(waitForClose, closedVars, arr, ix);
      if (req.size() > 0) {
        return new MakeImmRequest(null, req);
      }
      break;
    }
    case AREF_COPY_OUT_IMM: {
      // Could skip using reference
      Var arrRef = getInput(0).getVar();
      if (waitForClose || closedVars.contains(arrRef)) {
        return new MakeImmRequest(null, Arrays.asList(arrRef));
      }
      break;
    }
    case ARR_CONTAINS: {
      Var arr = getInput(0).getVar();
      // check to see if local version of array available
      // (Already assuming array closed)
      if (valueAvail.contains(arr)) {
        return new MakeImmRequest(null, Arrays.asList(arr));
      }
      break;  
    }
    case CONTAINER_SIZE: {
      Var cont = getInput(0).getVar();
      // check to see if local version of container available
      // (Already assuming array closed)
      if (valueAvail.contains(cont)) {
        return new MakeImmRequest(null, Arrays.asList(cont));
      }
      break;  
    }
    case STRUCT_COPY_IN: {
      Var val = getInput(0).getVar();
      if (waitForClose || closedVars.contains(val)) {
        return new MakeImmRequest(null, val.asList());
      }
      break;
    }
    case STRUCTREF_COPY_IN: {
      Var structRef = getOutput(0);
      Var val = getInput(0).getVar();
      List<Var> vs = mkImmVarList(waitForClose, closedVars,
                                  Arrays.asList(structRef, val));
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case STRUCTREF_STORE_SUB: {
      Var structRef = getOutput(0);
      if (waitForClose || closedVars.contains(structRef)) {
        return new MakeImmRequest(null, structRef.asList());
      }
      break;
    }
    case STRUCT_COPY_OUT: {
      // If struct is closed or this field already set, don't needto block
      Var struct = getInput(0).getVar();
      if (closedVars.contains(struct)) {
        // Don't request to wait for close - whole struct doesn't need to be
        // closed
        return new MakeImmRequest(null, Collections.<Var>emptyList());
      }
      break;
    }
    case STRUCTREF_COPY_OUT: {
      Var structRef = getInput(0).getVar();
      if (waitForClose || closedVars.contains(structRef)) {
        return new MakeImmRequest(null, structRef.asList());
      }
      break;  
    }
    case ARR_COPY_IN_IMM: {
      // See if we can get deref arg
      Var mem = getInput(1).getVar();
      List<Var> vs = mkImmVarList(waitForClose, closedVars, mem.asList());
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case ARR_STORE_FUTURE:
    case ARR_COPY_IN_FUTURE: {
      Var ix = getInput(0).getVar();
      Arg val = getInput(1);
      List<Var> vs;
      if (op == Opcode.ARR_STORE_FUTURE) {
        vs = ix.asList();
      } else { 
        assert(op == Opcode.ARR_COPY_IN_FUTURE);
        vs = Arrays.asList(ix, val.getVar());
      }
      vs = mkImmVarList(waitForClose, closedVars, vs);
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case AREF_STORE_IMM: 
    case AREF_COPY_IN_IMM: {
      List<Var> vs;
      Var arrRef = getOutput(0);
      Arg mem = getInput(1);
      if (op == Opcode.AREF_STORE_IMM) {
        vs = arrRef.asList();
      } else {
        assert(op == Opcode.AREF_COPY_IN_IMM);
        vs = Arrays.asList(arrRef, mem.getVar());
      }
      vs = mkImmVarList(insertRefWaitForClose, closedVars, vs);
      
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case AREF_STORE_FUTURE: 
    case AREF_COPY_IN_FUTURE: {
      Var arrRef = getOutput(0);
      Var ix = getInput(0).getVar();
      Arg mem = getInput(1);
      List<Var> req;
      if (op == Opcode.AREF_STORE_FUTURE) {
        req = Arrays.asList(arrRef, ix);
      } else {
        assert(op == Opcode.AREF_COPY_IN_FUTURE);
        req = Arrays.asList(arrRef, ix, mem.getVar());
      }
      // We will take either the index or the dereferenced array
      req = mkImmVarList(insertRefWaitForClose, closedVars, req);
      if (req.size() > 0) {
        return new MakeImmRequest(null, req);
      }
      break;
    }
    case ARR_CREATE_NESTED_FUTURE: {
      // Try to get immediate index
      Var ix = getInput(0).getVar();
      if (waitForClose || closedVars.contains(ix)) {
        return new MakeImmRequest(null, Arrays.asList(ix));
      }
      break;
    }
    case AREF_CREATE_NESTED_IMM: {
      Var arrRef = getOutput(1);
      if (waitForClose || closedVars.contains(arrRef)) {
        return new MakeImmRequest(null, Arrays.asList(arrRef));
      }
      break;
    }
    case AREF_CREATE_NESTED_FUTURE: {
      Var arrRef = getOutput(1);
      Var ix = getInput(0).getVar();
      List<Var> req5 = mkImmVarList(waitForClose, closedVars, arrRef, ix);
      if (req5.size() > 0) {
        return new MakeImmRequest(null, req5);
      }
      break;
    }
    case ASYNC_COPY_CONTAINER: 
    case ASYNC_COPY_STRUCT: {
      // See if we can get closed container/struct
      List<Var> req = mkImmVarList(waitForClose, closedVars,
                                   getInput(0).getVar());
      if (req.size() > 0) {
        return new MakeImmRequest(null, req);
      }
      break;
    }
    case SYNC_COPY_CONTAINER: 
    case SYNC_COPY_STRUCT: {
      // TODO: would be nice to switch to array_store if we already have
      //       loaded array value
      break;
    }
    case COPY_IN_FILENAME:
      return new MakeImmRequest(null, getInput(0).getVar().asList());
    case UPDATE_INCR:
    case UPDATE_MIN:
    case UPDATE_SCALE:
      return new MakeImmRequest(null, getInput(0).getVar().asList());
    default:
      // fall through
    }
    return null;
  }
  
  private List<Var> mkImmVarList(boolean waitForClose,
                                 Set<Var> closedVars, Var... args) {
    return mkImmVarList(waitForClose, closedVars, Arrays.asList(args));
  }
  
  private List<Var> mkImmVarList(boolean waitForClose,
        Set<Var> closedVars, List<Var> args) {
    ArrayList<Var> req = new ArrayList<Var>(args.size());
    for (Var v: args) {
      if (waitForClose || closedVars.contains(v)) {
        req.add(v);
      }
    }
    return req;
  }

  @Override
  public MakeImmChange makeImmediate(VarCreator creator,
                                     List<Fetched<Var>> out,
                                     List<Fetched<Arg>> values) {
    switch (op) {
    case ARR_COPY_OUT_IMM: {
      assert(values.size() == 0);
      // Input should be unchanged
      Var arr = getInput(0).getVar();
      // Output switched from ref to value
      Var origOut = getOutput(0);
      Var valOut = creator.createDerefTmp(origOut);
      Instruction newI = arrayRetrieve(valOut, arr, getInput(1));
      return new MakeImmChange(valOut, origOut, newI);
    }
    case ARR_COPY_OUT_FUTURE: {
      assert(values.size() == 1);
      Arg newIx = values.get(0).fetched;
      return new MakeImmChange(
              arrayCopyOutImm(getOutput(0), getInput(0).getVar(), newIx));
    }
    case AREF_COPY_OUT_FUTURE: {
      assert(values.size() == 1 || values.size() == 2);
      Var mem = getOutput(0); 
      Var arrRef = getInput(0).getVar();
      Var ix = getInput(1).getVar();
      Arg newIx = Fetched.findFetched(values, ix);
      Var newArr = Fetched.findFetchedVar(values, arrRef);
      
      Instruction inst;
      // Could be either array ref, index, or both
      if (newIx != null && newArr != null) {
        inst = arrayCopyOutImm(mem, newArr, newIx);
      } else if (newIx != null && newArr == null){
        inst = arrayRefCopyOutImm(mem, arrRef, newIx);
      } else { 
        assert(newIx == null && newArr != null);
        inst = arrayCopyOutFuture(mem, newArr, ix);
      }
      return new MakeImmChange(inst);
    }
    case AREF_COPY_OUT_IMM: {
      assert(values.size() == 1);
      // Switch from ref to plain array
      Var newArr = values.get(0).fetched.getVar();
      return new MakeImmChange(
          arrayCopyOutImm(getOutput(0), newArr, getInput(1)));
    }
    case ARR_CONTAINS: {
      Var localArr = values.get(0).fetched.getVar();
      return new MakeImmChange(
          arrayLocalContains(getOutput(0), localArr, getInput(1)));
    }
    case CONTAINER_SIZE: {
      Var localCont = values.get(0).fetched.getVar();
      return new MakeImmChange(
          containerLocalSize(getOutput(0), localCont));
    }
    case STRUCT_COPY_IN: {
      assert(values.size() == 1);
      Arg derefMember = values.get(0).fetched;
      List<String> fields = Arg.extractStrings(getInputsTail(1));
      return new MakeImmChange(
          structStoreSub(getOutput(0), fields, derefMember));
    }
    case STRUCTREF_STORE_SUB: {
      assert(values.size() == 1);
      Var structRef = getOutput(0);
      Var newStruct = Fetched.findFetchedVar(values, structRef);
      List<String> fields = Arg.extractStrings(getInputsTail(1));
      return new MakeImmChange(
          structStoreSub(newStruct, fields, getInput(0)));
    } 
    case STRUCTREF_COPY_IN: {
      Var structRef = getOutput(0);
      Var val = getInput(0).getVar();
      Var newStruct = Fetched.findFetchedVar(values, structRef);
      Arg newVal = Fetched.findFetched(values, val);

      List<String> fields = Arg.extractStrings(getInputsTail(1));
      
      Instruction newI;
      if (newStruct != null && newVal != null) {
        newI = structStoreSub(newStruct, fields, newVal);
      } else if (newStruct != null && newVal == null) {
        newI = structCopyIn(newStruct, fields, val);
      } else {
        assert(newStruct == null && newVal != null);
        newI = structRefStoreSub(structRef, fields, newVal);
      }
      
      return new MakeImmChange(newI);
    }
    case STRUCT_COPY_OUT: {
      assert(values.size() == 0);
      // Input should be unchanged
      Var arr = getInput(0).getVar();
      // Output switched from ref to value
      Var origOut = getOutput(0);
      List<String> fields = Arg.extractStrings(getInputsTail(1));
      Var valOut = creator.createDerefTmp(origOut);
      Instruction newI = structRetrieveSub(valOut, arr, fields);
      return new MakeImmChange(valOut, origOut, newI);
    }
    case STRUCTREF_COPY_OUT: {
      assert(values.size() == 1);
      Var structRef = getInput(0).getVar();
      Var newStruct = Fetched.findFetchedVar(values, structRef);
      List<String> fields = Arg.extractStrings(getInputsTail(1));
      return new MakeImmChange(
          structCopyOut(getOutput(0), newStruct, fields));
    }
    case ARR_COPY_IN_IMM: {
      assert(values.size() == 1);
      Arg derefMember = values.get(0).fetched;
      return new MakeImmChange(
          arrayStore(getOutput(0), getInput(0), derefMember));
    }
    case ARR_STORE_FUTURE: {
      assert(values.size() == 1);
      Arg fetchedIx = values.get(0).fetched;
      return new MakeImmChange(
          arrayStore(getOutput(0), fetchedIx, getInput(1)));
    }
    case ARR_COPY_IN_FUTURE: {
      Var arr = getOutput(0);
      Var ix = getInput(0).getVar();
      Var mem = getInput(1).getVar();
      Arg newIx = Fetched.findFetched(values, ix);
      Arg newMem = Fetched.findFetched(values, mem);
      Instruction inst;
      if (newIx != null && newMem != null) {
        inst = arrayStore(arr, newIx, newMem);
      } else if (newIx != null && newMem == null) {
        inst = arrayCopyInImm(arr, newIx, mem); 
      } else {
        assert(newIx == null && newMem != null);
        inst = arrayStoreFuture(arr, ix, newMem);
      }
      return new MakeImmChange(inst);
    }
    case AREF_STORE_IMM: {
      assert(values.size() == 1);
      Var newOut = values.get(0).fetched.getVar();
      // Switch from ref to plain array
      return new MakeImmChange(arrayStore(newOut, getInput(0), getInput(1)));
    }
    case AREF_COPY_IN_IMM: {
      Var arrRef = getOutput(0);
      Arg ix = getInput(0);
      Var mem = getInput(1).getVar();
      Var newArr = Fetched.findFetchedVar(values, arrRef);
      Arg newMem = Fetched.findFetched(values, mem);
      Instruction newI;
      if (newArr != null && newMem != null) {
        newI = arrayStore(newArr, ix, newMem);
      } else if (newArr != null && newMem == null) {
        newI = arrayCopyInImm(newArr, ix, mem);
      } else {
        assert(newArr == null && newMem != null);
        newI = arrayRefStoreImm(arrRef, ix, newMem);
      }
      
      return new MakeImmChange(newI);
    }
    case AREF_STORE_FUTURE:
    case AREF_COPY_IN_FUTURE: {
      Var arrRef = getOutput(0);
      Var ix = getInput(0).getVar();
      Arg mem = getInput(1);
      
      // Various combinations are possible
      Var newArr = Fetched.findFetchedVar(values, arrRef);
      Arg newIx = Fetched.findFetched(values, ix);
      Arg derefMem = null;
      if (mem.isVar()) {
        derefMem = Fetched.findFetched(values, mem.getVar());
      }
      
      Instruction inst;
      if (derefMem != null || op == Opcode.AREF_STORE_FUTURE) {
        if (derefMem == null) {
          assert(op == Opcode.AREF_STORE_FUTURE);
          // It was already dereferenced
          derefMem = mem;
        }
        if (newArr != null && newIx != null) {
          inst = arrayStore(newArr, newIx, derefMem);
        } else if (newArr != null && newIx == null) {
          inst = arrayStoreFuture(newArr, ix, derefMem);
        } else if (newArr == null && newIx != null) {
          inst = arrayRefStoreImm(arrRef, newIx, derefMem);
        } else {
          assert(newArr == null && newIx == null);
          inst = arrayRefStoreFuture(arrRef, ix, derefMem);
        }
      } else {
        Var memVar = mem.getVar();
        assert(op == Opcode.AREF_COPY_IN_FUTURE);
        if (newArr != null && newIx != null) {
          inst = arrayCopyInImm(newArr, newIx, memVar);
        } else if (newArr != null && newIx == null) {
          inst = arrayCopyInFuture(newArr, ix, memVar);
        } else {
          assert(newArr == null && newIx != null) :
                 this + " | " + newArr + " " + newIx;
          inst = arrayRefCopyInImm(arrRef, newIx, memVar);
        }
      }
      return new MakeImmChange(inst);
    }
    case ARR_CREATE_NESTED_FUTURE: {
      assert(values.size() == 1);
      Arg ix = values.get(0).fetched;
      Var oldResult = getOutput(0);
      Var oldArray = getOutput(1);
      assert(Types.isArrayKeyVal(oldArray, ix)) : oldArray + " " + ix.type();
      // Output type of instruction changed from ref to direct
      // array handle
      assert(Types.isArrayRef(oldResult.type()));
      Var newOut = creator.createDerefTmp(oldResult);
      return new MakeImmChange(newOut, oldResult,
          arrayCreateNestedImm(newOut, oldArray, ix));
    }
    case AREF_CREATE_NESTED_FUTURE: {
      assert(values.size() == 1 || values.size() == 2);
      Var arrResult = getOutput(0);
      Var arrRef = getOutput(1);
      Var ix = getInput(0).getVar();
      
      Var newArr = Fetched.findFetchedVar(values, arrRef);
      Arg newIx = Fetched.findFetched(values, ix);
      
      if (newArr != null && newIx != null) {
        Var oldOut = getOutput(0);
        assert(Types.isArrayRef(oldOut.type()));
        Var newOut = creator.createDerefTmp(arrResult);
        return new MakeImmChange(newOut, oldOut,
            arrayCreateNestedImm(newOut, newArr, newIx));
      } else if (newArr != null && newIx == null) {
        return new MakeImmChange(
            arrayCreateNestedFuture(arrResult, newArr, ix));
      } else {
        assert(newArr == null && newIx != null);
        return new MakeImmChange(
            arrayRefCreateNestedImmIx(arrResult, arrRef, newIx));
      }
    }
    case AREF_CREATE_NESTED_IMM: {
      assert(values.size() == 1);
      Var newArr = values.get(0).fetched.getVar();
      Arg ix = getInput(0);
      Var arrResult = getOutput(0);
      assert(Types.isArray(newArr));
      assert(Types.isArrayRef(arrResult.type()));
      Var newOut3 = creator.createDerefTmp(arrResult);
      assert(Types.isArrayKeyVal(newArr, ix));
      return new MakeImmChange(newOut3, arrResult,
          arrayCreateNestedImm(newOut3, newArr, getInput(0)));
    }
    case ASYNC_COPY_CONTAINER: {
      // Array is closed: replace with sync version
      return new MakeImmChange(
          syncCopyContainer(getOutput(0), getInput(0).getVar()));
    }
    case SYNC_COPY_CONTAINER: 
    case SYNC_COPY_STRUCT: {
      // TODO: would be nice to switch to array_store if we already have
      //       loaded array value
      break;
    }
    case ASYNC_COPY_STRUCT: {
      // Array is closed: replace with sync version
      return new MakeImmChange(
          syncCopyStruct(getOutput(0), getInput(0).getVar()));
    }
    case COPY_IN_FILENAME: {
      return new MakeImmChange(
          setFilenameVal(getOutput(0), values.get(0).fetched));
    }
    case UPDATE_INCR:
    case UPDATE_MIN:
    case UPDATE_SCALE: {
      assert(values.size() == 1);
      UpdateMode mode;
      switch (op) {
      case UPDATE_INCR:
        mode = UpdateMode.INCR;
        break;
      case UPDATE_MIN:
        mode = UpdateMode.MIN;
        break;
      case UPDATE_SCALE:
        mode = UpdateMode.SCALE;
        break;
      default:
        throw new STCRuntimeError("op: " + op +
                                  " ... shouldn't be here");
      }
      return new MakeImmChange(null, null, TurbineOp.updateImm(
          getOutput(0), mode, values.get(0).fetched));
    }
    default:
      // fall through
      break;
    }
    throw new STCRuntimeError("Couldn't make inst "
        + this.toString() + " immediate with vars: "
        + values.toString());
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Pair<Var, Instruction.InitType>> getInitialized() {
    switch (op) {
      case LOAD_REF:
      case COPY_REF:
      case ARR_CREATE_NESTED_IMM:
      case ARRAY_CREATE_BAG:
      case GET_FILENAME_ALIAS:
        // Initialises alias
        return Arrays.asList(Pair.create(getOutput(0), InitType.FULL));

      case ARR_RETRIEVE:
      case STRUCT_RETRIEVE_SUB:
      case STRUCT_CREATE_ALIAS: {
        // May initialise alias if we're looking up a reference
        Var output = getOutput(0);
        if (output.storage() == Alloc.ALIAS) {
          return Arrays.asList(Pair.create(output, InitType.FULL));
        } else {
          return Collections.emptyList();
        }
      }

      case STRUCT_INIT_FIELDS: {
        // Initializes struct fields that we assume are present
        Var struct = getOutput(0);
        return Arrays.asList(Pair.create(struct, InitType.FULL));
      }
      
      case INIT_UPDATEABLE_FLOAT:
        // Initializes updateable
        return Arrays.asList(Pair.create(getOutput(0), InitType.FULL));
      
      case INIT_LOCAL_OUTPUT_FILE:
      case LOAD_FILE:
        // Initializes output file value
        return Arrays.asList(Pair.create(getOutput(0), InitType.FULL));
      default:
        return Collections.emptyList();
    }
  }

  /**
   * @return list of outputs for which previous value is read
   */
  public List<Var> getReadOutputs(Map<String, Function> fns) {
    switch (op) {
    case ARR_CREATE_NESTED_IMM:
    case ARR_CREATE_NESTED_FUTURE:
      // In create_nested instructions the 
      // second array being inserted into is needed
      return Arrays.asList(getOutput(1));
    case ARRAY_CREATE_BAG:
      // the array being inserted into
      return getOutput(1).asList();
    case AREF_CREATE_NESTED_IMM:
    case AREF_CREATE_NESTED_FUTURE:
      // In ref_create_nested instructions the 
      // second array being inserted into is needed
      return Arrays.asList(getOutput(1));
      default:
        return Var.NONE;
    }
  }
  
  public List<Var> getModifiedOutputs() {
    switch (op) {
    case ARR_CREATE_NESTED_IMM:
    case ARR_CREATE_NESTED_FUTURE:
    case AREF_CREATE_NESTED_IMM:
    case AREF_CREATE_NESTED_FUTURE:
    case ARRAY_CREATE_BAG:
      // In create_nested instructions only the 
      // first output (the created array) is needed
      return Collections.singletonList(getOutput(0));
    default:
        return this.getOutputs();
    }
  }

  /**
   * @return List of outputs that are piecewise assigned
   */
  public List<Var> getPiecewiseAssignedOutputs() {
    switch (op) {
      case ARR_STORE_FUTURE:
      case ARR_COPY_IN_FUTURE:
      case ARR_STORE:
      case ARR_COPY_IN_IMM:
      case AREF_STORE_FUTURE:
      case AREF_COPY_IN_FUTURE:
      case AREF_STORE_IMM:
      case AREF_COPY_IN_IMM:
        // All outputs are piecewise assigned
        return getOutputs();
      case ARR_CREATE_NESTED_FUTURE:
      case ARR_CREATE_NESTED_IMM:
      case ARRAY_CREATE_BAG:
      case AREF_CREATE_NESTED_FUTURE:
      case AREF_CREATE_NESTED_IMM: {
        // All arrays except the newly created array; 
        List<Var> outputs = getOutputs();
        return outputs.subList(1, outputs.size());
      }
      case STRUCT_STORE_SUB:
      case STRUCT_COPY_IN:
      case STRUCTREF_STORE_SUB:
      case STRUCTREF_COPY_IN:
        return getOutputs();
      case COPY_IN_FILENAME:
      case SET_FILENAME_VAL:
        // File's filename might be modified
        return getOutput(0).asList();
      case STORE_FILE: {
        Arg setFilename = getInput(1);
        if (setFilename.isBoolVal() && setFilename.getBoolLit()) {
          // Assign whole file
          return Var.NONE;
        } else {
          // Assigned filename separately
          return getOutput(0).asList();
        }
      }
      default:
        return Var.NONE;
    }
  }

  @Override
  public List<Var> getBlockingInputs(Program prog) {
    if (getMode() == TaskMode.SYNC) {
      return Var.NONE;
    }
    
    // If async, assume that all scalar input vars are blocked on
    ArrayList<Var> blocksOn = new ArrayList<Var>();
    for (Arg oa: getInputs()) {
      if (oa.kind == ArgKind.VAR) {
        Var v = oa.getVar();
        Type t = v.type();
        if (Types.isPrimFuture(t) || Types.isRef(t)) {
          blocksOn.add(v);
        } else if (Types.isPrimValue(t) || Types.isStruct(t) ||
            Types.isContainer(t) || Types.isPrimUpdateable(t)) {
          // No turbine ops block on these types
        } else {
          throw new STCRuntimeError("Don't handle type "
                              + t.toString() + " here");
        }
      }
    }
    return blocksOn;
  }


  @Override
  public TaskMode getMode() {
    switch (op) {
    case STORE_SCALAR:
    case STORE_FILE:
    case STORE_ARRAY:
    case STORE_BAG:
    case STORE_STRUCT:
    case STORE_RECURSIVE:
    case LOAD_SCALAR:
    case LOAD_FILE:
    case LOAD_ARRAY:
    case LOAD_BAG:
    case LOAD_STRUCT:
    case LOAD_RECURSIVE:
    case UPDATE_INCR:
    case UPDATE_MIN:
    case UPDATE_SCALE:
    case UPDATE_INCR_IMM:
    case UPDATE_MIN_IMM:
    case UPDATE_SCALE_IMM:
    case INIT_UPDATEABLE_FLOAT:
    case LATEST_VALUE:
    case ARR_STORE:
    case STRUCT_INIT_FIELDS:
    case STRUCT_STORE_SUB:
    case STRUCT_RETRIEVE_SUB:
    case STRUCT_CREATE_ALIAS:
    case ARR_CREATE_NESTED_IMM:
    case ARRAY_CREATE_BAG:
    case STORE_REF:
    case LOAD_REF:
    case FREE_BLOB:
    case DECR_LOCAL_FILE_REF:
    case GET_FILENAME_ALIAS:
    case GET_LOCAL_FILENAME:
    case IS_MAPPED:
    case COPY_FILE_CONTENTS:
    case ARR_RETRIEVE:
    case COPY_REF:
    case CHOOSE_TMP_FILENAME:
    case GET_FILENAME_VAL:
    case SET_FILENAME_VAL:
    case INIT_LOCAL_OUTPUT_FILE:
    case ARRAY_BUILD:
    case SYNC_COPY_CONTAINER:
    case SYNC_COPY_STRUCT:
    case BAG_INSERT:
    case CHECKPOINT_WRITE_ENABLED:
    case CHECKPOINT_LOOKUP_ENABLED:
    case LOOKUP_CHECKPOINT:
    case WRITE_CHECKPOINT:
    case PACK_VALUES:
    case UNPACK_VALUES:
    case UNPACK_ARRAY_TO_FLAT:
    case ARR_CONTAINS:
    case CONTAINER_SIZE:
    case ARR_LOCAL_CONTAINS:
    case CONTAINER_LOCAL_SIZE:
      return TaskMode.SYNC;
    
    case ARR_COPY_IN_IMM:
    case ARR_STORE_FUTURE:
    case ARR_COPY_IN_FUTURE:
    case AREF_STORE_FUTURE:
    case AREF_COPY_IN_FUTURE:
    case AREF_STORE_IMM:
    case AREF_COPY_IN_IMM:
    case AREF_COPY_OUT_FUTURE:
    case AREF_COPY_OUT_IMM:
    case ARR_COPY_OUT_IMM:
    case DEREF_SCALAR:
    case DEREF_FILE:
    case ARR_COPY_OUT_FUTURE:
    case AREF_CREATE_NESTED_FUTURE:
    case ARR_CREATE_NESTED_FUTURE:
    case AREF_CREATE_NESTED_IMM:
    case ASYNC_COPY_CONTAINER:
    case ASYNC_COPY_STRUCT:
    case STRUCT_COPY_IN:
    case STRUCTREF_STORE_SUB:
    case STRUCTREF_COPY_IN:
    case STRUCT_COPY_OUT:
    case STRUCTREF_COPY_OUT:
    case COPY_IN_FILENAME:
      return TaskMode.LOCAL;
    default:
      throw new STCRuntimeError("Need to add opcode " + op.toString()
          + " to getMode");
    }
  }

  @Override
  public List<ValLoc> getResults() {
    switch(op) {
      case LOAD_SCALAR:
      case LOAD_REF: 
      case LOAD_FILE:
      case LOAD_ARRAY:
      case LOAD_BAG:
      case LOAD_STRUCT:
      case LOAD_RECURSIVE: {
        Arg src = getInput(0);
        Var dst = getOutput(0);

        Closed outIsClosed;
        if (op == Opcode.LOAD_REF) {
          outIsClosed = Closed.MAYBE_NOT;
        } else {
          outIsClosed = Closed.YES_NOT_RECURSIVE;
        }

        if (op == Opcode.LOAD_REF) {
          // use standard deref value
          return ValLoc.derefCompVal(dst, src.getVar(), IsValCopy.NO,
                                     IsAssign.NO).asList();
        } else {
          return vanillaResult(outIsClosed, IsAssign.NO).asList();
        }
      }
      case STORE_REF:
      case STORE_SCALAR:
      case STORE_FILE:
      case STORE_ARRAY:
      case STORE_BAG:
      case STORE_STRUCT:
      case STORE_RECURSIVE: {

        // add assign so we can avoid recreating future 
        // (closed b/c this instruction closes val immediately)
        ValLoc assign = vanillaResult(Closed.YES_NOT_RECURSIVE,
                                      IsAssign.TO_LOCATION);
        // add retrieve so we can avoid retrieving later
        Arg dst = getOutput(0).asArg();
        Arg src = getInput(0);


        if (op == Opcode.STORE_REF) {
          // Use standard dereference computed value
          ValLoc retrieve = ValLoc.derefCompVal(src.getVar(), dst.getVar(),
                                   IsValCopy.NO, IsAssign.NO);
          return Arrays.asList(retrieve, assign);
        } else {
          return assign.asList();
        }
      }
      case IS_MAPPED: {
        // Closed because we don't need to wait to check mapping
        ValLoc vanilla = vanillaResult(Closed.YES_NOT_RECURSIVE,
                                       IsAssign.TO_LOCATION);
        assert(vanilla != null);
        Var fileVar = getInput(0).getVar();
        if (fileVar.isMapped() == Ternary.MAYBE) {
          return vanilla.asList();
        } else {
          // We know the value already, so check it's a constant
          Arg result = Arg.createBoolLit(fileVar.isMapped() == Ternary.TRUE);
          return Arrays.asList(vanilla,
                ValLoc.makeCopy(getOutput(0), result, IsAssign.NO));
        }
      }
      case GET_FILENAME_ALIAS: {
        Arg filename = getOutput(0).asArg();
        Var file = getInput(0).getVar();
        return ValLoc.makeFilename(filename, file).asList();
      }
      case COPY_IN_FILENAME: {
        Arg filename = getInput(0);
        Var file = getOutput(0);
        return ValLoc.makeFilename(filename, file).asList();
      }
      case GET_LOCAL_FILENAME: {
        return ValLoc.makeFilenameLocal(getOutput(0).asArg(),
                getInput(0).getVar(), IsAssign.TO_LOCATION).asList();
      }
      case SET_FILENAME_VAL: {
        Var file = getOutput(0);
        Arg val = getInput(0);
        return ValLoc.makeFilenameVal(file, val, IsAssign.TO_VALUE).asList();
      }
      case GET_FILENAME_VAL: {
        Var file = getInput(0).getVar();
        Var val = getOutput(0);
        return ValLoc.makeFilenameVal(file, val.asArg(), IsAssign.NO).asList();
      }
      case DEREF_SCALAR: 
      case DEREF_FILE: {
        return ValLoc.derefCompVal(getOutput(0), getInput(0).getVar(),
                                   IsValCopy.YES, IsAssign.NO).asList();
      }

      case STRUCT_CREATE_ALIAS: 
      case STRUCT_COPY_OUT:
      case STRUCTREF_COPY_OUT: {
        // Ops that lookup field in struct somehow
        Var struct = getInput(0).getVar();
        List<Arg> fields = getInputsTail(1);
        ValLoc copyV = ValLoc.makeStructFieldCopyResult(getOutput(0),
                                                        struct, fields);
        if (op == Opcode.STRUCT_CREATE_ALIAS) {
          // Create values to repr both alias and value
          ValLoc aliasV = ValLoc.makeStructFieldAliasResult(getOutput(0),
                                struct, fields);
          return Arrays.asList(aliasV, copyV);
        } else {
          // Not an alias - copy val only
          return copyV.asList();
        }
      }
      case STRUCT_RETRIEVE_SUB: {
        Var struct = getInput(0).getVar();
        List<Arg> fields = getInputsTail(1);
        return ValLoc.makeStructFieldValResult(getOutput(0).asArg(),
                                                struct, fields).asList();
      }
      case STRUCT_INIT_FIELDS: {
        List<ValLoc> results = new ArrayList<ValLoc>();
        Out<List<List<Arg>>> fieldPaths = new Out<List<List<Arg>>>();
        Out<List<Arg>> fieldVals = new Out<List<Arg>>();
        unpackStructInitArgs(null, fieldPaths, fieldVals);

        Var struct = getOutput(0);
        
        assert(fieldPaths.val.size() == fieldVals.val.size());
        for (int i = 0; i < fieldPaths.val.size(); i++) {
          results.add(ValLoc.makeStructFieldValResult(fieldVals.val.get(i),
                                  struct, fieldPaths.val.get(i)));
        }
        
        return results;
      }
      case STRUCT_STORE_SUB: 
      case STRUCTREF_STORE_SUB: {
        Var struct = getOutput(0);
        Arg val = getInput(0);
        List<Arg> fields = getInputsTail(1);
        return ValLoc.makeStructFieldValResult(val, struct, fields).asList();
      }
      case STRUCT_COPY_IN:
      case STRUCTREF_COPY_IN: {
        Var struct = getOutput(0);
        Var val = getInput(0).getVar();
        List<Arg> fields = getInputsTail(1);
        return ValLoc.makeStructFieldCopyResult(val, struct, fields).asList();
      }
      case ARR_STORE:
      case ARR_STORE_FUTURE:
      case AREF_STORE_IMM:
      case AREF_STORE_FUTURE:
      case ARR_COPY_IN_IMM:
      case ARR_COPY_IN_FUTURE:
      case AREF_COPY_IN_IMM: 
      case AREF_COPY_IN_FUTURE: {
        // STORE <out array> <in index> <in var>
        Var arr;
        arr = getOutput(0);
        Arg ix = getInput(0);
        Arg member = getInput(1);
        boolean insertingVal = isArrayValStore(op);
        return Arrays.asList(ValLoc.makeArrayResult(arr, ix, member,
                                       insertingVal, IsAssign.TO_VALUE));
      }
      case ARRAY_BUILD: {
        Var arr = getOutput(0);
        List<ValLoc> res = new ArrayList<ValLoc>();
        // Computed value for whole array
        res.add(ValLoc.buildResult(op, getInputs(), arr.asArg(),
                       Closed.YES_NOT_RECURSIVE, IsAssign.NO));
        // For individual array elements
        assert(getInputs().size() % 2 == 0);
        int elemCount = getInputs().size() / 2;
        for (int i = 0; i < elemCount; i++) {
          Arg key = getInput(2 * i);
          Arg val = getInput(2 * i + 1);
          res.add(ValLoc.makeArrayResult(arr, key, val, true,
                                         IsAssign.TO_VALUE));
        }
        
        res.add(ValLoc.makeContainerSizeCV(arr,
                    Arg.createIntLit(elemCount), false, IsAssign.NO));
        return res;
      }
      case ARR_RETRIEVE:
      case ARR_COPY_OUT_IMM:
      case ARR_COPY_OUT_FUTURE:
      case AREF_COPY_OUT_FUTURE:
      case AREF_COPY_OUT_IMM: {
        // LOAD <out var> <in array> <in index>
        Var arr = getInput(0).getVar();
        Arg ix = getInput(1);
        Var contents = getOutput(0);
        

        if (op == Opcode.ARR_RETRIEVE) {
          // This just retrieves the item immediately
          return ValLoc.makeArrayResult(arr, ix, contents.asArg(),
                                         true, IsAssign.NO).asList();
        } else {
          assert (Types.isElemType(arr, contents));
          // Will assign the reference
          return ValLoc.makeArrayResult(arr, ix, contents.asArg(), false,
                                         IsAssign.TO_LOCATION).asList();
        }
      }
      case ARR_CREATE_NESTED_FUTURE:
      case ARR_CREATE_NESTED_IMM:
      case AREF_CREATE_NESTED_FUTURE:
      case AREF_CREATE_NESTED_IMM: 
      case ARRAY_CREATE_BAG: {
        // CREATE_NESTED <out inner array> <in array> <in index>
        // OR
        // CREATE_BAG <out inner bag> <in array> <in index> 
        Var nestedArr = getOutput(0);
        Var arr = getOutput(1);
        Arg ix = getInput(0);
        List<ValLoc> res = new ArrayList<ValLoc>();
        
        boolean returnsNonRef = op == Opcode.ARR_CREATE_NESTED_IMM ||
                                op == Opcode.ARRAY_CREATE_BAG;
        // Mark as not substitutable since this op may have
        // side-effect of creating array
        res.add(ValLoc.makeArrayResult(arr, ix, nestedArr.asArg(),
                                              returnsNonRef, IsAssign.NO));
        res.add(ValLoc.makeCreateNestedResult(arr, ix, nestedArr,
                                              returnsNonRef));
        return res;
      }
      case SYNC_COPY_CONTAINER: 
      case ASYNC_COPY_CONTAINER: 
      case SYNC_COPY_STRUCT:
      case ASYNC_COPY_STRUCT: {
        return ValLoc.makeCopy(getOutput(0), getInput(0),
                               IsAssign.TO_LOCATION).asList();
      }
      case COPY_REF: {
        Var srcRef = getInput(0).getVar();
        return ValLoc.makeAlias(getOutput(0), srcRef).asList();
      }
      case LOOKUP_CHECKPOINT:
      case UNPACK_VALUES: {
        // Both have multiple outputs
        List<ValLoc> res = new ArrayList<ValLoc>(outputs.size()); 
        for (int i = 0; i < outputs.size(); i++) {
          Var out = outputs.get(i);
          res.add(ValLoc.buildResult(op, 
                   (Object)i, getInput(0).asList(), out.asArg(),
                   Closed.YES_RECURSIVE, IsValCopy.NO, IsAssign.NO));
        }
        return res;
      }
      case PACK_VALUES:
        return vanillaResult(Closed.YES_NOT_RECURSIVE, IsAssign.NO).asList();
      case CHECKPOINT_LOOKUP_ENABLED:
      case CHECKPOINT_WRITE_ENABLED:
        return vanillaResult(Closed.YES_NOT_RECURSIVE, IsAssign.NO).asList();
      case UNPACK_ARRAY_TO_FLAT:
        return vanillaResult(Closed.YES_NOT_RECURSIVE, IsAssign.NO).asList();
      case ARR_CONTAINS:
      case CONTAINER_SIZE:
      case ARR_LOCAL_CONTAINS:
      case CONTAINER_LOCAL_SIZE:
        return vanillaResult(Closed.YES_NOT_RECURSIVE, IsAssign.NO).asList();
      default:
        return null;
    }
  }

  private boolean isArrayValStore(Opcode op) {
    switch (op) {
      case ARR_STORE:
      case ARR_STORE_FUTURE:
      case AREF_STORE_IMM:
      case AREF_STORE_FUTURE:
        return true;
      default:
        return false;
    }
  }
  /**
   * Create the "standard" computed value
   * assume 1 output arg
   * @return
   */
  private ValLoc vanillaResult(Closed closed, IsAssign isAssign) {
    assert(outputs.size() == 1);
    return ValLoc.buildResult(op, inputs, outputs.get(0).asArg(), closed,
                              isAssign);
  }

  @Override
  public List<Var> getClosedOutputs() {
    if (op == Opcode.ARRAY_BUILD || op == Opcode.SYNC_COPY_CONTAINER ||
        op == Opcode.SYNC_COPY_STRUCT) {
      // Output array should be closed
      return Collections.singletonList(getOutput(0));
    } else if (op == Opcode.STORE_REF) {
      return Collections.singletonList(getOutput(0));
    }
    return super.getClosedOutputs();
  }

  @Override
  public Instruction clone() {
    return new TurbineOp(op, new ArrayList<Var>(outputs),
                             new ArrayList<Arg>(inputs));
  }

  @Override
  public Pair<List<VarCount>, List<VarCount>> inRefCounts(
                Map<String, Function> functions) {
    switch (op) {
      case STORE_REF:
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           VarCount.NONE);
      case ARRAY_BUILD: {
        List<VarCount> readIncr = new ArrayList<VarCount>();
        for (int i = 0; i < getInputs().size() / 2; i++) {
          // Skip keys and only get values
          Arg elem = getInput(i * 2 + 1);
          // Container gets reference to member
          if (elem.isVar() && RefCounting.trackReadRefCount(elem.getVar())) {
            readIncr.add(VarCount.one(elem.getVar()));
          }
        }
        Var arr = getOutput(0);
        return Pair.create(readIncr, VarCount.one(arr).asList());
      }
      case ASYNC_COPY_CONTAINER:
      case SYNC_COPY_CONTAINER:
      case ASYNC_COPY_STRUCT:
      case SYNC_COPY_STRUCT:
        // Need to pass in refcount for var to be copied, and write
        // refcount for assigned var
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           VarCount.one(getOutput(0)).asList());
      case STORE_BAG:
      case STORE_ARRAY: 
      case STORE_STRUCT:
      case STORE_RECURSIVE: { 
        // Inputs stored into array need to have refcount incremented
        // This finalizes array so will consume refcount
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           VarCount.one(getOutput(0)).asList());
      }
      case DEREF_SCALAR:
      case DEREF_FILE: {
        // Increment refcount of ref var
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           VarCount.NONE);
      }
      case AREF_COPY_OUT_FUTURE:
      case ARR_COPY_OUT_FUTURE: {
        // Array and index
        return Pair.create(Arrays.asList(
            VarCount.one(getInput(0).getVar()), VarCount.one(getInput(1).getVar())),
                VarCount.NONE);
      }
      case AREF_COPY_OUT_IMM:
      case ARR_COPY_OUT_IMM: {
        // Array only
        return Pair.create(
                  VarCount.one(getInput(0).getVar()).asList(),
                  VarCount.NONE);
      }
      case ARR_CONTAINS:
      case CONTAINER_SIZE: {
        // Executes immediately, doesn't need refcount
        return Pair.create(VarCount.NONE, VarCount.NONE);
      }
      case ARR_STORE: {
        Arg mem = getInput(1);
        // Increment reference to memberif needed
        List<VarCount> readIncr;
        if (mem.isVar() && RefCounting.trackReadRefCount(mem.getVar())) {
          readIncr = VarCount.one(mem.getVar()).asList(); 
        } else {
          readIncr = VarCount.NONE;
        }
        return Pair.create(readIncr, VarCount.NONE);
      }
      case ARR_COPY_IN_IMM: {
        // Increment reference to member ref
        // Increment writers count on array
        Var mem = getInput(1).getVar();
        return Pair.create(VarCount.one(mem).asList(),
                           VarCount.one(getOutput(0)).asList());
      }
      case ARR_STORE_FUTURE: 
      case ARR_COPY_IN_FUTURE: {
        // Increment reference to member/member ref and index future
        // Increment writers count on array
        Var arr = getInput(0).getVar();
        Arg mem = getInput(1);

        List<VarCount> readIncr;
        if (mem.isVar() && RefCounting.trackReadRefCount(mem.getVar())) {
          readIncr = Arrays.asList(VarCount.one(arr),
                                   VarCount.one(mem.getVar()));
        } else {
          readIncr = VarCount.one(arr).asList();
        }
        return Pair.create(readIncr, VarCount.one((getOutput(0))).asList());
      }
      case AREF_STORE_IMM:
      case AREF_COPY_IN_IMM:
      case AREF_STORE_FUTURE: 
      case AREF_COPY_IN_FUTURE: {
        Arg ix = getInput(0);
        Arg mem = getInput(1);
        Var arrayRef = getOutput(0);
        List<VarCount> readers = new ArrayList<VarCount>(3);
        readers.add(VarCount.one(arrayRef));
        if (mem.isVar() && RefCounting.trackReadRefCount(mem.getVar())) {
          readers.add(VarCount.one(mem.getVar()));
        }

        if (op == Opcode.AREF_STORE_FUTURE ||
            op == Opcode.AREF_COPY_IN_FUTURE) {
          readers.add(VarCount.one(ix.getVar()));
        } else {
          assert(op == Opcode.AREF_STORE_IMM ||
                 op == Opcode.AREF_COPY_IN_IMM);
        }
        // Management of reference counts from array ref is handled by runtime
        return Pair.create(readers, VarCount.NONE);
      }
      case ARR_CREATE_NESTED_FUTURE: {
        Var srcArray = getOutput(1);
        Var ix = getInput(0).getVar();
        return Pair.create(VarCount.one(ix).asList(),
                           VarCount.one(srcArray).asList());
      }
      case AREF_CREATE_NESTED_IMM:
      case AREF_CREATE_NESTED_FUTURE: {
        Var arr = getOutput(1);
        Arg ixArg = getInput(0);
        List<VarCount> readVars;
        if (op == Opcode.AREF_CREATE_NESTED_IMM) {
          readVars = VarCount.one(arr).asList();
        } else {
          assert(op == Opcode.AREF_CREATE_NESTED_FUTURE);
          readVars = Arrays.asList(VarCount.one(arr),
                                   VarCount.one(ixArg.getVar()));
        }

        // Management of reference counts from array ref is handled by runtime
        return Pair.create(readVars, VarCount.NONE);
      }
      case BAG_INSERT: {
        Arg mem = getInput(0);
        List<VarCount> readers = VarCount.NONE;
        if (mem.isVar() && RefCounting.trackReadRefCount(mem.getVar())) {
          readers = VarCount.one(mem.getVar()).asList();
        }
        return Pair.create(readers, VarCount.NONE);
      }
      case STRUCT_INIT_FIELDS: {
        Out<List<Arg>> fieldVals = new Out<List<Arg>>();
        unpackStructInitArgs(null, null, fieldVals);
        
        List<VarCount> readIncr = new ArrayList<VarCount>();
        List<VarCount> writeIncr = new ArrayList<VarCount>();
        for (Arg fieldVal: fieldVals.val) {
          if (fieldVal.isVar()) {
            // Need to acquire refcount to pass to struct
            Var fieldVar = fieldVal.getVar();
            if (RefCounting.trackReadRefCount(fieldVar)) {
              readIncr.add(VarCount.one(fieldVar));
            }
            if (RefCounting.trackWriteRefCount(fieldVar)) {
              writeIncr.add(VarCount.one(fieldVar));
            }
          }
        }
        
        return Pair.create(readIncr, writeIncr);
      }
      case STRUCTREF_COPY_OUT:
      case STRUCT_COPY_OUT: {
        // Array only
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           VarCount.NONE);
      }
      case STRUCT_STORE_SUB:
      case STRUCT_COPY_IN:
      case STRUCTREF_STORE_SUB:
      case STRUCTREF_COPY_IN:
        // Do nothing: reference count tracker can track variables
        // across struct boundaries
        // TODO: still right?
        return Pair.create(VarCount.NONE, VarCount.NONE);
      case COPY_REF: {
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           VarCount.one(getInput(0).getVar()).asList());
      }
      case COPY_IN_FILENAME: {
        // Read for input filename
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           VarCount.NONE);
      }
      case UPDATE_INCR:
      case UPDATE_MIN:
      case UPDATE_SCALE:
        // Consumes a read refcount for the input argument and
        // write refcount for updated variable
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           VarCount.one(getOutput(0)).asList());
      default:
        // Default is nothing
        return Pair.create(VarCount.NONE, VarCount.NONE);
    }
  }
  
  @Override
  public Pair<List<VarCount>, List<VarCount>> outRefCounts(
                 Map<String, Function> functions) {
    switch (this.op) {
      case COPY_REF: {
        // We incremented refcounts for orig. var, now need to decrement
        // refcount on alias vars
        Var newAlias = getOutput(0);
        return Pair.create(VarCount.one(newAlias).asList(),
                           VarCount.one(newAlias).asList());
      }
      case LOAD_REF: {
        // Load_ref will increment reference count of referand
        Var v = getOutput(0);
        long readRefs = getInput(1).getIntLit();
        long writeRefs = getInput(2).getIntLit();
        // TODO: return actual # of refs
        return Pair.create(new VarCount(v, readRefs).asList(), 
                           new VarCount(v, writeRefs).asList());
      }
      case STRUCT_RETRIEVE_SUB:
        // TODO: other array/struct retrieval funcs
        return Pair.create(VarCount.NONE, VarCount.NONE);
      default:
        return Pair.create(VarCount.NONE, VarCount.NONE);
    }
  }
  
  @Override
  public List<Var> tryPiggyback(Counters<Var> increments, RefCountType type) {
    switch (op) {
      case LOAD_SCALAR:
      case LOAD_FILE:
      case LOAD_ARRAY:
      case LOAD_BAG:
      case LOAD_STRUCT:
      case LOAD_RECURSIVE: {
        Var inVar = getInput(0).getVar();
        if (type == RefCountType.READERS) {
          long amt = increments.getCount(inVar);
          if (amt < 0) {
            assert(getInputs().size() == 1);
            // Add extra arg
            this.inputs = Arrays.asList(getInput(0),
                                      Arg.createIntLit(amt * -1));
            return inVar.asList();
          }
        }
        break;
      }
      case LOAD_REF:
        Var inVar = getInput(0).getVar();
        if (type == RefCountType.READERS) {
          long amt = increments.getCount(inVar);
          if (amt < 0) {
            assert(getInputs().size() == 3);
            // Add extra arg
            this.inputs = Arrays.asList(getInput(0), getInput(1), getInput(2),
                                      Arg.createIntLit(amt * -1));
            return inVar.asList();
          }
        }
        break;
      case ARR_STORE:
      case ARR_COPY_IN_IMM: 
      case ARR_STORE_FUTURE: 
      case ARR_COPY_IN_FUTURE: {
        Var arr = getOutput(0);
        if (type == RefCountType.WRITERS) {
          long amt = increments.getCount(arr);
          if (amt < 0) {
            assert(getInputs().size() == 2);
            // All except the fully immediate version decrement by 1 by default
            int defaultDecr = op == Opcode.ARR_STORE ? 0 : 1;
            Arg decrArg = Arg.createIntLit(amt * -1 + defaultDecr);
            this.inputs = Arrays.asList(getInput(0), getInput(1), decrArg);
            return arr.asList();
          }
        }
        break;
      }
      case ARR_CREATE_NESTED_IMM:
      case ARRAY_CREATE_BAG: {
        // Instruction can give additional refcounts back
        Var nested = getOutput(0);
        assert(getInputs().size() == 3);
        return tryPiggyBackHelper(increments, type, nested, 1, 2);
      }
      case BAG_INSERT: {
        Var bag = getOutput(0);
        return tryPiggyBackHelper(increments, type, bag, -1, 1);
      }
      default:
        // Do nothing
    }

    // Fall through to here if can do nothing
    return Var.NONE;
  }

  
  /**
   * Try to piggyback by applying refcount to an argument
   * @param increments
   * @param type
   * @param var the variable with refcount being managed
   * @param readDecrInput input index of read refcount arg, negative if none
   * @param writeDecrInput input index of write refcount arg, negative if none
   * @return
   */
  private List<Var> tryPiggyBackHelper(Counters<Var> increments,
      RefCountType type, Var var, int readDecrInput, int writeDecrInput) {
    long amt = increments.getCount(var);
    if (amt > 0) {
      // Which argument is increment
      int inputPos;
      if (type == RefCountType.READERS) {
        inputPos = readDecrInput;
      } else {
        assert(type == RefCountType.WRITERS);
        inputPos = writeDecrInput;
      }
      if (inputPos < 0) {
        // No input
        return Var.NONE;
      }
      assert(inputPos < inputs.size());

      Arg oldAmt = getInput(inputPos);
      if (oldAmt.isIntVal()) {
        setInput(inputPos, Arg.createIntLit(oldAmt.getIntLit() + amt));
        return var.asList();
      }
    }
    return Var.NONE;
  }

  @Override
  public List<Alias> getAliases() {
    switch (this.op) {
      case STRUCT_CREATE_ALIAS:
        return Alias.makeStructAliases2(getInput(0).getVar(), getInputsTail(1),
            getOutput(0), AliasTransform.IDENTITY);
      case STRUCT_INIT_FIELDS: {
        Out<List<List<String>>> fieldPaths = new Out<List<List<String>>>();
        Out<List<Arg>> fieldVals = new Out<List<Arg>>();
        List<Alias> aliases = new ArrayList<Alias>();
        unpackStructInitArgs(fieldPaths, null, fieldVals);
        assert (fieldPaths.val.size() == fieldVals.val.size());

        for (int i = 0; i < fieldPaths.val.size(); i++) {
          List<String> fieldPath = fieldPaths.val.get(i);
          Arg fieldVal = fieldVals.val.get(i);
          if (fieldVal.isVar()) {
            aliases.addAll(Alias.makeStructAliases(getOutput(0), fieldPath,
                fieldVal.getVar(), AliasTransform.RETRIEVE));
          }
        }
        return aliases;
      }
      case STRUCT_RETRIEVE_SUB:
        return Alias.makeStructAliases2(getInput(0).getVar(), getInputsTail(1),
            getOutput(0), AliasTransform.RETRIEVE);
      case STRUCT_STORE_SUB:
        return Alias.makeStructAliases2(getOutput(0), getInputsTail(1),
            getInput(0).getVar(), AliasTransform.RETRIEVE);
      case STRUCT_COPY_OUT:
        return Alias.makeStructAliases2(getInput(0).getVar(), getInputsTail(1),
            getOutput(0), AliasTransform.COPY);
      case STRUCT_COPY_IN:
        return Alias.makeStructAliases2(getOutput(0), getInputsTail(1),
            getInput(0).getVar(), AliasTransform.COPY);
      case STORE_REF: {
        // need to track if ref is alias to struct field
        Var ref = getOutput(0);
        Var val = getInput(0).getVar();
        
        return new Alias(ref, Collections.<String>emptyList(),
                         AliasTransform.RETRIEVE, val).asList();
      }
      case LOAD_REF: {
        // need to track if ref is alias to struct field
        Var val = getOutput(0);
        Var ref = getInput(0).getVar();
        
        return new Alias(ref, Collections.<String>emptyList(),
                         AliasTransform.RETRIEVE, val).asList();
      }
      case COPY_REF: {
        // need to track if ref is alias to struct field
        Var ref1 = getOutput(0);
        Var ref2 = getInput(0).getVar();
        return Arrays.asList(
            new Alias(ref1, Collections.<String>emptyList(),
                      AliasTransform.COPY, ref2),
            new Alias(ref2, Collections.<String>emptyList(),
                      AliasTransform.COPY, ref1));
      }
      default:
        // Opcode not relevant
        break;
    }
    return Alias.NONE;
  }

  @Override
  public List<ComponentAlias> getComponentAliases() {
    // TODO: more accurate handling of array or struct refs?
    //       we really need to reflect the different between array refs and arrays, etc
    //       Can we just prefix with an extra wildcard?
    switch (op) {
      case ARR_CREATE_NESTED_IMM:
      case ARR_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_BAG:
        // From inner object to immediately enclosing
        return new ComponentAlias(getOutput(0), getOutput(1), getInput(0)).asList();
      case AREF_CREATE_NESTED_IMM:
      case AREF_CREATE_NESTED_FUTURE:
        // From inner array to immediately enclosing
        return new ComponentAlias(getOutput(0), getOutput(1), getInput(0)).asList();
      case LOAD_REF:
        // If reference was a part of something, modifying the
        // dereferenced object will modify the whole
        return ComponentAlias.ref(getOutput(0), getInput(0).getVar()).asList();
      case COPY_REF:
        // TODO: way to mark alias
        return ComponentAlias.directAlias(getOutput(0), getInput(0).getVar()).asList();
      case STORE_REF:
        // Sometimes a reference is filled in
        return ComponentAlias.ref(getInput(0).getVar(), getOutput(0)).asList();
      case STRUCT_INIT_FIELDS: {
        Out<List<List<Arg>>> fieldPaths = new Out<List<List<Arg>>>();
        Out<List<Arg>> fieldVals = new Out<List<Arg>>();
        List<ComponentAlias> aliases = new ArrayList<ComponentAlias>();
        unpackStructInitArgs(null, fieldPaths, fieldVals);
        assert (fieldPaths.val.size() == fieldVals.val.size());

        Var struct = getOutput(0);
        
        for (int i = 0; i < fieldPaths.val.size(); i++) {
          List<Arg> fieldPath = fieldPaths.val.get(i);
          Arg fieldVal = fieldVals.val.get(i);
          if (fieldVal.isVar()) {
            if (Alias.fieldIsRef(struct, Arg.extractStrings(fieldPath))) {
              // TODO: translate to multiple nodes
              aliases.add(new ComponentAlias(fieldVal.getVar(), struct,
                                    ComponentAlias.deref(fieldPath)));
            }
          }
        }
        return aliases;
      }
      case STRUCT_CREATE_ALIAS: {
        // Output is alias for part of struct
        // TODO: multiple fields
        List<Arg> fields = getInputsTail(1);
        return new ComponentAlias(getOutput(0), getInput(0).getVar(),
                                  fields).asList();
      }
      case STRUCTREF_STORE_SUB:
      case STRUCT_STORE_SUB:
        if (Alias.fieldIsRef(getOutput(0),
                             Arg.extractStrings(getInputsTail(1)))) {
          List<Arg> fields = getInputsTail(1);
          return new ComponentAlias(getInput(0).getVar(), getOutput(0),
                                ComponentAlias.deref(fields)).asList();
        }
        break;
      case STRUCT_RETRIEVE_SUB:
        if (Alias.fieldIsRef(getInput(0).getVar(),
                             Arg.extractStrings(getInputsTail(1)))) {
          List<Arg> fields = getInputsTail(1);
          return new ComponentAlias(getOutput(0), getInput(0).getVar(),
                                ComponentAlias.deref(fields)).asList();
        }
        break;
      case STRUCTREF_COPY_IN:
      case STRUCT_COPY_IN:
        if (Alias.fieldIsRef(getOutput(0),
                             Arg.extractStrings(getInputsTail(1)))) {
          List<Arg> fields = getInputsTail(1);
          return new ComponentAlias(getInput(0).getVar(),
                                    getOutput(0), fields).asList();
        }
        break;
      case STRUCTREF_COPY_OUT:
      case STRUCT_COPY_OUT:
        if (Alias.fieldIsRef(getInput(0).getVar(),
                             Arg.extractStrings(getInputsTail(1)))) {
          List<Arg> fields = getInputsTail(1);
          return new ComponentAlias(getOutput(0),
                  getInput(0).getVar(), fields).asList();
        }
        break;
      default:
        // Return nothing
        break;
    }
    return Collections.emptyList();
  }

  public boolean isIdempotent() {
    switch (op) {
      case ARR_CREATE_NESTED_FUTURE:
      case ARR_CREATE_NESTED_IMM:
      case AREF_CREATE_NESTED_FUTURE:
      case AREF_CREATE_NESTED_IMM:
      case ARRAY_CREATE_BAG:
        return true;
      default:
        return false;
    }
  }
  
  
  /**
   * Instruction class specifically for reference counting operations with
   * defaults derived from TurbineOp
   */
  public static class RefCountOp extends TurbineOp {
    
    /**
     * Direction of change (increment or decrement)
     */
    public static enum RCDir {
      INCR,
      DECR;
      
      public static RCDir fromAmount(long amount) {
        if (amount >= 0) {
          return INCR;
        } else {
          return DECR;
        }
      }
    };
    
    public RefCountOp(Var target, RCDir dir, RefCountType type, Arg amount) {
      super(getRefCountOp(dir, type), Var.NONE,
            Arrays.asList(target.asArg(), amount));
    }
    
    public static RefCountOp decrWriters(Var target, Arg amount) {
      return new RefCountOp(target, RCDir.DECR, RefCountType.WRITERS, amount);
    }
    
    public static RefCountOp incrWriters(Var target, Arg amount) {
      return new RefCountOp(target, RCDir.INCR, RefCountType.WRITERS, amount);
    }
    
    public static RefCountOp decrRef(Var target, Arg amount) {
      return new RefCountOp(target, RCDir.DECR, RefCountType.READERS, amount);
    }
    
    public static RefCountOp incrRef(Var target, Arg amount) {
      return new RefCountOp(target, RCDir.INCR, RefCountType.READERS, amount);
    }
    
    public static RefCountOp decrRef(RefCountType rcType, Var v, Arg amount) {
      return new RefCountOp(v, RCDir.DECR, rcType, amount);
    }
    
    public static RefCountOp incrRef(RefCountType rcType, Var v, Arg amount) {
      return new RefCountOp(v, RCDir.INCR, rcType, amount);
    }
    
    public static RefCountType getRCType(Opcode op) {
      assert(isRefcountOp(op));
      if (op == Opcode.INCR_READERS || op == Opcode.DECR_READERS) {
        return RefCountType.READERS;
      } else {
        assert(op == Opcode.INCR_WRITERS || op == Opcode.DECR_WRITERS);
        return RefCountType.WRITERS;
      }
    }

    public static Var getRCTarget(Instruction refcountOp) {
      assert(isRefcountOp(refcountOp.op));
      return refcountOp.getInput(0).getVar();
    }
    
    public static Arg getRCAmount(Instruction refcountOp) {
      assert(isRefcountOp(refcountOp.op));
      return refcountOp.getInput(1);
    }
    
    
    
    private static Opcode getRefCountOp(RCDir dir, RefCountType type) {
      if (type == RefCountType.READERS) {
        if (dir == RCDir.INCR) {
          return Opcode.INCR_READERS;
        } else {
          assert(dir == RCDir.DECR);
          return Opcode.DECR_READERS;
        }
      } else {
        assert(type == RefCountType.WRITERS);
        if (dir == RCDir.INCR) {
          return Opcode.INCR_WRITERS;
        } else {
          assert(dir == RCDir.DECR);
          return Opcode.DECR_WRITERS;
        }
      }
    }
    
    private static RCDir getRefcountDir(Opcode op) {
      if (isIncrement(op)) {
        return RCDir.INCR;
      } else {
        assert(isDecrement(op));
        return RCDir.DECR;
      }
    }

    public static boolean isIncrement(Opcode op) {
      return (op == Opcode.INCR_READERS || op == Opcode.INCR_WRITERS);
    }
    
    public static boolean isDecrement(Opcode op) {
      return (op == Opcode.DECR_READERS || op == Opcode.DECR_WRITERS);
    }

    public static boolean isRefcountOp(Opcode op) {
      return isIncrement(op) || isDecrement(op);
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      switch (op) {
        case DECR_WRITERS:
          gen.decrWriters(getRCTarget(this), getRCAmount(this));
          break;
        case INCR_WRITERS:
          gen.incrWriters(getRCTarget(this), getRCAmount(this));
          break;
        case DECR_READERS:
          gen.decrRef(getRCTarget(this), getRCAmount(this));
          break;
        case INCR_READERS:
          gen.incrRef(getRCTarget(this), getRCAmount(this));
          break;
        default:
          throw new STCRuntimeError("Unknown op type: " + op);
      }
    }

    @Override
    public TaskMode getMode() {
      // Executes right away
      return TaskMode.SYNC;
    }
    
    @Override
    public boolean hasSideEffects() {
      // Model refcount change as side-effect
      return true;
    }

    @Override
    public Instruction clone() {
      return new RefCountOp(getRCTarget(this), getRefcountDir(this.op),
                            getRCType(this.op), getRCAmount(this));
    }
  }

}