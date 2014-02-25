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
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.Counters;
import exm.stc.common.util.Pair;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.ICUtil;
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
      gen.assignFile(getOutput(0), getInput(0));
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
      gen.arrayRefStoreImm(getOutput(0),
          getOutput(1), getInput(0), getInput(1));
      break;
    case AREF_STORE_FUTURE:
      gen.arrayRefStoreFuture(getOutput(0),
          getOutput(1), getInput(0).getVar(), getInput(1));
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
      gen.arrayRefCopyInImm(getOutput(0),
          getOutput(1), getInput(0), getInput(1).getVar());
      break;
    case AREF_COPY_IN_FUTURE:
      gen.arrayRefCopyInFuture(getOutput(0),
          getOutput(1), getInput(0).getVar(), getInput(1).getVar());
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
    case STRUCT_RETRIEVE:
      gen.structRetrieve(getOutput(0), getInput(0).getVar(),
                         Arg.extractStrings(getInputsTail(1)));
      break;
    case STRUCT_COPY_OUT:
      gen.structCopyOut(getOutput(0), getInput(0).getVar(),
                         Arg.extractStrings(getInputsTail(1)));
      break;
    case STRUCTREF_COPY_OUT:
      gen.structRefLookup(getOutput(0), getInput(0).getVar(),
                          Arg.extractStrings(getInputsTail(1)));
      break;
    case STRUCT_STORE:
      gen.structStore(getOutput(0), Arg.extractStrings(getInputsTail(1)),
                                    getInput(0));
      break;
    case STRUCT_COPY_IN:
      gen.structCopyIn(getOutput(0), Arg.extractStrings(getInputsTail(1)),
                       getInput(0).getVar());
      break;
    case STRUCTREF_STORE:
      gen.structRefStore(getOutput(0), Arg.extractStrings(getInputsTail(1)),
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
      gen.retrieveRef(getOutput(0), getInput(0).getVar(),
            getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case COPY_REF:
      gen.makeAlias(getOutput(0), getInput(0).getVar());
      break;
    case ARR_CREATE_NESTED_FUTURE:
      gen.arrayCreateNestedFuture(getOutput(0), getOutput(1), 
                                  getInput(0).getVar());
      break;
    case AREF_CREATE_NESTED_FUTURE:
      gen.arrayRefCreateNestedFuture(getOutput(0), getOutput(1), getOutput(2),
                                     getInput(0).getVar());
      break;
    case AREF_CREATE_NESTED_IMM:
      gen.arrayRefCreateNestedImm(getOutput(0), getOutput(1), getOutput(2),
                                  getInput(0));
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
    case GET_FILENAME:
      gen.getFileName(getOutput(0), getInput(0).getVar());
      break;
    case GET_LOCAL_FILENAME:
      gen.getLocalFileName(getOutput(0), getInput(0).getVar());
      break;
    case IS_MAPPED:
      gen.isMapped(getOutput(0), getInput(0).getVar());
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

  public static Instruction arrayRetrieve(Var oVar, Var arrayVar,
                                            Arg arrayIndex) {
    return new TurbineOp(Opcode.ARR_RETRIEVE,
        oVar, arrayVar.asArg(), arrayIndex);
  }

  public static Instruction arrayRefCopyOutImm(Var oVar,
      Var arrayVar, Arg arrayIndex) {
    return new TurbineOp(Opcode.AREF_COPY_OUT_IMM,
        oVar, arrayVar.asArg(), arrayIndex);
  }

  public static Instruction arrayCopyOutImm(Var oVar, Var arrayVar,
      Arg arrayIndex) {
    return new TurbineOp(Opcode.ARR_COPY_OUT_IMM,
        oVar, arrayVar.asArg(), arrayIndex);
  }

  public static TurbineOp arrayCopyOutFuture(Var oVar, Var arrayVar,
      Var indexVar) {
    return new TurbineOp(Opcode.ARR_COPY_OUT_FUTURE,
        oVar, arrayVar.asArg(), indexVar.asArg());
  }

  public static TurbineOp arrayRefCopyOutFuture(Var oVar, Var arrayRefVar,
      Var indexVar) {
    return new TurbineOp(Opcode.AREF_COPY_OUT_FUTURE, oVar,
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
      array + " " + member + " " + member.type();
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

  public static Instruction arrayRefStoreImm(Var outerArray,
      Var array, Arg ix, Arg member) {
    assert(Types.isArrayRef(array));
    assert(Types.isArrayKeyVal(array, ix));
    assert(Types.isElemValType(array, member));
    return new TurbineOp(Opcode.AREF_STORE_IMM,
        Arrays.asList(outerArray, array),
        ix, member);
  }

  public static Instruction arrayRefStoreFuture(Var outerArray,
      Var array, Var ix, Arg member) {
    assert(Types.isArrayRef(array));
    assert(Types.isArrayKeyFuture(array, ix));
    assert(Types.isElemValType(array, member));
    return new TurbineOp(Opcode.AREF_STORE_FUTURE,
        Arrays.asList(outerArray, array), ix.asArg(), member);
  }

  public static Instruction arrayCopyInImm(Var array,
      Arg ix, Var member) {
    return new TurbineOp(Opcode.ARR_COPY_IN_IMM,
                         array, ix, member.asArg());
  }

  public static Instruction arrayCopyInFuture(Var array, Var ix, Var member) {
    return new TurbineOp(Opcode.ARR_COPY_IN_FUTURE, array, ix.asArg(),
                          member.asArg());
  }

  public static Instruction arrayRefCopyInImm(Var outerArray,
      Var array, Arg ix, Var member) {
    return new TurbineOp(Opcode.AREF_COPY_IN_IMM,
        Arrays.asList(outerArray, array),
        ix, member.asArg());
  }
  
  public static Instruction arrayRefCopyInFuture(Var outerArray,
      Var array, Var ix, Var member) {
    return new TurbineOp(Opcode.AREF_COPY_IN_FUTURE,
        Arrays.asList(outerArray, array),
        ix.asArg(), member.asArg());
  }

  public static Instruction arrayBuild(Var array, List<Arg> keys, List<Arg> vals) {
    int elemCount = keys.size();
    assert(vals.size() == elemCount);
    
    ArrayList<Arg> inputs = new ArrayList<Arg>(elemCount * 2);
    for (int i = 0; i < elemCount; i++) {
      inputs.add(keys.get(i));
      inputs.add(vals.get(i));
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

  public static Instruction bagInsert(Var bag, Arg elem, Arg writersDecr) {
    assert(Types.isElemValType(bag, elem));
    assert(writersDecr.isImmediateInt());
    return new TurbineOp(Opcode.BAG_INSERT, bag, elem, writersDecr);
  }

  public static Instruction structRetrieve(Var oVar, Var structVar,
                                           List<String> fields) {
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);
    
    in.add(structVar.asArg());
    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    return new TurbineOp(Opcode.STRUCT_RETRIEVE, oVar.asList(), in);
  }

  public static Instruction structCreateAlias(Var oVar, Var structVar,
                                              List<String> fields) {
    assert(oVar.storage() == Alloc.ALIAS) : oVar;
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);
    
    in.add(structVar.asArg());
    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    return new TurbineOp(Opcode.STRUCT_CREATE_ALIAS, oVar.asList(), in);
  }
  
  public static Instruction structCopyOut(Var oVar, Var structVar,
                                          List<String> fields) {
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);
    
    in.add(structVar.asArg());
    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    return new TurbineOp(Opcode.STRUCT_COPY_OUT, oVar.asList(), in);
  }

  public static Instruction structRefCopyOut(Var oVar, Var structVar,
                                          List<String> fields) {
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    in.add(structVar.asArg());
    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    return new TurbineOp(Opcode.STRUCTREF_COPY_OUT, oVar.asList(), in);
  }

  public static Instruction structStore(Var structVar,
        List<String> fields, Arg fieldVal) {
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    in.add(fieldVal);
    return new TurbineOp(Opcode.STRUCT_STORE, structVar.asList(), in);
  }
  
  public static Instruction structCopyIn(Var structVar,
      List<String> fields, Var fieldVar) {
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    in.add(fieldVar.asArg());
    return new TurbineOp(Opcode.STRUCT_COPY_IN, structVar.asList(), in);
  }
  
  public static Instruction structRefStore(Var structVar,
      List<String> fields, Arg fieldVal) {
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    in.add(fieldVal);
    return new TurbineOp(Opcode.STRUCTREF_STORE, structVar.asList(), in);
  }
  
  public static Instruction structRefCopyIn(Var structVar,
                        List<String> fields, Var fieldVar) {
    List<Arg> in = new ArrayList<Arg>(fields.size() + 1);

    for (String field: fields) {
      in.add(Arg.createStringLit(field));
    }
    in.add(fieldVar.asArg());
    return new TurbineOp(Opcode.STRUCTREF_COPY_IN, structVar.asList(), in);
  }
  
  public static Instruction assignScalar(Var target, Arg src) {
    return new TurbineOp(Opcode.STORE_SCALAR, target, src);
  }

  public static Instruction assignFile(Var target, Arg src) {
    return new TurbineOp(Opcode.STORE_FILE, target, src);
  }
  
  public static Instruction assignArray(Var target, Arg src) {
    return new TurbineOp(Opcode.STORE_ARRAY, target, src);
  }
  
  public static Instruction assignBag(Var target, Arg src) {
    return new TurbineOp(Opcode.STORE_BAG, target, src);
  }

  public static Instruction retrieveScalar(Var target, Var source) {
    return new TurbineOp(Opcode.LOAD_SCALAR, target, source.asArg());
  }
  
  public static Instruction retrieveFile(Var target, Var source) {
    return new TurbineOp(Opcode.LOAD_FILE, target, source.asArg());
  }
  
  public static Instruction retrieveArray(Var target, Var source) {
    return new TurbineOp(Opcode.LOAD_ARRAY, target, source.asArg());
  }
  
  public static Instruction retrieveBag(Var target, Var source) {
    return new TurbineOp(Opcode.LOAD_BAG, target, source.asArg());
  }
  
  public static Instruction storeRecursive(Var target, Arg src) {
    return new TurbineOp(Opcode.STORE_RECURSIVE, target, src);
  }
  
  public static Instruction retrieveRecursive(Var target, Var src) {
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

  public static Instruction storeRef(Var target, Var src) {
    return new TurbineOp(Opcode.STORE_REF, target, src.asArg());
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
      return assignFile(dst, src);
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
  
  public static Instruction retrieveRef(Var target, Var src) {
    return new TurbineOp(Opcode.LOAD_REF, target, src.asArg());
  }
  
  public static Instruction copyRef(Var dst, Var src) {
    return new TurbineOp(Opcode.COPY_REF, dst, src.asArg());
  }

  public static Instruction arrayCreateNestedFuture(Var arrayResult,
      Var array, Var ix) {
    assert(Types.isArrayRef(arrayResult.type()));
    assert(arrayResult.storage() != Alloc.ALIAS);
    assert(Types.isArray(array.type()));
    assert(Types.isArrayKeyFuture(array, ix));
    // Both arrays are modified, so outputs
    return new TurbineOp(Opcode.ARR_CREATE_NESTED_FUTURE,
        Arrays.asList(arrayResult, array), ix.asArg());
  }

  public static Instruction arrayCreateNestedImm(Var arrayResult,
      Var arrayVar, Arg arrIx) {
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(arrayResult.storage() == Alloc.ALIAS);
    // Both arrays are modified, so outputs
    return new TurbineOp(Opcode.ARR_CREATE_NESTED_IMM,
        Arrays.asList(arrayResult, arrayVar),
        arrIx, Arg.ZERO, Arg.ZERO);
  }

  public static Instruction arrayRefCreateNestedComputed(Var arrayResult,
      Var outerArr, Var array, Var ix) {
    assert(Types.isArrayRef(arrayResult.type())): arrayResult;
    assert(arrayResult.storage() != Alloc.ALIAS);
    assert(Types.isArrayRef(array.type())): array;
    assert(Types.isArray(outerArr.type())): outerArr;
    assert(Types.isArrayKeyFuture(array, ix));
    // Returns nested array, modifies outer array and
    // reference counts outmost array
    return new TurbineOp(Opcode.AREF_CREATE_NESTED_FUTURE,
        Arrays.asList(arrayResult, outerArr, array),
        ix.asArg());
  }

  public static Instruction arrayRefCreateNestedImmIx(Var arrayResult,
      Var outerArray, Var array, Arg ix) {
    assert(Types.isArrayRef(arrayResult.type())): arrayResult;
    assert(arrayResult.storage() != Alloc.ALIAS);
    assert(Types.isArrayRef(array.type())): array;
    assert(Types.isArray(outerArray.type())): outerArray;
    assert(Types.isArrayKeyVal(array, ix));
    return new TurbineOp(Opcode.AREF_CREATE_NESTED_IMM,
        // Returns nested array, modifies outer array and
        // reference counts outmost array
        Arrays.asList(arrayResult, outerArray, array),
        ix);
  }
  

  public static Instruction arrayCreateBag(Var bag,
      Var array, Arg arrIx) {
    assert(Types.isArrayKeyVal(array, arrIx));
    assert(bag.storage() == Alloc.ALIAS);
    // Both arrays are modified, so outputs
    return new TurbineOp(Opcode.ARRAY_CREATE_BAG,
        Arrays.asList(bag, array),
        arrIx, Arg.ZERO, Arg.ZERO);
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
  
  public static Instruction getFileName(Var filename, Var file) {
    return new TurbineOp(Opcode.GET_FILENAME, filename, file.asArg());
  }
  
  public static Instruction getLocalFileName(Var filename, Var file) {
    assert(Types.isFileVal(file));
    assert(Types.isStringVal(filename));
    return new TurbineOp(Opcode.GET_LOCAL_FILENAME, filename, file.asArg());
  }

  public static Instruction setFilenameVal(Var file, Arg filenameVal) {
    return new TurbineOp(Opcode.SET_FILENAME_VAL, file, filenameVal);
  }

  public static Instruction copyFileContents(Var target, Var src) {
    return new TurbineOp(Opcode.COPY_FILE_CONTENTS, target, src.asArg());
  }
  
  public static Instruction isMapped(Var isMapped, Var filename) {
    assert(Types.isBoolVal(isMapped));
    assert(Types.isFile(filename));
    return new TurbineOp(Opcode.IS_MAPPED, isMapped, filename.asArg());
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
    case STRUCT_STORE:
    case STRUCT_COPY_IN:
    case STRUCTREF_STORE:
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
    case STORE_RECURSIVE:
    case DEREF_SCALAR:
    case DEREF_FILE:
    case LOAD_SCALAR:
    case LOAD_FILE:
    case LOAD_ARRAY:
    case LOAD_BAG:
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

    case GET_FILENAME:
      // Only effect is setting alias var
      return false;
    case GET_LOCAL_FILENAME:
      return false;
    case IS_MAPPED:
      // will always returns same result for same var
      return false;
    case CHOOSE_TMP_FILENAME:
      // Non-deterministic
      return true;
    case SET_FILENAME_VAL:
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
      
    case STRUCT_LOOKUP:
    case LOAD_REF:
    case STORE_REF:
    case COPY_REF:
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
      Set<Var> valueAvail, boolean waitForClose) {
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
      Var innerArrRef = getOutput(1);
      Arg mem = getInput(1);
      if (op == Opcode.AREF_STORE_IMM) {
        vs = innerArrRef.asList();
      } else {
        assert(op == Opcode.AREF_COPY_IN_IMM);
        vs = Arrays.asList(innerArrRef, mem.getVar());
      }
      vs = mkImmVarList(insertRefWaitForClose, closedVars, vs);
      
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case AREF_STORE_FUTURE: 
    case AREF_COPY_IN_FUTURE: {
      Var innerArrRef = getOutput(1);
      Var ix = getInput(0).getVar();
      Arg mem = getInput(1);
      List<Var> req;
      if (op == Opcode.AREF_STORE_FUTURE) {
        req = Arrays.asList(innerArrRef, ix);
      } else {
        assert(op == Opcode.AREF_COPY_IN_FUTURE);
        req = Arrays.asList(innerArrRef, ix, mem.getVar());
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
      Var arrRef = getOutput(2);
      if (waitForClose || closedVars.contains(arrRef)) {
        return new MakeImmRequest(null, Arrays.asList(arrRef));
      }
      break;
    }
    case AREF_CREATE_NESTED_FUTURE: {
      Var arrRef = getOutput(2);
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
    case UPDATE_INCR:
    case UPDATE_MIN:
    case UPDATE_SCALE:
      return new MakeImmRequest(null, Arrays.asList(
                getInput(0).getVar()));
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
      // TODO: update to reflect that ref is not internal repr
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
    case STRUCTREF_COPY_OUT: {
      assert(values.size() == 1);
      // OUtput switched from ref to value
      Var newStruct = values.get(0).fetched.getVar();
      assert(Types.isRefTo(getInput(0).getVar(), newStruct));
      Var refOut = getOutput(0);
      Var valOut = creator.createDerefTmp(refOut);
      String field = getInput(1).getStringLit();
      Instruction newI = structLookup(valOut, newStruct, field);
      return new MakeImmChange(valOut, refOut, newI);
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
      Var outerArrRef = getOutput(0);
      Var innerArrRef = getOutput(1);
      Arg ix = getInput(0);
      Var mem = getInput(1).getVar();
      Var newArr = Fetched.findFetchedVar(values, innerArrRef);
      Arg newMem = Fetched.findFetched(values, mem);
      Instruction newI;
      if (newArr != null && newMem != null) {
        newI = arrayStore(newArr, ix, newMem);
      } else if (newArr != null && newMem == null) {
        newI = arrayCopyInImm(newArr, ix, mem);
      } else {
        assert(newArr == null && newMem != null);
        newI = arrayRefStoreImm(outerArrRef, innerArrRef, ix, newMem);
      }
      
      return new MakeImmChange(newI);
    }
    case AREF_STORE_FUTURE:
    case AREF_COPY_IN_FUTURE: {
      Var outerArr = getOutput(0);
      Var arrRef = getOutput(1);
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
          inst = arrayRefStoreImm(outerArr, arrRef, newIx, derefMem);
        } else {
          assert(newArr == null && newIx == null);
          inst = arrayRefStoreFuture(outerArr, arrRef, ix, derefMem);
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
          inst = arrayRefCopyInImm(outerArr, arrRef, newIx, memVar);
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
      Var outerArr = getOutput(1);
      Var arrRef = getOutput(2);
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
            arrayRefCreateNestedImmIx(arrResult, outerArr, arrRef, newIx));
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
    case SYNC_COPY_CONTAINER: {
      // TODO: would be nice to switch to array_store if we already have
      //       loaded array value
      break;
    }
    case ASYNC_COPY_STRUCT: {
      // Array is closed: replace with sync version
      return new MakeImmChange(
          syncCopyStruct(getOutput(0), getInput(0).getVar()));
    }
    case SYNC_COPY_STRUCT: {
      // TODO: would be nice to switch to directly store if we already
      // have value
      break;
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
      case GET_FILENAME:
        // Initialises alias
        return Arrays.asList(Pair.create(getOutput(0), InitType.FULL));

      case ARR_RETRIEVE:
      case STRUCT_LOOKUP: {
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
      return Arrays.asList(getOutput(2));
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

    case AREF_STORE_FUTURE:
    case AREF_STORE_IMM:
      // In the arrayref_insert instructions, the first output
      // is a reference to an outer array that is kept open but not
      // modified
      return Collections.singletonList(getOutput(1));
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
      case STRUCT_STORE:
      case STRUCT_COPY_IN:
      case STRUCTREF_STORE:
      case STRUCTREF_COPY_IN:
        return getOutputs();
      case SET_FILENAME_VAL:
        // File's filename might be modified
        return Collections.singletonList(getOutput(0));
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
    case STORE_RECURSIVE:
    case LOAD_SCALAR:
    case LOAD_FILE:
    case LOAD_ARRAY:
    case LOAD_BAG:
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
    case STRUCT_STORE:
    case STRUCT_LOOKUP:
    case ARR_CREATE_NESTED_IMM:
    case ARRAY_CREATE_BAG:
    case STORE_REF:
    case LOAD_REF:
    case FREE_BLOB:
    case DECR_LOCAL_FILE_REF:
    case GET_FILENAME:
    case GET_LOCAL_FILENAME:
    case IS_MAPPED:
    case COPY_FILE_CONTENTS:
    case ARR_RETRIEVE:
    case COPY_REF:
    case CHOOSE_TMP_FILENAME:
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
    case STRUCTREF_COPY_OUT:
    case ARR_COPY_OUT_FUTURE:
    case AREF_CREATE_NESTED_FUTURE:
    case ARR_CREATE_NESTED_FUTURE:
    case AREF_CREATE_NESTED_IMM:
    case ASYNC_COPY_CONTAINER:
    case ASYNC_COPY_STRUCT:
    case STRUCT_COPY_IN:
    case STRUCTREF_STORE:
    case STRUCTREF_COPY_IN:
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
      case GET_FILENAME: {
        Arg filename = getOutput(0).asArg();
        Var file = getInput(0).getVar();
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
      case DEREF_SCALAR: 
      case DEREF_FILE: {
        return ValLoc.derefCompVal(getOutput(0), getInput(0).getVar(),
                                   IsValCopy.YES, IsAssign.NO).asList();
      }

      case STRUCT_STORE:
      case STRUCT_COPY_IN:
      case STRUCTREF_STORE:
      case STRUCTREF_COPY_IN:{
        throw new STCRuntimeError("TODO");
        /*ValLoc lookup = ValLoc.makeStructLookupResult(
            getInput(1).getVar(), getOutput(0), getInput(0).getStringLit());
        return lookup.asList();*/ 
      }
      case STRUCT_LOOKUP: {
        ValLoc lookup = ValLoc.makeStructLookupResult(
            getOutput(0), getInput(0).getVar(), getInput(1).getStringLit());
        return lookup.asList(); 
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
        // STORE  <in outer array> <out array> <in index> <in var>
        Var arr;
        if (op == Opcode.AREF_STORE_FUTURE ||
            op == Opcode.AREF_STORE_IMM ||
            op == Opcode.AREF_COPY_IN_FUTURE ||
            op == Opcode.AREF_COPY_IN_IMM) {
          arr = getOutput(1);
        } else {
          arr = getOutput(0);
        }
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
        // CREATE_NESTED <out inner array> <outer arr> <in array> <in index>
        // OR
        // CREATE_BAG <out inner bag> <in array> <in index> 
        Var nestedArr = getOutput(0);
        Var arr;
        if (op == Opcode.AREF_CREATE_NESTED_FUTURE ||
            op == Opcode.AREF_CREATE_NESTED_IMM) {
          arr = getOutput(2);
        } else {
          arr = getOutput(1);
        }
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
  public Pair<List<Var>, List<Var>> getIncrVars() {
    switch (op) {
      case STORE_REF:
        return Pair.create(getInput(0).getVar().asList(), Var.NONE);
      case ARRAY_BUILD: {
        List<Var> readIncr = new ArrayList<Var>(getInputs().size() / 2);
        for (int i = 0; i < getInputs().size() / 2; i++) {
          // Skip keys and only get values
          Arg elem = getInput(i * 2 + 1);
          // Container gets reference to member
          if (elem.isVar() && RefCounting.hasReadRefCount(elem.getVar())) {
            readIncr.add(elem.getVar());
          }
        }
        Var arr = getOutput(0);
        return Pair.create(readIncr, Arrays.asList(arr));
      }
      case ASYNC_COPY_CONTAINER:
      case SYNC_COPY_CONTAINER:
      case ASYNC_COPY_STRUCT:
      case SYNC_COPY_STRUCT:
        // Need to pass in refcount for var to be copied, and write
        // refcount for assigned var
        return Pair.create(getInput(0).getVar().asList(),
                           getOutput(0).asList());
      case STORE_BAG:
      case STORE_ARRAY: 
      case STORE_RECURSIVE: { 
        // Inputs stored into array need to have refcount incremented
        // This finalizes array so will consume refcount
        return Pair.create(getInput(0).getVar().asList(),
                            getOutput(0).asList());
      }
      case DEREF_SCALAR:
      case DEREF_FILE: {
        // Increment refcount of ref var
        return Pair.create(Arrays.asList(getInput(0).getVar()),
                           Var.NONE);
      }
      case AREF_COPY_OUT_FUTURE:
      case ARR_COPY_OUT_FUTURE: {
        // Array and index
        return Pair.create(
                Arrays.asList(getInput(0).getVar(), getInput(1).getVar()),
                Var.NONE);
      }
      case AREF_COPY_OUT_IMM:
      case ARR_COPY_OUT_IMM: {
        // Array only
        return Pair.create(
                  Arrays.asList(getInput(0).getVar()),
                  Var.NONE);
      }
      case ARR_CONTAINS:
      case CONTAINER_SIZE: {
        // Executes immediately, doesn't need refcount
        return Pair.create(Var.NONE, Var.NONE);
      }
      case ARR_STORE: {
        Arg mem = getInput(1);
        // Increment reference to memberif needed
        List<Var> readIncr;
        if (mem.isVar() && RefCounting.hasReadRefCount(mem.getVar())) {
          readIncr = mem.getVar().asList(); 
        } else {
          readIncr = Var.NONE;
        }
        return Pair.create(readIncr, Var.NONE);
      }
      case ARR_COPY_IN_IMM: {
        // Increment reference to member ref
        // Increment writers count on array
        Var mem = getInput(1).getVar();
        return Pair.create(Arrays.asList(mem),
                           Arrays.asList(getOutput(0)));
      }
      case ARR_STORE_FUTURE: 
      case ARR_COPY_IN_FUTURE: {
        // Increment reference to member/member ref and index future
        // Increment writers count on array
        Var arr = getInput(0).getVar();
        Arg mem = getInput(1);

        List<Var> readIncr;
        if (mem.isVar() && RefCounting.hasReadRefCount(mem.getVar())) {
          readIncr = Arrays.asList(arr, mem.getVar());
        } else {
          readIncr = arr.asList();
        }
        return Pair.create(readIncr, Arrays.asList(getOutput(0)));
      }
      case AREF_STORE_IMM:
      case AREF_COPY_IN_IMM:
      case AREF_STORE_FUTURE: 
      case AREF_COPY_IN_FUTURE: {
        Arg ix = getInput(0);
        Arg mem = getInput(1);
        Var outerArr = getOutput(0);
        Var arrayRef = getOutput(1);
        List<Var> readers = new ArrayList<Var>(3);
        readers.add(arrayRef);
        if (mem.isVar() && RefCounting.hasReadRefCount(mem.getVar())) {
          readers.add(mem.getVar());
        }

        if (op == Opcode.AREF_STORE_FUTURE ||
            op == Opcode.AREF_COPY_IN_FUTURE) {
          readers.add(ix.getVar());
        } else {
          assert(op == Opcode.AREF_STORE_IMM ||
                 op == Opcode.AREF_COPY_IN_IMM);
        }
        // Maintain slots on outer array
        return Pair.create(readers,
                Arrays.asList(outerArr));
      }
      case ARR_CREATE_NESTED_FUTURE: {
        Var srcArray = getOutput(1);
        Var ix = getInput(0).getVar();
        return Pair.create(ix.asList(), srcArray.asList());
      }
      case STRUCTREF_COPY_OUT: {
        return Pair.create(Arrays.asList(getInput(0).getVar()),
                           Var.NONE);
      }
      case AREF_CREATE_NESTED_IMM:
      case AREF_CREATE_NESTED_FUTURE: {
        Var outerArr = getOutput(1);
        assert(Types.isArray(outerArr.type())): outerArr + " " + this;
        assert(Types.isArrayRef(outerArr.type().memberType())) : outerArr;
        Var arr = getOutput(2);
        Arg ixArg = getInput(0);
        List<Var> readVars;
        if (op == Opcode.AREF_CREATE_NESTED_IMM) {
          readVars = Arrays.asList(arr);
        } else {
          assert(op == Opcode.AREF_CREATE_NESTED_FUTURE);
          readVars = Arrays.asList(arr, ixArg.getVar());
        }
        return Pair.create(readVars,
                Arrays.asList(outerArr));
      }
      case BAG_INSERT: {
        Arg mem = getInput(0);
        List<Var> readers = Var.NONE;
        if (mem.isVar() && RefCounting.hasReadRefCount(mem.getVar())) {
          readers = mem.getVar().asList();
        }
        return Pair.create(readers, Var.NONE);
      }
      case STRUCT_STORE:
      case STRUCT_COPY_IN:
      case STRUCTREF_STORE:
      case STRUCTREF_COPY_IN:
        // Do nothing: reference count tracker can track variables
        // across struct boundaries
        return super.getIncrVars();
      case COPY_REF: {
        return Pair.create(getInput(0).getVar().asList(),
                           getInput(0).getVar().asList());
      }
      case UPDATE_INCR:
      case UPDATE_MIN:
      case UPDATE_SCALE:
        // Consumes a read refcount for the input argument and
        // write refcount for updated variable
        return Pair.create(getInput(0).getVar().asList(),
                           getOutput(0).asList());
      default:
        // Return default
        return super.getIncrVars();
    }
  }
  
  @Override
  public List<Var> tryPiggyback(Counters<Var> increments, RefCountType type) {
    switch (op) {
      case LOAD_SCALAR:
      case LOAD_FILE:
      case LOAD_REF: 
      case LOAD_ARRAY:
      case LOAD_BAG: 
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
        // TODO: don't allow piggybacking r/w refcounts yet since that might
        // lead to premature closing of outer array -> premature closing of inner
        // array
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
  
  public Pair<Var, Var> getComponentAlias() {
    switch (op) {
      case ARR_CREATE_NESTED_IMM:
      case ARR_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_BAG:
        // From inner object to immediately enclosing
        return Pair.create(getOutput(0), getOutput(1));
      case AREF_CREATE_NESTED_IMM:
      case AREF_CREATE_NESTED_FUTURE:
        // From inner array to immediately enclosing
        return Pair.create(getOutput(0), getOutput(2));
      case LOAD_REF:
        // If reference was a part of something, modifying the
        // dereferenced object will modify the whole
        return Pair.create(getOutput(0), getInput(0).getVar());
      case COPY_REF:
        return Pair.create(getOutput(0), getInput(0).getVar());
      case STORE_REF:
        // Sometimes a reference is filled in
        return Pair.create(getOutput(0), getInput(0).getVar());
      case STRUCT_LOOKUP:
      case STRUCTREF_COPY_OUT:
        // Output is alias for part of struct
        return Pair.create(getOutput(0), getInput(0).getVar());
      default:
        return null;
    }
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