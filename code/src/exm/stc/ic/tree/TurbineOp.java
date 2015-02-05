package exm.stc.ic.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.CompilerBackend.DirRefCount;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Arg.ArgKind;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.Operators.UpdateMode;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Unimplemented;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.VarCount;
import exm.stc.common.util.Out;
import exm.stc.common.util.Pair;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.ICUtil;
import exm.stc.ic.aliases.Alias;
import exm.stc.ic.aliases.Alias.AliasTransform;
import exm.stc.ic.componentaliases.Component;
import exm.stc.ic.componentaliases.ComponentAlias;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ValLoc;
import exm.stc.ic.opt.valuenumber.ValLoc.Closed;
import exm.stc.ic.opt.valuenumber.ValLoc.IsAssign;
import exm.stc.ic.opt.valuenumber.ValLoc.IsValCopy;
import exm.stc.ic.refcount.RefCountsToPlace;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GenInfo;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;

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
      gen.assignReference(getOutput(0), getInput(0).getVar(),
            getInput(1).getInt(), getInput(2).getInt());
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
    case STORE_ARRAY_RECURSIVE:
      gen.assignArrayRecursive(getOutput(0), getInput(0));
      break;
    case STORE_STRUCT_RECURSIVE:
      gen.assignStructRecursive(getOutput(0), getInput(0));
      break;
    case STORE_BAG_RECURSIVE:
      gen.assignBagRecursive(getOutput(0), getInput(0));
      break;
    case ARR_RETRIEVE:
      gen.arrayRetrieve(getOutput(0), getInput(0).getVar(), getInput(1),
                        getInput(2), getInput(3));
      break;
    case ARR_CREATE_ALIAS:
      gen.arrayCreateAlias(getOutput(0), getInput(0).getVar(),
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
    case ASYNC_COPY: {
      gen.asyncCopy(getOutput(0), getInput(0).getVar());
      break;
    }
    case SYNC_COPY: {
      gen.syncCopy(getOutput(0), getInput(0).getVar());
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
      gen.structRetrieveSub(getOutput(0), getInput(0).getVar(), Arg.extractStrings(getInputsTail(2)),
                         getInput(1));
      break;
    case STRUCT_COPY_OUT:
      gen.structCopyOut(getOutput(0), getInput(0).getVar(),
                         Arg.extractStrings(getInputsTail(1)));
      break;
    case STRUCTREF_COPY_OUT:
      gen.structRefCopyOut(getOutput(0), getInput(0).getVar(),
                          Arg.extractStrings(getInputsTail(1)));
      break;
    case STRUCT_LOCAL_BUILD: {
      Out<List<List<String>>> fieldPaths = new Out<List<List<String>>>();
      Out<List<Arg>> fieldVals = new Out<List<Arg>>();
      unpackStructBuildArgs(fieldPaths, null, fieldVals);

      gen.buildStructLocal(getOutput(0), fieldPaths.val, fieldVals.val);
      break;
    }
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
    case STRUCT_CREATE_NESTED:
      gen.structCreateNested(getOutput(0), getOutput(1),
                Arg.extractStrings(getInputsTail(STRUCT_NESTED_FIELDS_START)),
                getInput(0), getInput(1), getInput(2), getInput(3));
      break;
    case DEREF_SCALAR:
      gen.dereferenceScalar(getOutput(0), getInput(0).getVar());
      break;
    case DEREF_FILE:
      gen.dereferenceFile(getOutput(0), getInput(0).getVar());
      break;
    case LOAD_REF:
      gen.retrieveReference(getOutput(0), getInput(0).getVar(), getInput(1),
            getInput(2), getInput(3));
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
                    getInput(1), getInput(2), getInput(3), getInput(4));
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
    case LOAD_ARRAY_RECURSIVE:
      gen.retrieveArrayRecursive(getOutput(0), getInput(0).getVar(),
              getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case LOAD_STRUCT_RECURSIVE:
      gen.retrieveStructRecursive(getOutput(0), getInput(0).getVar(),
              getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case LOAD_BAG_RECURSIVE:
      gen.retrieveBagRecursive(getOutput(0), getInput(0).getVar(),
              getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case FREE_BLOB:
      gen.freeBlob(getOutput(0));
      break;
    case DECR_LOCAL_FILE_REF:
      gen.decrLocalFileRefCount(getInput(0).getVar());
      break;
    case INIT_UPDATEABLE_FLOAT:
      gen.initScalarUpdateable(getOutput(0), getInput(0));
      break;
    case LATEST_VALUE:
      gen.latestValue(getOutput(0), getInput(0).getVar());
      break;
    case UPDATE_INCR:
      gen.updateScalarFuture(getOutput(0), UpdateMode.INCR, getInput(0).getVar());
      break;
    case UPDATE_MIN:
      gen.updateScalarFuture(getOutput(0), UpdateMode.MIN, getInput(0).getVar());
      break;
    case UPDATE_SCALE:
      gen.updateScalarFuture(getOutput(0), UpdateMode.SCALE, getInput(0).getVar());
      break;
    case UPDATE_INCR_IMM:
      gen.updateScalarImm(getOutput(0), UpdateMode.INCR, getInput(0));
      break;
    case UPDATE_MIN_IMM:
      gen.updateScalarImm(getOutput(0), UpdateMode.MIN, getInput(0));
      break;
    case UPDATE_SCALE_IMM:
      gen.updateScalarImm(getOutput(0), UpdateMode.SCALE, getInput(0));
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

  public static Instruction arrayRetrieve(Var dst, Var arrayVar, Arg arrIx) {
    long decrRead = 0;
    long acquireRead = RefCounting.trackReadRefCount(dst) ? 1 : 0;
    return arrayRetrieve(dst, arrayVar, arrIx, decrRead, acquireRead);
  }

  /**
   * Look up value of array index immediately
   * @param dst
   * @param arrayVar
   * @param arrIx
   * @param decrRead
   * @param acquireRead
   * @return
   */
  public static Instruction arrayRetrieve(Var dst, Var arrayVar,
                                          Arg arrIx, long decrRead, long acquireRead) {
    assert(dst.storage() == Alloc.LOCAL || dst.storage() == Alloc.ALIAS);
    assert(Types.isArray(arrayVar));
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(Types.isElemValType(arrayVar, dst));

    return new TurbineOp(Opcode.ARR_RETRIEVE, dst, arrayVar.asArg(), arrIx,
        Arg.newInt(decrRead), Arg.newInt(acquireRead));
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

  public static Instruction arrayCreateAlias(Var dst, Var arrayVar,
      Arg arrIx) {
    assert(Types.isArray(arrayVar));
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(Types.isElemType(arrayVar, dst)) : arrayVar + " " + dst;
    assert(dst.storage() == Alloc.ALIAS);

    // Check that we can generate valid code for it
    assert(Unimplemented.subscriptAliasSupported(arrayVar));

    return new TurbineOp(Opcode.ARR_CREATE_ALIAS,
                         dst, arrayVar.asArg(), arrIx);
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
    assert(Types.isArray(arrayVar)) : arrayVar;
    assert(Types.isArrayKeyVal(arrayVar, arrIx)) :
                arrayVar.type() + " " + arrIx.type();
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
    assert(Types.isArray(array));
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
    assert(Types.simpleCopySupported(member));

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
    assert(Types.isArray(array));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(Types.isElemType(array, member));
    assert(Types.simpleCopySupported(member));

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
    assert(Types.simpleCopySupported(member));

    return new TurbineOp(Opcode.AREF_COPY_IN_IMM,
                         array, ix, member.asArg());
  }

  public static Instruction arrayRefCopyInFuture(Var array, Var ix,
                                                  Var member) {
    assert(Types.isArrayKeyFuture(array, ix));
    assert(Types.isArrayRef(array, true));
    assert(Types.isElemType(array, member));
    assert(Types.simpleCopySupported(member));

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
    assert(Types.isArray(array));
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

  /**
   * Generic async copy instruction for non-local data
   */
  public static Instruction asyncCopy(Var dst, Var src) {
    assert(src.type().assignableTo(dst.type()));
    assert(dst.storage() != Alloc.LOCAL);
    return new TurbineOp(Opcode.ASYNC_COPY, dst, src.asArg());
  }

  /**
   * Generic sync copy instruction for non-local data.
   * Assumes input data closed
   */
  public static Instruction syncCopy(Var dst, Var src) {
    assert(src.type().assignableTo(dst.type()));
    assert(dst.storage() != Alloc.LOCAL);
    return new TurbineOp(Opcode.SYNC_COPY, dst, src.asArg());
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
    assert(writersDecr.isImmInt());
    return new TurbineOp(Opcode.BAG_INSERT, bag, elem, writersDecr);
  }

  /**
   * Retrieve value of a struct entry
   *
   * TODO: for case of ref entries, separate op to get writable reference?
   * @param dst
   * @param structVar
   * @param fields
   * @param readDecr
   * @return
   */
  public static Instruction structRetrieveSub(Var dst, Var structVar,
                                           List<String> fields, Arg readDecr) {
    assert(Types.isStruct(structVar));
    assert(Types.isStructFieldVal(structVar, fields, dst)) :
          "(" + structVar.name()  + ":" + structVar.type()  + ")." + fields
          + " => " + dst;
    assert (readDecr.isImmInt());

    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    in.add(structVar.asArg());
    in.add(readDecr);
    for (String field: fields) {
      in.add(Arg.newString(field));
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
      in.add(Arg.newString(field));
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
      in.add(Arg.newString(field));
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
      in.add(Arg.newString(field));
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
    assert(Types.isStruct(structVar)) : structVar;
    assert(Types.isStructFieldVal(structVar, fields, fieldVal));

    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    in.add(fieldVal);
    for (String field: fields) {
      in.add(Arg.newString(field));
    }
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
    assert(Types.isStructField(structVar, fields, fieldVar)) :
        structVar + " " + fields + " = " + fieldVar;
    assert(Types.simpleCopySupported(fieldVar));

    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    in.add(fieldVar.asArg());
    for (String field: fields) {
      in.add(Arg.newString(field));
    }
    return new TurbineOp(Opcode.STRUCT_COPY_IN, structVar.asList(), in);
  }

  public static Instruction structRefStoreSub(Var structVar,
      List<String> fields, Arg fieldVal) {
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    in.add(fieldVal);
    for (String field: fields) {
      in.add(Arg.newString(field));
    }
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
    assert(Types.simpleCopySupported(fieldVar));

    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    in.add(fieldVar.asArg());
    for (String field: fields) {
      in.add(Arg.newString(field));
    }
    return new TurbineOp(Opcode.STRUCTREF_COPY_IN, structRef.asList(), in);
  }


 public static Instruction structCreateNested(Var result,
     Var struct, List<String> fields) {
   return structCreateNested(result, struct, fields,
               Arg.ONE, Arg.ONE, Arg.ZERO, Arg.ZERO);
  }

  static final int STRUCT_NESTED_FIELDS_START = 4;

  /**
   * Create a nested datum inside the current one, or return current
   * nested datum if present.  Acquire read + write reference
   * to nested datum.
   * @param result
   * @param struct
   * @param fields
   * @return
   */
  public static Instruction structCreateNested(Var result,
      Var struct, List<String> fields, Arg readAcquire, Arg writeAcquire,
      Arg readDecr, Arg writeDecr) {
    assert(Types.isNonLocal(result));
    assert(Types.isStruct(struct));
    assert(result.storage() == Alloc.ALIAS);
    assert(Types.isStructFieldVal(struct, fields, result)) :
          struct.type().typeName() + "." + fields + " => " +
          result.type().typeName();
    assert(readAcquire.isImmInt());
    assert(writeAcquire.isImmInt());
    assert(readDecr.isImmInt());
    assert(writeDecr.isImmInt());

    List<Arg> in = new ArrayList<Arg>();
    in.add(readAcquire);
    in.add(writeAcquire);
    in.add(readDecr);
    in.add(writeDecr);
    assert(in.size() == STRUCT_NESTED_FIELDS_START); // Check constant

    // Variable number of fields goes at end
    for (String field: fields) {
      in.add(Arg.newString(field));
    }

    // Both arrays are modified, so outputs
    return new TurbineOp(Opcode.STRUCT_CREATE_NESTED,
        Arrays.asList(result, struct), in);
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
    assert(Types.isFile(dst));
    assert(src.isVar());
    assert(Types.isFileVal(src.getVar()));
    assert(setFilename.isImmBool());
    if (setFilename.isBool() && setFilename.getBool()) {
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
    assert(Types.isArray(dst)) : dst;
    assert(Types.isArrayLocal(src)) : src + " " + src.type();
    assert(Types.arrayKeyType(src).assignableTo(Types.arrayKeyType(dst)));
    assert(Types.containerElemType(src).assignableTo(
              Types.containerElemValType(dst)));
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
    assert(Types.isBagLocal(src)) : src.type();
    assert(Types.containerElemType(src).assignableTo(
              Types.containerElemValType(dst)));
    return new TurbineOp(Opcode.STORE_BAG, dst, src);
  }

  public static Statement structLocalBuild(Var struct,
      List<List<String>> fieldPaths, List<Arg> fieldVals) {
    assert(Types.isStructLocal(struct));

    List<Arg> inputs = new ArrayList<Arg>();

    packFieldData(struct, fieldPaths, fieldVals, inputs);

    return new TurbineOp(Opcode.STRUCT_LOCAL_BUILD, struct.asList(), inputs);
  }

  public void unpackStructBuildArgs(Out<List<List<String>>> fieldPaths,
      Out<List<List<Arg>>> fieldPathsArgs,
      Out<List<Arg>> fieldVals) {
    assert(op == Opcode.STRUCT_LOCAL_BUILD) : op;

    List<Arg> packedFieldData = inputs;
    unpackFieldData(packedFieldData, fieldPaths, fieldPathsArgs, fieldVals);
  }

  /**
   * Pack info about struct fields into arg list
   * @param struct
   * @param fieldPaths
   * @param fieldVals
   * @param result
   */
  private static void packFieldData(Typed structType,
      List<List<String>> fieldPaths, List<Arg> fieldVals, List<Arg> result) {
    assert(fieldPaths.size() == fieldVals.size());
    for (int i = 0; i < fieldPaths.size(); i++) {
      List<String> fieldPath = fieldPaths.get(i);
      Arg fieldVal = fieldVals.get(i);
      assert(Types.isStructFieldVal(structType, fieldPath, fieldVal))
            : structType + " " + fieldPath + " " + fieldVal.getVar() + "\n"
              + structType.type();
      // encode lists with length prefixed
      result.add(Arg.newInt(fieldPath.size()));
      for (String field: fieldPath) {
        result.add(Arg.newString(field));
      }
      result.add(fieldVal);
    }
  }

  private static void unpackFieldData(List<Arg> packedFieldData,
      Out<List<List<String>>> fieldPaths, Out<List<List<Arg>>> fieldPathsArgs,
      Out<List<Arg>> fieldVals) {
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
    while (pos < packedFieldData.size()) {
      long pathLength = packedFieldData.get(pos).getInt();
      assert(pathLength > 0 && pathLength <= Integer.MAX_VALUE);
      pos++;

      List<String> fieldPath = (fieldPaths == null) ? null:
                            new ArrayList<String>((int)pathLength);

      List<Arg> fieldPathArgs = (fieldPathsArgs == null) ? null:
                            new ArrayList<Arg>((int)pathLength);


      for (int i = 0; i < pathLength; i++) {
        if (fieldPath != null) {
          fieldPath.add(packedFieldData.get(pos).getString());
        }

        if (fieldPathArgs != null) {
          fieldPathArgs.add(packedFieldData.get(pos));
        }
        pos++;
      }

      Arg fieldVal = packedFieldData.get(pos);
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
    assert(Types.isScalarFuture(src));
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
    assert(Types.isFile(src));
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
    assert(Types.isArray(src));
    assert(Types.isArrayLocal(dst));
    assert(Types.containerElemValType(src).assignableTo(
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
    assert(Types.isBag(src));
    assert(Types.isBagLocal(target));
    assert(Types.containerElemValType(src).assignableTo(
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
    assert(Types.isStruct(src));
    assert(Types.isStructLocal(dst));
    assert(StructType.localStruct((StructType)src.type().getImplType())
                                            .assignableTo(dst.type())):
                src.type() + " => " + dst.type();
    return new TurbineOp(Opcode.LOAD_STRUCT, dst, src.asArg());
  }

  /**
   * Store a completely unpacked array/bag/etc to the standard shared
   * representation
   * @param dst
   * @param src
   * @return
   */
  public static Instruction storeRecursive(Var dst, Arg src) {
    assert(Types.isContainer(dst) || Types.isStruct(dst));
    assert(Types.isContainerLocal(src) || Types.isStructLocal(src));
    assert(src.type().assignableTo(
            Types.unpackedType(dst))) : src + ":" + src.type()
                                                          + " " + dst;
    Opcode op;
    if (Types.isArray(dst)) {
      op = Opcode.STORE_ARRAY_RECURSIVE;
    } else if (Types.isStruct(dst)) {
      op = Opcode.STORE_STRUCT_RECURSIVE;
    } else {
      assert(Types.isBag(dst)) : src.type();
      op = Opcode.STORE_BAG_RECURSIVE;
    }
    return new TurbineOp(op, dst, src);
  }

  /**
   * Retrieve an array/bag/etc, following all references to included.
   * src must be recursively closed
   * @param dst
   * @param src
   * @return
   */
  public static Instruction retrieveRecursive(Var dst, Var src) {
    Type unpackedSrcType = Types.unpackedType(src);
    assert(unpackedSrcType.assignableTo(dst.type())) :
            unpackedSrcType + " => " + dst;

    Opcode op;
    if (Types.isArray(src)) {
      op = Opcode.LOAD_ARRAY_RECURSIVE;
    } else if (Types.isStruct(src)) {
      op = Opcode.LOAD_STRUCT_RECURSIVE;
    } else {
      assert(Types.isBag(src)) : src.type();
      op = Opcode.LOAD_BAG_RECURSIVE;
    }

    return new TurbineOp(op, dst, src.asArg());
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

  public static Instruction storeRef(Var dst, Var src, boolean mutable) {
    return storeRef(dst, src, 1, mutable ? 1 : 0);
  }

  /**
   * Store a reference
   * @param dst reference to store to
   * @param src some datastore object
   * @param readRefs read reference counts to transfer
   * @param writeRefs write reference counts to transfer
   * @return
   */
  public static Instruction storeRef(Var dst, Var src,
                          long readRefs, long writeRefs) {
    assert(Types.isRef(dst));
    assert(readRefs >= 0);
    assert(writeRefs >= 0);
    if (writeRefs == 0) {
      assert(Types.isRef(dst, false));
    } else {
      assert(Types.isMutableRef(dst)) : dst;
    }
    assert(src.type().assignableTo(Types.retrievedType(dst)));

    return new TurbineOp(Opcode.STORE_REF, dst, src.asArg(),
           Arg.newInt(readRefs), Arg.newInt(writeRefs));
  }

  /**
   * Helper to generate appropriate store instruction for any type
   * if possible
   * @param dst
   * @param src
   * @return
   */
  public static Instruction storeAny(Var dst, Arg src,
                                     boolean recursive) {
    assert(src.type().assignableTo(Types.retrievedType(dst, recursive)));
    if (Types.isRef(dst)) {
      assert(src.isVar());
      boolean mutable = Types.isMutableRef(dst);
      return storeRef(dst, src.getVar(), mutable);
    } else if (Types.isPrimFuture(dst)) {
      // Regular store?
      return storePrim(dst, src);
    } else if (Types.isArray(dst)) {
      assert(src.isVar()) : src;
      if (recursive) {
        return storeRecursive(dst, src);
      } else {
        return assignArray(dst, src);
      }
    } else if (Types.isBag(dst)) {
      assert(src.isVar());
      if (recursive) {
        return storeRecursive(dst, src);
      } else {
        return assignBag(dst, src);
      }
    } else if (Types.isStruct(dst)) {
      assert(src.isVar());
      if (recursive) {
        return storeRecursive(dst, src);
      } else {
        return assignStruct(dst, src);
      }
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


  public static Instruction retrieveRef(Var dst, Var src, boolean mutable) {
    long defaultAcquire = 1;
    return retrieveRef(dst, src, defaultAcquire, mutable ? defaultAcquire : 0,
                       Arg.ZERO);
  }

  /**
   * Retrieve a reference to a local handle
   * @param dst alias variable to hold handle to referenced data
   * @param src Closed reference
   * @param acquireRead num of read refcounts to acquire
   * @param acquireWrite num of write refcounts to acquire
   * @param decrRead num of read refcounts to decr on input
   * @return
   */
  public static Instruction retrieveRef(Var dst, Var src,
                     long acquireRead, long acquireWrite,
                     Arg decrRead) {
    assert(Types.isRef(src.type()));
    assert(acquireRead >= 0);
    assert(acquireWrite >= 0);
    assert(decrRead.isImmInt());

    if (acquireWrite > 0) {
      assert(Types.isAssignableRefTo(src, dst, true)) :
            src + " " + dst + " r: " + acquireRead + " w: " + acquireWrite;
    } else {
      assert(Types.isAssignableRefTo(src, dst));
    }
    assert(dst.storage() == Alloc.ALIAS);
    return new TurbineOp(Opcode.LOAD_REF, dst, src.asArg(),
          Arg.newInt(acquireRead), Arg.newInt(acquireWrite),
          decrRead);
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
    assert(Types.isArrayRef(arrayResult, true));
    assert(arrayResult.storage() != Alloc.ALIAS);
    assert(Types.isArray(array.type()));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(!Types.isConstRef(arrayResult)); // Should be mutable if ref
    // Both arrays are modified, so outputs
    return new TurbineOp(Opcode.ARR_CREATE_NESTED_FUTURE,
        Arrays.asList(arrayResult, array), ix.asArg());
  }

  /**
   * arrayCreateNestedImm with default refcount settings
   */
  public static Instruction arrayCreateNestedImm(Var arrayResult,
      Var arrayVar, Arg arrIx) {
    return arrayCreateNestedImm(arrayResult, arrayVar, arrIx,
                        Arg.ONE, Arg.ONE, Arg.ZERO, Arg.ZERO);
  }

  /**
   * Create a nested datum inside the current one, or return current
   * nested datum if not present.  Acquire read + write reference
   * to nested datum.
   * @param result
   * @param arrayVar
   * @param arrIx
   * @return
   */
  public static Instruction arrayCreateNestedImm(Var result,
      Var arrayVar, Arg arrIx, Arg readAcquire, Arg writeAcquire,
      Arg readDecr, Arg writeDecr) {
    assert(Types.isNonLocal(result));
    assert(Types.isArray(arrayVar));
    assert(result.storage() == Alloc.ALIAS);
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(readAcquire.isImmInt());
    assert(writeAcquire.isImmInt());
    assert(readDecr.isImmInt());
    assert(writeDecr.isImmInt());

    // Both arrays are modified, so outputs
    return new TurbineOp(Opcode.ARR_CREATE_NESTED_IMM,
        Arrays.asList(result, arrayVar),
        arrIx, readAcquire, writeAcquire, readDecr, writeDecr);
  }

  public static Instruction arrayRefCreateNestedComputed(Var result,
                                                       Var array, Var ix) {
    assert(Types.isNonLocalRef(result, true)): result;
    assert(result.storage() != Alloc.ALIAS);
    assert(Types.isArrayRef(array, true)): array;
    assert(Types.isArrayKeyFuture(array, ix));
    assert(!Types.isConstRef(result)); // Should be mutable if ref
    // Returns nested array, modifies outer array and
    // reference counts outmost array
    return new TurbineOp(Opcode.AREF_CREATE_NESTED_FUTURE,
        Arrays.asList(result, array),
        ix.asArg());
  }

  /**
   *
   * @param result
   * @param outerArray
   * @param array
   * @param ix
   * @return
   */
  public static Instruction arrayRefCreateNestedImmIx(Var result,
                                                   Var array, Arg ix) {
    assert(Types.isNonLocalRef(result, true)): result;
    assert(result.storage() != Alloc.ALIAS);
    assert(Types.isArrayRef(array, true)): array;
    assert(Types.isArrayKeyVal(array, ix));
    assert(!Types.isConstRef(result)); // Should be mutable if ref
    return new TurbineOp(Opcode.AREF_CREATE_NESTED_IMM,
        // Returns nested array, modifies outer array and
        // reference counts outmost array
        Arrays.asList(result, array),
        ix);
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
    assert(Types.isFile(file));
    assert(filenameVal.isImmString());
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
    assert(Types.isStringVal(outFilename));
    assert(Types.isBoolVal(isMapped));
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
    assert(Types.isBlobVal(key));
    assert(Types.isBlobVal(value));
    return new TurbineOp(Opcode.WRITE_CHECKPOINT, Var.NONE, key, value);
  }

  public static Instruction lookupCheckpoint(Var checkpointExists, Var value,
      Arg key) {
    assert(Types.isBoolVal(checkpointExists));
    assert(Types.isBlobVal(value));
    assert(Types.isBlobVal(key));
    return new TurbineOp(Opcode.LOOKUP_CHECKPOINT,
        Arrays.asList(checkpointExists, value), key);
  }

  public static Instruction packValues(Var packedValues, List<Arg> values) {
    for (Arg val: values) {
      assert(val.isConst() || val.getVar().storage() == Alloc.LOCAL);
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
  public void renameVars(String function, Map<Var, Arg> renames, RenameMode mode) {
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
    case SYNC_COPY:
    case ASYNC_COPY:
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
    case STORE_ARRAY_RECURSIVE:
    case STORE_BAG_RECURSIVE:
    case STORE_STRUCT_RECURSIVE:
    case DEREF_SCALAR:
    case DEREF_FILE:
    case LOAD_SCALAR:
    case LOAD_FILE:
    case LOAD_ARRAY:
    case LOAD_BAG:
    case LOAD_STRUCT:
    case LOAD_ARRAY_RECURSIVE:
    case LOAD_STRUCT_RECURSIVE:
    case LOAD_BAG_RECURSIVE:
    case STRUCT_LOCAL_BUILD:
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
      if (getInput(1).isBool() && getInput(1).getBool() == false) {
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
    case ARR_CREATE_ALIAS:
        // Always has alias as output because the instructions initialises
        // the aliases
        return false;

    case ARR_CREATE_NESTED_FUTURE:
    case AREF_CREATE_NESTED_FUTURE:
    case ARR_CREATE_NESTED_IMM:
    case AREF_CREATE_NESTED_IMM:
    case STRUCT_CREATE_NESTED:
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


  @Override
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
        return MakeImmRequest.fromVars(Var.NONE, Var.NONE);
      }
      break;
    }
    case ARR_COPY_OUT_FUTURE: {
      Var index = getInput(1).getVar();
      if (waitForClose || closedVars.contains(index)) {
        return MakeImmRequest.fromVars(Var.NONE, index);
      }
      break;
    }
    case AREF_COPY_OUT_FUTURE: {
      Var arr = getInput(0).getVar();
      Var ix = getInput(1).getVar();
      // We will take either the index or the dereferenced array
      List<MakeImmVar> req = mkImmVarList(waitForClose, closedVars,
                                    true, false, false, arr, ix);
      if (req.size() > 0) {
        return new MakeImmRequest(null, req);
      }
      break;
    }
    case AREF_COPY_OUT_IMM: {
      // Could skip using reference
      Var arrRef = getInput(0).getVar();
      if (waitForClose || closedVars.contains(arrRef)) {
        return MakeImmRequest.fromVars(Var.NONE, arrRef);
      }
      break;
    }
    case ARR_CONTAINS: {
      Var arr = getInput(0).getVar();
      // check to see if local version of array available
      // (Already assuming array closed)
      if (valueAvail.contains(arr)) {
        return MakeImmRequest.fromVars(Var.NONE, arr);
      }
      break;
    }
    case CONTAINER_SIZE: {
      Var cont = getInput(0).getVar();
      // check to see if local version of container available
      // (Already assuming array closed)
      if (valueAvail.contains(cont)) {
        return MakeImmRequest.fromVars(Var.NONE, cont);
      }
      break;
    }
    case STRUCT_COPY_IN: {
      Var val = getInput(0).getVar();
      if (waitForClose || closedVars.contains(val)) {
        return MakeImmRequest.fromVars(Var.NONE, val);
      }
      break;
    }
    case STRUCTREF_COPY_IN: {
      Var structRef = getOutput(0);
      Var val = getInput(0).getVar();

      List<Var> inputs = Arrays.asList(structRef, val);
      List<Boolean> acquireWrite = Arrays.asList(true, false);
      List<MakeImmVar> vs = mkImmVarListAcquire(waitForClose,
          closedVars, true, false, inputs, acquireWrite);

      if (vs.size() > 0) {
        return new MakeImmRequest(MakeImmVar.NONE, vs);
      }
      break;
    }
    case STRUCTREF_STORE_SUB: {
      Var structRef = getOutput(0);
      List<MakeImmVar> vs = mkImmVarList(waitForClose, closedVars, true,
                  false, true, structRef.asList());
      if (waitForClose || closedVars.contains(structRef)) {
        return new MakeImmRequest(MakeImmVar.NONE, vs);
      }
      break;
    }
    case STRUCT_COPY_OUT: {
      // If struct is closed or this field already set, don't needto block
      Var struct = getInput(0).getVar();
      if (closedVars.contains(struct)) {
        // Don't request to wait for close - whole struct doesn't need to be
        // closed
        return MakeImmRequest.fromVars(Var.NONE, Var.NONE);
      }
      break;
    }
    case STRUCTREF_COPY_OUT: {
      Var structRef = getInput(0).getVar();
      if (waitForClose || closedVars.contains(structRef)) {
        return MakeImmRequest.fromVars(Var.NONE, structRef);
      }
      break;
    }
    case ARR_COPY_IN_IMM: {
      // See if we can get deref arg
      Var mem = getInput(1).getVar();
      List<MakeImmVar> vs = mkImmVarList(waitForClose, closedVars,
          true, false, false, mem);
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case ARR_STORE_FUTURE:
    case ARR_COPY_IN_FUTURE: {
      Var ix = getInput(0).getVar();
      Arg val = getInput(1);
      List<Var> candVs;
      if (op == Opcode.ARR_STORE_FUTURE) {
        candVs = ix.asList();
      } else {
        assert(op == Opcode.ARR_COPY_IN_FUTURE);
        candVs = Arrays.asList(ix, val.getVar());
      }
      List<MakeImmVar> vs = mkImmVarList(waitForClose, closedVars,
                                        true, false, false, candVs);
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case AREF_STORE_IMM:
    case AREF_COPY_IN_IMM: {
      List<Var> candVs;
      List<Boolean> candAcquireWrite;
      Var arrRef = getOutput(0);
      Arg mem = getInput(1);
      if (op == Opcode.AREF_STORE_IMM) {
        candVs = arrRef.asList();
        candAcquireWrite = Collections.singletonList(true);
      } else {
        assert(op == Opcode.AREF_COPY_IN_IMM);
        candVs = Arrays.asList(arrRef, mem.getVar());
        candAcquireWrite = Arrays.asList(true, false);
      }
      List<MakeImmVar> vs = mkImmVarListAcquire(waitForClose, closedVars,
                                 true, false, candVs, candAcquireWrite);

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
      List<Var> candVs;
      List<Boolean> candAcquireWrite;
      if (op == Opcode.AREF_STORE_FUTURE) {
        candVs = Arrays.asList(arrRef, ix);
        candAcquireWrite = Arrays.asList(true, false);
      } else {
        assert(op == Opcode.AREF_COPY_IN_FUTURE);
        candVs = Arrays.asList(arrRef, ix, mem.getVar());
        candAcquireWrite = Arrays.asList(true, false, false);
      }
      // We will take either the index or the dereferenced array

      List<MakeImmVar> vs = mkImmVarListAcquire(waitForClose, closedVars,
                                 true, false, candVs, candAcquireWrite);
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case ARR_CREATE_NESTED_FUTURE: {
      // Try to get immediate index
      Var ix = getInput(0).getVar();
      if (waitForClose || closedVars.contains(ix)) {
        return MakeImmRequest.fromVars(Var.NONE, Arrays.asList(ix));
      }
      break;
    }
    case AREF_CREATE_NESTED_IMM: {
      Var arrRef = getOutput(1);
      List<MakeImmVar> vs = mkImmVarList(waitForClose, closedVars,
                  true, false, true, arrRef.asList());
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case AREF_CREATE_NESTED_FUTURE: {
      Var arrRef = getOutput(1);
      Var ix = getInput(0).getVar();

      List<Var> candVs;
      List<Boolean> candAcquireWrite;
      candVs = Arrays.asList(arrRef, ix);
      candAcquireWrite = Arrays.asList(true, false);

      List<MakeImmVar> vs = mkImmVarListAcquire(waitForClose, closedVars,
                                 true, false, candVs, candAcquireWrite);
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case ASYNC_COPY: {
      // See if we can get closed container/struct
      List<MakeImmVar> req = mkImmVarList(waitForClose, closedVars,
                                   false, false, false, getInput(0).getVar());
      if (req.size() > 0) {
        // Wait for vars only
        return new MakeImmRequest(null, req);
      }
      break;
    }
    case SYNC_COPY: {
      // TODO: would be nice to switch to  explicit load/store if container
      //       datum is small enough
      Var dst = getOutput(0);
      Var src = getInput(0).getVar();
      if (Types.isPrimFuture(dst) || Types.isStruct(dst) ||
          Types.isRef(dst)) {
        // Small data
        if (waitForClose || closedVars.contains(src)) {
          return MakeImmRequest.fromVars(Var.NONE, src);
        }
      }
      break;
    }
    case COPY_IN_FILENAME: {
      Var filenameIn = getInput(0).getVar();
      if (waitForClose || closedVars.contains(filenameIn)) {
        return MakeImmRequest.fromVars(Var.NONE, filenameIn);
      }
      break;
    }
    case UPDATE_INCR:
    case UPDATE_MIN:
    case UPDATE_SCALE: {
      Var newVal = getInput(0).getVar();
      if (waitForClose || closedVars.contains(newVal)) {
        return MakeImmRequest.fromVars(Var.NONE, newVal);
      }
      break;
    }
    default:
      // fall through
      break;
    }
    return null;
  }

  private List<MakeImmVar> mkImmVarListAcquire(boolean waitForClose,
      Set<Var> closedVars, boolean fetch,
      boolean recursive, List<Var> inputs, List<Boolean> acquireWrite) {
    List<MakeImmVar> vs = new ArrayList<MakeImmVar>();

    assert(inputs.size() == acquireWrite.size());
    for (int i = 0; i < inputs.size(); i++) {
      MakeImmVar check = checkMkImmInput(waitForClose, closedVars,
                      fetch, recursive, acquireWrite.get(i), inputs.get(i));
      if (check != null) {
        vs.add(check);
      }
    }
    return vs;
  }

  private List<MakeImmVar> mkImmVarList(boolean waitForClose,
      Set<Var> closedVars, boolean fetch,
      boolean recursive, boolean acquireWriteRefs,
      Var... args) {
    return mkImmVarList(waitForClose, closedVars,
        fetch, recursive, acquireWriteRefs, Arrays.asList(args));

  }
  private List<MakeImmVar> mkImmVarList(boolean waitForClose,
      Set<Var> closedVars, boolean fetch, boolean recursive,
      boolean acquireWriteRefs, List<Var> args) {
    ArrayList<MakeImmVar> req = new ArrayList<MakeImmVar>(args.size());
    for (Var v: args) {
      MakeImmVar in;
      in = checkMkImmInput(waitForClose, closedVars, fetch, recursive,
          acquireWriteRefs, v);
      if (in != null) {
        req.add(in);
      }
    }
    return req;
  }

  private MakeImmVar checkMkImmInput(boolean waitForClose, Set<Var> closedVars,
      boolean fetch, boolean recursive, boolean acquireWriteRefs, Var v) {
    MakeImmVar in;
    if (waitForClose || closedVars.contains(v)) {
      in = MakeImmVar.in(v, fetch, recursive, acquireWriteRefs);
    } else {
      in = null;
    }
    return in;
  }

  @Override
  public MakeImmChange makeImmediate(VarCreator creator,
                                     List<Fetched<Var>> out,
                                     List<Fetched<Arg>> values) {
    switch (op) {
    case ARR_COPY_OUT_IMM: {
      assert(values.size() == 0) : values;
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
      Instruction newI = structRetrieveSub(valOut, arr, fields, Arg.ZERO);
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
      assert(Types.isNonLocalRef(oldResult, true));
      Var newOut = creator.createDerefTmp(oldResult);
      return new MakeImmChange(newOut, oldResult,
          arrayCreateNestedImm(newOut, oldArray, ix));
    }
    case AREF_CREATE_NESTED_FUTURE: {
      assert(values.size() == 1 || values.size() == 2);
      Var result = getOutput(0);
      Var arrRef = getOutput(1);
      Var ix = getInput(0).getVar();

      Var newArr = Fetched.findFetchedVar(values, arrRef);
      Arg newIx = Fetched.findFetched(values, ix);

      if (newArr != null && newIx != null) {
        Var oldOut = getOutput(0);
        assert(Types.isArrayRef(oldOut));
        Var newOut = creator.createDerefTmp(result);
        return new MakeImmChange(newOut, oldOut,
            arrayCreateNestedImm(newOut, newArr, newIx));
      } else if (newArr != null && newIx == null) {
        return new MakeImmChange(
            arrayCreateNestedFuture(result, newArr, ix));
      } else {
        assert(newArr == null && newIx != null);
        return new MakeImmChange(
            arrayRefCreateNestedImmIx(result, arrRef, newIx));
      }
    }
    case AREF_CREATE_NESTED_IMM: {
      assert(values.size() == 1);
      Var result = values.get(0).fetched.getVar();
      Arg ix = getInput(0);
      Var arrResult = getOutput(0);
      assert(Types.isArray(result));
      assert(Types.isNonLocalRef(arrResult, true));
      Var newOut3 = creator.createDerefTmp(arrResult);
      assert(Types.isArrayKeyVal(result, ix));
      return new MakeImmChange(newOut3, arrResult,
          arrayCreateNestedImm(newOut3, result, getInput(0)));
    }
    case ASYNC_COPY: {
      // data is closed: replace with sync version
      return new MakeImmChange(
          syncCopy(getOutput(0), getInput(0).getVar()));
    }
    case SYNC_COPY: {
      assert(values.get(0).original.equals(getInput(0).getVar()));
      Arg val = values.get(0).fetched;
      return new MakeImmChange(
          TurbineOp.storeAny(getOutput(0), val, false));
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

  @Override
  public List<Pair<Var, Instruction.InitType>> getInitialized() {
    switch (op) {
      case LOAD_REF:
      case COPY_REF:
      case ARR_CREATE_NESTED_IMM:
      case STRUCT_CREATE_NESTED:
      case GET_FILENAME_ALIAS:
        // Initialises alias
        return Arrays.asList(Pair.create(getOutput(0), InitType.FULL));

      case ARR_RETRIEVE:
      case ARR_CREATE_ALIAS:
      case STRUCT_RETRIEVE_SUB:
      case STRUCT_CREATE_ALIAS:{
        // May initialise alias if we're looking up a reference
        Var output = getOutput(0);
        if (output.storage() == Alloc.ALIAS) {
          return Arrays.asList(Pair.create(output, InitType.FULL));
        } else {
          return Collections.emptyList();
        }
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
  @Override
  public List<Var> getReadOutputs(Map<String, Function> fns) {
    switch (op) {
    case STRUCT_CREATE_NESTED:
    case ARR_CREATE_NESTED_IMM:
    case ARR_CREATE_NESTED_FUTURE:
      // In create_nested instructions the
      // outer datum being inserted into is needed
      return Arrays.asList(getOutput(1));
    case AREF_CREATE_NESTED_IMM:
    case AREF_CREATE_NESTED_FUTURE:
      // In ref_create_nested instructions the
      // outer array being inserted into is needed
      return Arrays.asList(getOutput(1));
      default:
        return Var.NONE;
    }
  }

  @Override
  public List<Var> getModifiedOutputs() {
    switch (op) {
    case ARR_CREATE_NESTED_IMM:
    case STRUCT_CREATE_NESTED:
    case ARR_CREATE_NESTED_FUTURE:
    case AREF_CREATE_NESTED_IMM:
    case AREF_CREATE_NESTED_FUTURE:
      // In create_nested instructions only the
      // first output (the created datum) is needed
      return Collections.singletonList(getOutput(0));
    default:
        return this.getOutputs();
    }
  }

  @Override
  public List<Component> getModifiedComponents() {
    switch (op) {
      case AREF_COPY_IN_FUTURE:
      case AREF_COPY_IN_IMM:
      case AREF_STORE_FUTURE:
      case AREF_STORE_IMM:
      case STRUCTREF_COPY_IN:
      case STRUCTREF_STORE_SUB:
        // This functions modify the datum referred to by the output reference
        return new Component(getOutput(0), Component.DEREF).asList();
      default:
         return null;
    }
  }

  /**
   * @return List of outputs that are piecewise assigned
   */
  @Override
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
      case STRUCT_CREATE_NESTED:
      case AREF_CREATE_NESTED_FUTURE:
      case AREF_CREATE_NESTED_IMM: {
        // All outputs except the newly created datum;
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
        if (setFilename.isBool() && setFilename.getBool()) {
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
    if (!execMode().isAsync()) {
      return Var.NONE;
    }

    switch (op) {
      case ASYNC_COPY:
        // Async copy is special because it copies the entire structure
        return ICUtil.extractVars(inputs);
      default:
        // Fall through;
        break;
    }

    // If async, assume that all scalar input vars are blocked on
    ArrayList<Var> blocksOn = new ArrayList<Var>();
    for (Arg oa: getInputs()) {
      if (oa.kind == ArgKind.VAR) {
        Var v = oa.getVar();
        if (Types.isPrimFuture(v) || Types.isRef(v)) {
          blocksOn.add(v);
        } else if (Types.isPrimValue(v) || Types.isStruct(v) ||
            Types.isContainer(v) || Types.isPrimUpdateable(v)) {
          // Not all turbine ops block on these types
        } else {
          throw new STCRuntimeError("Don't handle type "
                              + v.type().toString() + " here");
        }
      }
    }
    return blocksOn;
  }


  @Override
  public ExecTarget execMode() {
    switch (op) {
    case STORE_SCALAR:
    case STORE_FILE:
    case STORE_ARRAY:
    case STORE_BAG:
    case STORE_STRUCT:
    case STORE_ARRAY_RECURSIVE:
    case STORE_STRUCT_RECURSIVE:
    case STORE_BAG_RECURSIVE:
    case LOAD_SCALAR:
    case LOAD_FILE:
    case LOAD_ARRAY:
    case LOAD_BAG:
    case LOAD_STRUCT:
    case LOAD_ARRAY_RECURSIVE:
    case LOAD_STRUCT_RECURSIVE:
    case LOAD_BAG_RECURSIVE:
    case UPDATE_INCR:
    case UPDATE_MIN:
    case UPDATE_SCALE:
    case UPDATE_INCR_IMM:
    case UPDATE_MIN_IMM:
    case UPDATE_SCALE_IMM:
    case INIT_UPDATEABLE_FLOAT:
    case LATEST_VALUE:
    case ARR_STORE:
    case STRUCT_STORE_SUB:
    case STRUCT_RETRIEVE_SUB:
    case STRUCT_CREATE_ALIAS:
    case ARR_CREATE_ALIAS:
    case ARR_CREATE_NESTED_IMM:
    case STRUCT_CREATE_NESTED:
    case STORE_REF:
    case LOAD_REF:
    case FREE_BLOB:
    case DECR_LOCAL_FILE_REF:
    case GET_FILENAME_ALIAS:
    case GET_LOCAL_FILENAME:
    case IS_MAPPED:
    case ARR_RETRIEVE:
    case COPY_REF:
    case CHOOSE_TMP_FILENAME:
    case GET_FILENAME_VAL:
    case SET_FILENAME_VAL:
    case INIT_LOCAL_OUTPUT_FILE:
    case ARRAY_BUILD:
    case SYNC_COPY:
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
    case STRUCT_LOCAL_BUILD:
      return ExecTarget.syncAny();

    case COPY_FILE_CONTENTS:
      // Should run on worker in case large file
      return ExecTarget.sync(ExecContext.defaultWorker());

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
    case ASYNC_COPY:
    case STRUCT_COPY_IN:
    case STRUCTREF_STORE_SUB:
    case STRUCTREF_COPY_IN:
    case STRUCT_COPY_OUT:
    case STRUCTREF_COPY_OUT:
    case COPY_IN_FILENAME:
      return ExecTarget.nonDispatchedAny();
    default:
      throw new STCRuntimeError("Need to add opcode " + op.toString()
          + " to getMode");
    }
  }

  @Override
  public boolean isCheap() {
    switch (op) {
      case LOAD_ARRAY:
      case LOAD_BAG:
      case LOAD_FILE:
      case LOAD_ARRAY_RECURSIVE:
      case LOAD_STRUCT_RECURSIVE:
      case LOAD_BAG_RECURSIVE:
      case LOAD_REF:
      case LOAD_SCALAR:
      case LOAD_STRUCT:
      case LATEST_VALUE:
      case STORE_ARRAY:
      case STORE_BAG:
      case STORE_FILE:
      case STORE_ARRAY_RECURSIVE:
      case STORE_STRUCT_RECURSIVE:
      case STORE_BAG_RECURSIVE:
      case STORE_REF:
      case STORE_SCALAR:
      case STORE_STRUCT:
      case ARRAY_BUILD:
        // Loads and stores aren't too expensive
        return true;

      case SET_FILENAME_VAL:
      case GET_FILENAME_VAL:
      case COPY_IN_FILENAME:
        // Filename loads and stores aren't expensive
        return true;

      case INIT_LOCAL_OUTPUT_FILE:
      case INIT_UPDATEABLE_FLOAT:
        // Init operations
        return true;

      case UPDATE_INCR:
      case UPDATE_INCR_IMM:
      case UPDATE_MIN:
      case UPDATE_MIN_IMM:
      case UPDATE_SCALE:
      case UPDATE_SCALE_IMM:
        // Update operations
        return true;

      case COPY_REF:
      case STRUCT_CREATE_ALIAS:
      case GET_FILENAME_ALIAS:
      case ARR_CREATE_ALIAS:
        // Creating alias is cheap
        return true;

      case DEREF_FILE:
      case DEREF_SCALAR:
        // Spawning deref is cheap
        return true;

      case ARR_CONTAINS:
      case ARR_COPY_IN_FUTURE:
      case ARR_COPY_IN_IMM:
      case ARR_COPY_OUT_FUTURE:
      case ARR_COPY_OUT_IMM:
      case ARR_CREATE_NESTED_FUTURE:
      case ARR_CREATE_NESTED_IMM:
      case STRUCT_CREATE_NESTED:
      case ARR_LOCAL_CONTAINS:
      case ARR_RETRIEVE:
      case ARR_STORE:
      case ARR_STORE_FUTURE:
      case AREF_COPY_IN_FUTURE:
      case AREF_COPY_IN_IMM:
      case AREF_COPY_OUT_FUTURE:
      case AREF_COPY_OUT_IMM:
      case AREF_CREATE_NESTED_FUTURE:
      case AREF_CREATE_NESTED_IMM:
      case AREF_STORE_FUTURE:
      case AREF_STORE_IMM:
      case BAG_INSERT:
      case CONTAINER_SIZE:
      case CONTAINER_LOCAL_SIZE:
        // Container operations aren't too expensive
        return true;

      case STRUCT_COPY_IN:
      case STRUCT_COPY_OUT:
      case STRUCT_RETRIEVE_SUB:
      case STRUCT_STORE_SUB:
      case STRUCTREF_COPY_IN:
      case STRUCTREF_COPY_OUT:
      case STRUCTREF_STORE_SUB:
        // Struct operations aren't too expensive
        return true;

      case ASYNC_COPY:
        // Spawning the task isn't expensive
        return true;

      case SYNC_COPY:
        // Copying a large container may be expensive
        return false;

      case CHECKPOINT_LOOKUP_ENABLED:
      case CHECKPOINT_WRITE_ENABLED:
      case CHOOSE_TMP_FILENAME:
      case IS_MAPPED:
      case GET_LOCAL_FILENAME:
        // Utility operations are cheap
        return true;

      case COPY_FILE_CONTENTS:
        // Copying a file can take some time
        return false;

      case DECR_LOCAL_FILE_REF:
      case FREE_BLOB:
        // Local refcount manipulations
        return true;

      case LOOKUP_CHECKPOINT:
        return true;

      case PACK_VALUES:
      case UNPACK_ARRAY_TO_FLAT:
      case UNPACK_VALUES:
      case STRUCT_LOCAL_BUILD:
        return true;

      case WRITE_CHECKPOINT:
        // May require I/O
        return false;

      default:
        throw new STCRuntimeError("Missing: " + op);
    }
  }

  @Override
  public boolean isProgressEnabling() {
    switch (op) {
      case LOAD_ARRAY:
      case LOAD_BAG:
      case LOAD_FILE:
      case LOAD_ARRAY_RECURSIVE:
      case LOAD_STRUCT_RECURSIVE:
      case LOAD_BAG_RECURSIVE:
      case LOAD_REF:
      case LOAD_SCALAR:
      case LOAD_STRUCT:
      case LATEST_VALUE:
      case GET_FILENAME_VAL:
        // Loads don't do anything
        return false;

      case STORE_ARRAY:
      case STORE_BAG:
      case STORE_FILE:
      case STORE_ARRAY_RECURSIVE:
      case STORE_STRUCT_RECURSIVE:
      case STORE_BAG_RECURSIVE:
      case STORE_REF:
      case STORE_SCALAR:
      case STORE_STRUCT:
      case ARRAY_BUILD:
      case SET_FILENAME_VAL:
      case COPY_IN_FILENAME:
        // Stores can enable progress
        return true;

      case INIT_LOCAL_OUTPUT_FILE:
      case INIT_UPDATEABLE_FLOAT:
        // Init operations don't enable progress
        return false;

      case UPDATE_INCR:
      case UPDATE_INCR_IMM:
      case UPDATE_MIN:
      case UPDATE_MIN_IMM:
      case UPDATE_SCALE:
      case UPDATE_SCALE_IMM:
        // Don't want to defer updates
        return true;

      case COPY_REF:
      case STRUCT_CREATE_ALIAS:
      case GET_FILENAME_ALIAS:
      case ARR_CREATE_ALIAS:
        // Creating alias doesn't make progress
        return false;

      case DEREF_FILE:
      case DEREF_SCALAR:
        // Deref assigns future
        return true;


      case ARR_COPY_IN_FUTURE:
      case ARR_COPY_IN_IMM:
      case ARR_STORE:
      case ARR_STORE_FUTURE:
      case AREF_COPY_IN_FUTURE:
      case AREF_COPY_IN_IMM:
      case AREF_STORE_FUTURE:
      case AREF_STORE_IMM:
      case BAG_INSERT:
        // Adding to container can enable progress
        return true;

      case ARR_CREATE_NESTED_FUTURE:
      case ARR_CREATE_NESTED_IMM:
      case AREF_CREATE_NESTED_FUTURE:
      case AREF_CREATE_NESTED_IMM:
      case STRUCT_CREATE_NESTED:
        // Creating nested datums can release write refcount on outer
        return true;

      case ARR_CONTAINS:
      case ARR_LOCAL_CONTAINS:
      case ARR_RETRIEVE:
      case CONTAINER_SIZE:
      case CONTAINER_LOCAL_SIZE:
        // Retrieving from array won't enable progress
        return false;

      case ARR_COPY_OUT_FUTURE:
      case ARR_COPY_OUT_IMM:
      case AREF_COPY_OUT_FUTURE:
      case AREF_COPY_OUT_IMM:
        // Copying to future can enable progress
        return true;

      case STRUCT_COPY_IN:
      case STRUCT_STORE_SUB:
      case STRUCTREF_COPY_IN:
      case STRUCTREF_STORE_SUB:
        // Adding to struct can enable progress
        return true;

      case STRUCT_RETRIEVE_SUB:
        // Retrieving from struct won't enable progress
        return false;

      case STRUCT_COPY_OUT:
      case STRUCTREF_COPY_OUT:
        // Copying to future can enable progress
        return true;

      case ASYNC_COPY:
        // Copying can enable progress
        return true;

      case SYNC_COPY:
        // Copying can enable progress
        return true;

      case CHECKPOINT_LOOKUP_ENABLED:
      case CHECKPOINT_WRITE_ENABLED:
      case CHOOSE_TMP_FILENAME:
      case IS_MAPPED:
      case GET_LOCAL_FILENAME:
        // Utility operations don't enable progress
        return true;

      case COPY_FILE_CONTENTS:
        // Copying a file doesn't assign future
        return false;

      case DECR_LOCAL_FILE_REF:
      case FREE_BLOB:
        // Local refcount manipulations
        return false;

      case LOOKUP_CHECKPOINT:
        return false;

      case PACK_VALUES:
      case UNPACK_ARRAY_TO_FLAT:
      case UNPACK_VALUES:
      case STRUCT_LOCAL_BUILD:
        return false;

      case WRITE_CHECKPOINT:
        // Don't defer writing checkpoint
        return true;

      default:
        throw new STCRuntimeError("Missing: " + op);
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
      case LOAD_ARRAY_RECURSIVE:
      case LOAD_STRUCT_RECURSIVE:
      case LOAD_BAG_RECURSIVE: {
        Arg src = getInput(0);
        Var dst = getOutput(0);

        if (op == Opcode.LOAD_REF) {
          // use standard deref value
          return ValLoc.derefCompVal(dst, src.getVar(), IsValCopy.NO,
                                     IsAssign.NO).asList();
        } else {
          return ValLoc.retrieve(dst, src.getVar(), op.isRecursiveRetrieve(),
                      Closed.MAYBE_NOT, IsValCopy.NO, IsAssign.TO_LOCATION).asList();
        }
      }
      case STORE_REF:
      case STORE_SCALAR:
      case STORE_FILE:
      case STORE_ARRAY:
      case STORE_BAG:
      case STORE_STRUCT:
      case STORE_ARRAY_RECURSIVE:
      case STORE_STRUCT_RECURSIVE:
      case STORE_BAG_RECURSIVE: {
        // add retrieve so we can avoid retrieving later
        Var dst = getOutput(0);
        Arg src = getInput(0);


        // add assign so we can avoid recreating future
        // (closed b/c this instruction closes val immediately)
        ValLoc assign;
        if (op == Opcode.STORE_FILE) {
          assign = ValLoc.assignFile(dst, src, getInput(1),
                                     IsAssign.TO_LOCATION);
        } else {
          assign = ValLoc.assign(dst, src, (op.isRecursiveAssign()),
            (op.isRecursiveAssign()) ? Closed.YES_RECURSIVE : Closed.YES_NOT_RECURSIVE,
            IsValCopy.NO, IsAssign.TO_LOCATION);
        }

        if (op == Opcode.STORE_STRUCT) {
          /*
           * Avoid invalid optimization in situations where we're only storing
           * a subset of fields.
           */
          assert(Types.isStruct(dst));
          StructType st = (StructType)dst.type().getImplType();
          if (st.hasRefField()) {
            return Collections.emptyList();
          }
        }

        if (op == Opcode.STORE_REF) {
          // TODO: incorporate mutability here?
          // Use standard dereference computed value
          ValLoc retrieve = ValLoc.derefCompVal(src.getVar(), dst,
                                   IsValCopy.NO, IsAssign.TO_LOCATION);
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
          Arg result = Arg.newBool(fileVar.isMapped() == Ternary.TRUE);
          return Arrays.asList(vanilla,
                ValLoc.makeCopy(getOutput(0), result, IsAssign.NO));
        }
      }
      case GET_FILENAME_ALIAS: {
        Arg filename = getOutput(0).asArg();
        Var file = getInput(0).getVar();
        return ValLoc.makeFilename(filename, file, IsAssign.NO).asList();
      }
      case COPY_IN_FILENAME: {
        Arg filename = getInput(0);
        Var file = getOutput(0);
        return ValLoc.makeFilename(filename, file, IsAssign.NO).asList();
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
        return ValLoc.makeFilenameVal(file, val.asArg(),
                           IsAssign.TO_LOCATION).asList();
      }
      case DEREF_SCALAR:
      case DEREF_FILE: {
        return ValLoc.derefCompVal(getOutput(0), getInput(0).getVar(),
                             IsValCopy.YES, IsAssign.TO_LOCATION).asList();
      }

      case STRUCT_CREATE_ALIAS:
      case STRUCT_COPY_OUT:
      case STRUCTREF_COPY_OUT: {
        // Ops that lookup field in struct somehow
        Var struct = getInput(0).getVar();
        List<Arg> fields = getInputsTail(1);

        if (op == Opcode.STRUCT_CREATE_ALIAS) {
          // Create values to repr both alias and value
          ValLoc copyV = ValLoc.makeStructFieldCopyResult(getOutput(0),
              struct, fields, IsAssign.NO);
          ValLoc aliasV = ValLoc.makeStructFieldAliasResult(getOutput(0),
                                struct, fields);
          return Arrays.asList(aliasV, copyV);
        } else {
          ValLoc copyV = ValLoc.makeStructFieldCopyResult(getOutput(0),
              struct, fields, IsAssign.TO_LOCATION);
          // Not an alias - copy val only
          return copyV.asList();
        }
      }
      case STRUCT_RETRIEVE_SUB: {
        Var struct = getInput(0).getVar();
        List<Arg> fields = getInputsTail(2);
        return ValLoc.makeStructFieldValResult(getOutput(0).asArg(),
                              struct, fields, IsAssign.NO).asList();
      }
      case STRUCT_STORE_SUB:
      case STRUCTREF_STORE_SUB: {
        Var struct = getOutput(0);
        Arg val = getInput(0);
        List<Arg> fields;
        if (op == Opcode.STRUCT_STORE_SUB) {
          fields = getInputsTail(1);
        } else {
          assert(op == Opcode.STRUCTREF_STORE_SUB);
          fields = getInputsTail(1);
        }
        return ValLoc.makeStructFieldValResult(val, struct, fields,
                                        IsAssign.TO_VALUE).asList();
      }
      case STRUCT_COPY_IN:
      case STRUCTREF_COPY_IN: {
        Var struct = getOutput(0);
        Var val = getInput(0).getVar();
        List<Arg> fields = getInputsTail(1);
        return ValLoc.makeStructFieldCopyResult(val, struct, fields,
                                        IsAssign.TO_VALUE).asList();
      }
      case STRUCT_CREATE_NESTED: {
        Var nested = getOutput(0);
        Var struct = getOutput(1);
        List<Arg> fields = getInputsTail(STRUCT_NESTED_FIELDS_START);

        ValLoc copyV = ValLoc.makeStructFieldValResult(nested.asArg(),
                          struct, fields, IsAssign.TO_LOCATION);
        ValLoc nestedV = ValLoc.makeStructCreateNestedResult(nested, struct, fields);
        return Arrays.asList(copyV, nestedV);
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
                       Closed.YES_NOT_RECURSIVE, IsAssign.TO_LOCATION));
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
                    Arg.newInt(elemCount), false, IsAssign.NO));
        return res;
      }
      case ARR_CREATE_ALIAS:
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
        } else if (op == Opcode.ARR_CREATE_ALIAS) {
          ValLoc memVal = ValLoc.makeArrayResult(arr, ix, contents.asArg(),
                                                false, IsAssign.NO);
          ValLoc memAlias = ValLoc.makeArrayAlias(arr, ix, contents.asArg(),
                                                IsAssign.NO);
          return Arrays.asList(memVal, memAlias);
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
      case AREF_CREATE_NESTED_IMM: {
        // CREATE_NESTED <out inner datum> <in array> <in index>
        Var nested = getOutput(0);
        Var arr = getOutput(1);
        Arg ix = getInput(0);
        List<ValLoc> res = new ArrayList<ValLoc>();

        boolean returnsNonRef = op == Opcode.ARR_CREATE_NESTED_IMM;
        // Mark as not substitutable since this op may have
        // side-effect of creating array
        res.add(ValLoc.makeArrayResult(arr, ix, nested.asArg(),
                                              returnsNonRef, IsAssign.NO));
        res.add(ValLoc.makeCreateNestedResult(arr, ix, nested,
                                              returnsNonRef));
        return res;
      }
      case SYNC_COPY:
      case ASYNC_COPY: {
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
                   i, getInput(0).asList(), out.asArg(),
                   Closed.YES_RECURSIVE, IsValCopy.NO, IsAssign.TO_LOCATION));
        }
        return res;
      }
      case PACK_VALUES:
        return vanillaResult(Closed.YES_NOT_RECURSIVE,
                             IsAssign.TO_LOCATION).asList();
      case CHECKPOINT_LOOKUP_ENABLED:
      case CHECKPOINT_WRITE_ENABLED:
        return vanillaResult(Closed.YES_NOT_RECURSIVE,
                             IsAssign.TO_LOCATION).asList();
      case UNPACK_ARRAY_TO_FLAT:
        return vanillaResult(Closed.YES_NOT_RECURSIVE,
                             IsAssign.TO_LOCATION).asList();
      case ARR_CONTAINS:
      case CONTAINER_SIZE:
      case ARR_LOCAL_CONTAINS:
      case CONTAINER_LOCAL_SIZE:
        return vanillaResult(Closed.YES_NOT_RECURSIVE,
                             IsAssign.TO_LOCATION).asList();
      case STRUCT_LOCAL_BUILD:
        // Worth optimising?
        return null;
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
    if (op == Opcode.ARRAY_BUILD || op == Opcode.SYNC_COPY ||
        op == Opcode.SYNC_COPY) {
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
        long readRefsIn = getInput(1).getInt();
        long writeRefsIn = getInput(2).getInt();
        Var src = getInput(0).getVar();
        return Pair.create(new VarCount(src, readRefsIn).asList(),
                           new VarCount(src, writeRefsIn).asList());
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
      case ASYNC_COPY:
      case SYNC_COPY: {
        // Need to pass in refcount for var to be copied, and write
        // refcount for assigned var if write refcounti tracked
        List<VarCount> writeRefs;
        if (RefCounting.trackWriteRefCount(getOutput(0))) {
          writeRefs = VarCount.one(getOutput(0)).asList();
        } else {
          writeRefs = VarCount.NONE;
        }
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           writeRefs);
      }
      case STORE_BAG:
      case STORE_ARRAY:
      case STORE_ARRAY_RECURSIVE:
      case STORE_BAG_RECURSIVE: {
        // Inputs stored into array need to have refcount incremented
        // This finalizes array so will consume refcount
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           VarCount.one(getOutput(0)).asList());
      }
      case STORE_STRUCT: {
        // Inputs stored into array need to have refcount incremented
        // Does not write any tracked elements of struct, so do not need to
        // manage write refcount.
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           VarCount.NONE);
      }
      case STORE_STRUCT_RECURSIVE: {
        // Inputs stored into array need to have refcount incremented
        // Does write any tracked elements of struct, so do need to
        // manage write refcount.
        long writeCount = RefCounting.baseWriteRefCount(getOutput(0), true, false);
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                      new VarCount(getOutput(0), writeCount).asList());
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
      case ARR_RETRIEVE: {
        VarCount readDecr = new VarCount(getInput(0).getVar(),
                                         getInput(2).getInt());
        return Pair.create(readDecr.asList(), VarCount.NONE);
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
      case ARR_CREATE_NESTED_IMM:{
        long readDecr = getInput(3).getInt();
        long writeDecr = getInput(4).getInt();
        Var arr = getOutput(1);
        return Pair.create(new VarCount(arr, readDecr).asList(),
                           new VarCount(arr, writeDecr).asList());
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
      case STRUCTREF_COPY_OUT:
      case STRUCT_COPY_OUT: {
        // Array only
        return Pair.create(VarCount.one(getInput(0).getVar()).asList(),
                           VarCount.NONE);
      }
      case STRUCT_STORE_SUB:
      case STRUCTREF_STORE_SUB:
      case STRUCT_COPY_IN:
      case STRUCTREF_COPY_IN:{
        Var struct = getOutput(0);
        Arg mem = getInput(0);
        List<VarCount> readers = VarCount.NONE;
        if (mem.isVar() && RefCounting.trackReadRefCount(mem.getVar())) {
          readers = VarCount.one(mem.getVar()).asList();
        }

        Pair<Long, Long> fieldRefCounts =
            RefCounting.baseStructFieldWriteRefCount(struct,
                      Arg.extractStrings(getInputsTail(1)),
                      getOutput(0).defType());

        // Will consume tracked refcounts
        Long trackedFieldRefCounts = fieldRefCounts.val1;
        return Pair.create(readers,
              new VarCount(struct, trackedFieldRefCounts).asList());
      }
      case STRUCT_CREATE_NESTED:{
        long readDecr = getInput(2).getInt();
        long writeDecr = getInput(3).getInt();
        Var struct = getOutput(1);
        return Pair.create(new VarCount(struct, readDecr).asList(),
                           new VarCount(struct, writeDecr).asList());
      }
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
        long readRefs = getInput(1).getInt();
        long writeRefs = getInput(2).getInt();
        return Pair.create(new VarCount(v, readRefs).asList(),
                           new VarCount(v, writeRefs).asList());
      }
      case STRUCT_RETRIEVE_SUB: {
        // Gives back a read refcount to the result if relevant
        return Pair.create(VarCount.one(getOutput(0)).asList(),
                           VarCount.NONE);
      }
      case ARR_RETRIEVE: {
        // Gives back a refcount to the result if relevant
        long acquireRead = getInput(3).getInt();
        return Pair.create(new VarCount(getOutput(0), acquireRead).asList(),
                           VarCount.NONE);
      }
      case ARR_CREATE_NESTED_IMM: {
        long readIncr = getInput(1).getInt();
        long writeIncr = getInput(2).getInt();
        Var result = getOutput(0);
        return Pair.create(new VarCount(result, readIncr).asList(),
                           new VarCount(result, writeIncr).asList());
      }
      case STRUCT_CREATE_NESTED: {
        long readIncr = getInput(0).getInt();
        long writeIncr = getInput(1).getInt();
        Var result = getOutput(0);
        return Pair.create(new VarCount(result, readIncr).asList(),
                           new VarCount(result, writeIncr).asList());
      }
        // TODO: other array/struct retrieval funcs
      default:
        return Pair.create(VarCount.NONE, VarCount.NONE);
    }
  }

  @Override
  public VarCount tryPiggyback(RefCountsToPlace increments, RefCountType type) {
    switch (op) {
      case LOAD_SCALAR:
      case LOAD_FILE:
      case LOAD_ARRAY:
      case LOAD_BAG:
      case LOAD_STRUCT:
      case LOAD_ARRAY_RECURSIVE:
      case LOAD_STRUCT_RECURSIVE:
      case LOAD_BAG_RECURSIVE: {
        Var inVar = getInput(0).getVar();
        if (type == RefCountType.READERS) {
          long amt = increments.getCount(inVar);
          if (amt < 0) {
            assert(getInputs().size() == 1);
            // Add extra arg
            this.inputs = Arrays.asList(getInput(0),
                                      Arg.newInt(amt * -1));
            return new VarCount(inVar, amt);
          }
        }
        break;
      }
      case LOAD_REF: {
        assert(inputs.size() == 4);
        VarCount success = piggyBackDecrHelper(increments, type,
                  getInput(0).getVar(), 3, -1);
        if (success != null) {
          return success;
        }

        success = piggybackCancelIncr(increments, getOutput(0), type,
                                      1, 2);
        if (success != null) {
          return success;
        }
        return null;
      }
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
            Arg decrArg = Arg.newInt(amt * -1 + defaultDecr);
            this.inputs = Arrays.asList(getInput(0), getInput(1), decrArg);
            return new VarCount(arr, amt);
          }
        }
        break;
      }
      case ARR_RETRIEVE: {
        Var arr = getInput(0).getVar();
        assert(getInputs().size() == 4);
        VarCount success = piggyBackDecrHelper(increments, type, arr, 2, -1);
        if (success != null) {
          return success;
        }

        success = piggybackCancelIncr(increments, getOutput(0), type,
                                      3, -1);
        if (success != null) {
          return success;
        }
        break;
      }
      case ARR_CREATE_NESTED_IMM:
      case STRUCT_CREATE_NESTED: {
        // Piggyback decrements on outer datum
        Var res = getOutput(0);
        Var outer = getOutput(1);

        // Input index for first refcount arg
        int rcStartInput;
        if (op == Opcode.ARR_CREATE_NESTED_IMM) {
          assert(getInputs().size() == 5);
          rcStartInput = 1;
        } else {
          assert(op == Opcode.STRUCT_CREATE_NESTED);
          rcStartInput = 0;
        }
        int readAcqIx = rcStartInput;
        int writeAcqIx = rcStartInput + 1;
        int readDecrIx = rcStartInput + 2;
        int writeDecrIx = rcStartInput + 3;

        // piggyback decrements here
        VarCount success = piggyBackDecrHelper(increments, type, outer,
                                              readDecrIx, writeDecrIx);
        if (success != null) {
          return success;
        }

        success = piggybackCancelIncr(increments, res, type,
                                      readAcqIx, writeAcqIx);
        if (success != null) {
          return success;
        }

        // Instruction can give additional refcounts back
        long resIncr = increments.getCount(res);
        if (resIncr > 0) {
          int pos = (type == RefCountType.READERS) ? readAcqIx : writeAcqIx;
          Arg currIncr = getInput(pos);
          if (currIncr.isInt()) {
            inputs.set(pos, Arg.newInt(currIncr.getInt() + resIncr));
            return new VarCount(res, resIncr);
          }
        }
        return null;
      }
      case BAG_INSERT: {
        Var bag = getOutput(0);
        return piggyBackDecrHelper(increments, type, bag, -1, 1);
      }

      case STRUCT_RETRIEVE_SUB:
        return piggyBackDecrHelper(increments, type, getInput(0).getVar(),
                                  1, -1);
      default:
        // Do nothing
    }

    // Fall through to here if can do nothing
    return null;
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
  private VarCount piggyBackDecrHelper(RefCountsToPlace increments,
      RefCountType type, Var var, int readDecrInput, int writeDecrInput) {
    long amt = increments.getCount(var);
    if (amt < 0) {
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
        return null;
      }
      assert(inputPos < inputs.size());

      Arg oldAmt = getInput(inputPos);
      if (oldAmt.isInt()) {
        setInput(inputPos, Arg.newInt(oldAmt.getInt() - amt));
        return new VarCount(var, amt);
      }
    }
    return null;
  }

  private VarCount piggybackCancelIncr(RefCountsToPlace increments, Var var,
      RefCountType type, int readIncrInput, int writeIncrInput) {
    long decrAmt = increments.getCount(var);
    if (decrAmt < 0) {
      int pos = (type == RefCountType.READERS) ?
                readIncrInput : writeIncrInput;
      if (pos < 0) {
        return null;
      }
      long currVal = getInput(pos).getInt();
      assert(currVal >= 0);
      long updatedVal;
      long piggybackedAmt;
      if (currVal + decrAmt >= 0) {
        updatedVal = currVal + decrAmt;
        piggybackedAmt = decrAmt;
      } else {
        // Can't piggyback all
        updatedVal = 0;
        piggybackedAmt = -currVal;
      }

      inputs.set(pos, Arg.newInt(updatedVal));
      if (piggybackedAmt != 0) {
        assert(piggybackedAmt < 0);
        return new VarCount(var, piggybackedAmt);
      }
    }
    return null;
  }

  @Override
  public List<Alias> getAliases() {
    switch (this.op) {
      case ARR_CREATE_ALIAS:
        return Alias.makeArrayAlias(getInput(0).getVar(), getInput(1),
            getOutput(0), AliasTransform.IDENTITY);
      case STRUCT_CREATE_ALIAS:
        return Alias.makeStructAliases2(getInput(0).getVar(), getInputsTail(1),
            getOutput(0), AliasTransform.IDENTITY);
      case STRUCT_RETRIEVE_SUB:
        return Alias.makeStructAliases2(getInput(0).getVar(), getInputsTail(2),
            getOutput(0), AliasTransform.RETRIEVE);
      case STRUCT_STORE_SUB:
        if (getInput(0).isVar()) {
          return Alias.makeStructAliases2(getOutput(0), getInputsTail(1),
                                  getInput(0).getVar(), AliasTransform.RETRIEVE);
        }
        break;
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
      case GET_FILENAME_ALIAS: {
        return new Alias(getInput(0).getVar(), Alias.FILENAME_PATH,
                         AliasTransform.IDENTITY, getOutput(0)).asList();
      }
      default:
        // Opcode not relevant
        break;
    }
    return Alias.NONE;
  }

  @Override
  public List<ComponentAlias> getComponentAliases() {
    switch (op) {
      case ARR_CREATE_NESTED_IMM:
      case STRUCT_CREATE_NESTED:
        // From inner object to immediately enclosing
        return new ComponentAlias(getOutput(1), Component.deref(getInput(0).asList()),
                   getOutput(0)).asList();
      case ARR_CREATE_NESTED_FUTURE: {
        // From inner object to immediately enclosing
        return new ComponentAlias(getOutput(1), getInput(0).asList(),
                                  getOutput(0)).asList();
      }
      case AREF_CREATE_NESTED_IMM:
      case AREF_CREATE_NESTED_FUTURE: {
        List<Arg> key = Arrays.asList(Component.DEREF, getInput(0));
        // From inner object to immediately enclosing
        return new ComponentAlias(getOutput(1), key, getOutput(0)).asList();
      }
      case AREF_STORE_FUTURE:
      case AREF_STORE_IMM:
      case ARR_STORE:
      case ARR_STORE_FUTURE: {
        Var arr = getOutput(0);

        if (Types.isRef(Types.arrayKeyType(arr))) {
          Arg ix = getInput(0);
          List<Arg> key;
          if (Types.isArrayRef(arr)) {
            // Mark extra dereference
            key = Arrays.asList(Component.DEREF, ix, Component.DEREF);
          } else {
            key = Arrays.asList(ix, Component.DEREF);
          }

          return new ComponentAlias(arr, key, getInput(1).getVar()).asList();
        }
        break;
      }
      case ARR_COPY_IN_FUTURE:
      case ARR_COPY_IN_IMM:
      case AREF_COPY_IN_FUTURE:
      case AREF_COPY_IN_IMM: {
        Var arr = getOutput(0);

        if (Types.isRef(Types.arrayKeyType(arr))) {
          Arg ix = getInput(0);
          List<Arg> key;
          if (Types.isArrayRef(arr)) {
            // Mark extra dereference
            key = Arrays.asList(Component.DEREF, ix);
          } else {
            key = ix.asList();
          }

          return new ComponentAlias(arr, key, getInput(1).getVar()).asList();
        }
        break;
      }
      case ARR_CREATE_ALIAS: {
        return new ComponentAlias(getInput(0).getVar(), getInput(1),
                                  getOutput(0)).asList();
      }
      case ARR_COPY_OUT_FUTURE:
      case ARR_COPY_OUT_IMM:
      case AREF_COPY_OUT_FUTURE:
      case AREF_COPY_OUT_IMM: {
        Var arr = getInput(0).getVar();

        if (Types.isRef(Types.arrayKeyType(arr))) {
          Arg ix = getInput(1);
          List<Arg> key;
          if (Types.isArrayRef(arr)) {
            // Mark extra dereference
            key = Arrays.asList(Component.DEREF, ix);
          } else {
            key = ix.asList();
          }
          return new ComponentAlias(arr, key, getOutput(0)).asList();
        }
        break;
      }
      case LOAD_REF:
        // If reference was a part of something, modifying the
        // dereferenced object will modify the whole
        return ComponentAlias.ref(getOutput(0), getInput(0).getVar()).asList();
      case COPY_REF:
        return ComponentAlias.directAlias(getOutput(0), getInput(0).getVar()).asList();
      case STORE_REF:
        // Sometimes a reference is filled in
        return ComponentAlias.ref(getInput(0).getVar(), getOutput(0)).asList();
      case STRUCT_CREATE_ALIAS: {
        // Output is alias for part of struct
        List<Arg> fields = getInputsTail(1);
        return new ComponentAlias(getInput(0).getVar(), fields,
                                  getOutput(0)).asList();
      }
      case STRUCTREF_STORE_SUB:
      case STRUCT_STORE_SUB:
        if (Alias.fieldIsRef(getOutput(0),
                             Arg.extractStrings(getInputsTail(1)))) {
          List<Arg> fields = getInputsTail(1);
          if (op == Opcode.STRUCTREF_STORE_SUB) {
            // Mark extra dereference
            fields = new ArrayList<Arg>(fields);
            fields.add(0, Component.DEREF);
          }
          return new ComponentAlias(getOutput(0), Component.deref(fields),
                                getInput(0).getVar()).asList();
        }
        break;
      case STRUCT_RETRIEVE_SUB:
        if (Alias.fieldIsRef(getInput(0).getVar(),
                             Arg.extractStrings(getInputsTail(2)))) {
          List<Arg> fields = getInputsTail(1);
          return new ComponentAlias(getInput(0).getVar(), Component.deref(fields),
                                getOutput(0)).asList();
        }
        break;
      case STRUCTREF_COPY_IN:
      case STRUCT_COPY_IN:
        if (Alias.fieldIsRef(getOutput(0),
                             Arg.extractStrings(getInputsTail(1)))) {
          List<Arg> fields = getInputsTail(1);
          if (op == Opcode.STRUCTREF_COPY_IN) {
            // Mark extra dereference
            fields = new ArrayList<Arg>(fields);
            fields.add(0, Component.DEREF);
          }
          return new ComponentAlias(getOutput(0),
                                    fields, getInput(0).getVar()).asList();
        }
        break;
      case STRUCTREF_COPY_OUT:
      case STRUCT_COPY_OUT:
        if (Alias.fieldIsRef(getInput(0).getVar(),
                             Arg.extractStrings(getInputsTail(1)))) {
          List<Arg> fields = getInputsTail(1);
          return new ComponentAlias(getInput(0).getVar(),
                  fields, getOutput(0)).asList();
        }
        break;
      default:
        // Return nothing
        break;
    }
    return Collections.emptyList();
  }

  @Override
  public boolean isIdempotent() {
    switch (op) {
      case ARR_CREATE_NESTED_FUTURE:
      case ARR_CREATE_NESTED_IMM:
      case AREF_CREATE_NESTED_FUTURE:
      case AREF_CREATE_NESTED_IMM:
      case STRUCT_CREATE_NESTED:
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
      // TODO: batching for reference counts?
      gen.modifyRefCounts(Collections.singletonList(new DirRefCount(
                    getRCTarget(this), getRCType(this.op),
                    getRefcountDir(this.op), getRCAmount(this))));
    }

    @Override
    public ExecTarget execMode() {
      // Executes right away
      return ExecTarget.syncAny();
    }

    @Override
    public boolean isCheap() {
      return true;
    }

    @Override
    public boolean isProgressEnabling() {
      // Decrementing write refcount can close
      return getRCType(op) == RefCountType.WRITERS;
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