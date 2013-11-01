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
import exm.stc.ic.opt.OptUtil;
import exm.stc.ic.opt.valuenumber.ValLoc;
import exm.stc.ic.opt.valuenumber.ValLoc.Closed;
import exm.stc.ic.opt.valuenumber.ValLoc.IsAssign;
import exm.stc.ic.opt.valuenumber.ValLoc.IsValCopy;
import exm.stc.ic.tree.ICInstructions.CommonFunctionCall;
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
    case STORE_INT:
      gen.assignInt(getOutput(0), getInput(0));
      break;
    case STORE_BOOL:
      gen.assignBool(getOutput(0), getInput(0));
      break;
    case STORE_VOID:
      gen.assignVoid(getOutput(0), getInput(0));
      break;
    case STORE_FLOAT:
      gen.assignFloat(getOutput(0), getInput(0));
      break;
    case STORE_STRING:
      gen.assignString(getOutput(0), getInput(0));
      break;
    case STORE_BLOB:
      gen.assignBlob(getOutput(0), getInput(0));
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
    case ARRAY_LOOKUP_FUTURE:
      gen.arrayLookupFuture(getOutput(0), 
          getInput(0).getVar(), getInput(1).getVar(), false);
      break;
    case ARRAYREF_LOOKUP_FUTURE:
      gen.arrayLookupFuture(getOutput(0), 
          getInput(0).getVar(), getInput(1).getVar(), true);
      break;
    case ARRAY_LOOKUP_REF_IMM:
      gen.arrayLookupRefImm(getOutput(0), getInput(0).getVar(),
                            getInput(1), false);
      break;
    case ARRAY_LOOKUP_IMM:
      gen.arrayLookupImm(getOutput(0), getInput(0).getVar(),
                         getInput(1));
      break;
    case ARRAYREF_LOOKUP_IMM:
      gen.arrayLookupRefImm(getOutput(0), getInput(0).getVar(),
                            getInput(1), true);
      break;
    case ARRAY_INSERT_FUTURE:
      gen.arrayInsertFuture(getOutput(0), getInput(0).getVar(),
                            getInput(1).getVar(),
                            getInputs().size() == 3 ? getInput(2) : Arg.ONE);
      break;
    case ARRAY_DEREF_INSERT_FUTURE:
      gen.arrayDerefInsertFuture(getOutput(0), getInput(0).getVar(),
                            getInput(1).getVar(),
                            getInputs().size() == 3 ? getInput(2) : Arg.ONE);
      break;
    case ARRAY_INSERT_IMM:
      gen.arrayInsertImm(getOutput(0), getInput(0), getInput(1).getVar(),
          getInputs().size() == 3 ? getInput(2) : Arg.ZERO);
      break;
    case ARRAY_DEREF_INSERT_IMM:
      gen.arrayDerefInsertImm(getOutput(0), getInput(0), getInput(1).getVar(),
          getInputs().size() == 3 ? getInput(2) : Arg.ONE);
      break;
    case ARRAYREF_INSERT_FUTURE:
      gen.arrayRefInsertFuture(getOutput(0),
          getOutput(1), getInput(0).getVar(), getInput(1).getVar());
      break;
    case ARRAYREF_DEREF_INSERT_FUTURE:
      gen.arrayRefDerefInsertFuture(getOutput(0),
          getOutput(1), getInput(0).getVar(), getInput(1).getVar());
      break;
    case ARRAYREF_INSERT_IMM:
      gen.arrayRefInsertImm(getOutput(0),
          getOutput(1), getInput(0), getInput(1).getVar());
      break;
    case ARRAYREF_DEREF_INSERT_IMM:
      gen.arrayRefDerefInsertImm(getOutput(0),
          getOutput(1), getInput(0), getInput(1).getVar());
      break;
    case ARRAY_BUILD: {
      assert (getInputs().size() % 2 == 0);
      int elemCount = getInputs().size() / 2;
      List<Arg> keys = new ArrayList<Arg>(elemCount);
      List<Var> vals = new ArrayList<Var>(elemCount);
      for (int i = 0; i < elemCount; i++) {
        keys.add(getInput(i * 2));
        vals.add(getInput(i * 2 + 1).getVar());
      }
      gen.arrayBuild(getOutput(0), keys, vals);
      break;
    }
    case BAG_INSERT:
      gen.bagInsert(getOutput(0), getInput(0).getVar(), getInput(1));
      break;
    case STRUCT_LOOKUP:
      gen.structLookup(getOutput(0), getInput(0).getVar(),
                       getInput(1).getStringLit());
      break;
    case STRUCTREF_LOOKUP:
      gen.structRefLookup(getOutput(0), getInput(0).getVar(),
                           getInput(1).getStringLit());
      break;
    case STRUCT_INIT_FIELD:
      gen.structInitField(getOutput(0), getInput(0).getStringLit(),
                       getInput(1).getVar());
      break;
    case DEREF_INT:
      gen.dereferenceInt(getOutput(0), getInput(0).getVar());
      break;
    case DEREF_VOID:
      gen.dereferenceVoid(getOutput(0), getInput(0).getVar());
      break;
    case DEREF_BOOL:
      gen.dereferenceBool(getOutput(0), getInput(0).getVar());
      break;
    case DEREF_FLOAT:
      gen.dereferenceFloat(getOutput(0), getInput(0).getVar());
      break;
    case DEREF_STRING:
      gen.dereferenceString(getOutput(0), getInput(0).getVar());
      break;
    case DEREF_BLOB:
      gen.dereferenceBlob(getOutput(0), getInput(0).getVar());
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
    case ARRAY_CREATE_NESTED_FUTURE:
      gen.arrayCreateNestedFuture(getOutput(0), getOutput(1), 
                                  getInput(0).getVar());
      break;
    case ARRAYREF_CREATE_NESTED_FUTURE:
      gen.arrayRefCreateNestedFuture(getOutput(0), getOutput(1), getOutput(2),
                                     getInput(0).getVar());
      break;
    case ARRAYREF_CREATE_NESTED_IMM:
      gen.arrayRefCreateNestedImm(getOutput(0), getOutput(1), getOutput(2),
                                  getInput(0));
      break;
    case ARRAY_CREATE_NESTED_IMM:
      gen.arrayCreateNestedImm(getOutput(0), getOutput(1), getInput(0),
                               getInput(1), getInput(2));
      break;
    case ARRAY_CREATE_BAG:
      gen.arrayCreateBag(getOutput(0), getOutput(1), getInput(0),
                         getInput(1), getInput(2));
      break;
    case LOAD_INT:
      gen.retrieveInt(getOutput(0), getInput(0).getVar(),
          getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case LOAD_STRING:
      gen.retrieveString(getOutput(0), getInput(0).getVar(),
          getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case LOAD_BOOL:
      gen.retrieveBool(getOutput(0), getInput(0).getVar(),
          getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case LOAD_VOID:
      gen.retrieveVoid(getOutput(0), getInput(0).getVar(),
          getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;
    case LOAD_FLOAT:
      gen.retrieveFloat(getOutput(0), getInput(0).getVar(),
          getInputs().size() == 2 ? getInput(1) : Arg.ZERO);
      break;  
    case LOAD_BLOB:
      gen.retrieveBlob(getOutput(0), getInput(0).getVar(),
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
    default:
      throw new STCRuntimeError("didn't expect to see op " +
                op.toString() + " here");
    }

  }

  public static TurbineOp arrayRefLookupFuture(Var oVar, Var arrayRefVar,
      Var indexVar) {
    return new TurbineOp(Opcode.ARRAYREF_LOOKUP_FUTURE, oVar,
                          arrayRefVar.asArg(), indexVar.asArg());
  }

  public static TurbineOp arrayLookupFuture(Var oVar, Var arrayVar,
      Var indexVar) {
    return new TurbineOp(Opcode.ARRAY_LOOKUP_FUTURE,
        oVar, arrayVar.asArg(), indexVar.asArg());
  }

  public static Instruction arrayInsertFuture(Var array,
      Var ix, Var member) {
    return new TurbineOp(Opcode.ARRAY_INSERT_FUTURE,
            array, ix.asArg(),
            member.asArg());
  }
  
  public static Instruction arrayDerefInsertFuture(Var array,
      Var ix, Var member) {
    return new TurbineOp(Opcode.ARRAY_DEREF_INSERT_FUTURE,
            array, ix.asArg(),
            member.asArg());
  }

  public static Instruction arrayRefInsertFuture(Var outerArray,
      Var array, Var ix, Var member) {
    return new TurbineOp(Opcode.ARRAYREF_INSERT_FUTURE,
        Arrays.asList(outerArray, array), ix.asArg(), member.asArg());
  }
  
  public static Instruction arrayRefDerefInsertFuture(Var outerArray,
      Var array, Var ix, Var member) {
    return new TurbineOp(Opcode.ARRAYREF_DEREF_INSERT_FUTURE,
        Arrays.asList(outerArray, array),
        ix.asArg(), member.asArg());
  }
  
  public static Instruction arrayInsertImm(Var array,
      Arg ix, Var member) {
    assert(Types.isArray(array));
    assert(Types.isArrayKeyVal(array, ix));
    assert(Types.isMemberType(array, member));
    return new TurbineOp(Opcode.ARRAY_INSERT_IMM,
                          array, ix, member.asArg());
  }
  
  public static Instruction arrayDerefInsertImm(Var array,
      Arg ix, Var member) {
    return new TurbineOp(Opcode.ARRAY_DEREF_INSERT_IMM,
                         array, ix, member.asArg());
  }

  public static Instruction arrayRefInsertImm(Var outerArray,
      Var array, Arg ix, Var member) {
    return new TurbineOp(Opcode.ARRAYREF_INSERT_IMM,
        Arrays.asList(outerArray, array),
        ix, member.asArg());
  }
  
  public static Instruction arrayRefDerefInsertImm(Var outerArray,
      Var array, Arg ix, Var member) {
    return new TurbineOp(Opcode.ARRAYREF_DEREF_INSERT_IMM,
        Arrays.asList(outerArray, array),
        ix, member.asArg());
  }

  public static Instruction arrayRefLookupImm(Var oVar,
      Var arrayVar, Arg arrayIndex) {
    return new TurbineOp(Opcode.ARRAYREF_LOOKUP_IMM,
        oVar, arrayVar.asArg(), arrayIndex);
  }

  public static Instruction arrayLookupRefImm(Var oVar, Var arrayVar,
      Arg arrayIndex) {
    return new TurbineOp(Opcode.ARRAY_LOOKUP_REF_IMM,
        oVar, arrayVar.asArg(), arrayIndex);
  }
  
  public static Instruction arrayLookupImm(Var oVar, Var arrayVar,
      Arg arrayIndex) {
    return new TurbineOp(Opcode.ARRAY_LOOKUP_IMM,
        oVar, arrayVar.asArg(), arrayIndex);
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

  public static Instruction bagInsert(Var bag, Var elem, Arg writersDecr) {
    assert(Types.isBagElem(bag, elem));
    assert(writersDecr.isImmediateInt());
    return new TurbineOp(Opcode.BAG_INSERT, bag, elem.asArg(), writersDecr);
  }
  
  public static Instruction structInitField(Var structVar,
      String fieldName, Var fieldContents) {
    return new TurbineOp(Opcode.STRUCT_INIT_FIELD,
                    structVar,
                    Arg.createStringLit(fieldName),
                    fieldContents.asArg());
  }

  public static Instruction structLookup(Var oVar, Var structVar,
                                                        String fieldName) {
    assert(oVar.storage() == Alloc.ALIAS) : oVar;
    return new TurbineOp(Opcode.STRUCT_LOOKUP,
        oVar, structVar.asArg(),
            Arg.createStringLit(fieldName));
  }
  
  public static Instruction structRefLookup(Var oVar, Var structVar,
      String fieldName) {
    return new TurbineOp(Opcode.STRUCTREF_LOOKUP,
            oVar, structVar.asArg(),
            Arg.createStringLit(fieldName));
  }

  public static Instruction assignInt(Var target, Arg src) {
    return new TurbineOp(Opcode.STORE_INT, target, src);
  }

  public static Instruction assignBool(Var target, Arg src) {
    return new TurbineOp(Opcode.STORE_BOOL, target, src);
  }

  public static Instruction assignVoid(Var target, Arg src) {
    return new TurbineOp(Opcode.STORE_VOID, target, src);
  }

  public static Instruction assignFloat(Var target, Arg src) {
    return new TurbineOp(Opcode.STORE_FLOAT, target, src);
  }

  public static Instruction assignString(Var target, Arg src) {
    return new TurbineOp(Opcode.STORE_STRING, target, src);
  }

  public static Instruction assignBlob(Var target, Arg src) {
    return new TurbineOp(Opcode.STORE_BLOB, target, src);
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

  public static Instruction retrieveString(Var target, Var source) {
    return new TurbineOp(Opcode.LOAD_STRING, target, source.asArg());
  }

  public static Instruction retrieveInt(Var target, Var source) {
    return new TurbineOp(Opcode.LOAD_INT, target, source.asArg());
  }

  public static Instruction retrieveBool(Var target, Var source) {
    return new TurbineOp(Opcode.LOAD_BOOL, target, source.asArg());
  }

  public static Instruction retrieveVoid(Var target, Var source) {
    return new TurbineOp(Opcode.LOAD_VOID, target, source.asArg());
  }
  
  public static Instruction retrieveFloat(Var target, Var source) {
    return new TurbineOp(Opcode.LOAD_FLOAT, target, source.asArg());
  }
  
  public static Instruction retrieveBlob(Var target, Var source) {
    return new TurbineOp(Opcode.LOAD_BLOB, target, source.asArg());
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

  public static Instruction addressOf(Var target, Var src) {
    return new TurbineOp(Opcode.STORE_REF,
        target, src.asArg());
  }

  public static Instruction dereferenceInt(Var target, Var src) {
    return new TurbineOp(Opcode.DEREF_INT,
        target, src.asArg());
  }
  
  public static Instruction dereferenceVoid(Var target, Var src) {
    return new TurbineOp(Opcode.DEREF_VOID,
        target, src.asArg());
  }
  
  public static Instruction dereferenceBool(Var target, Var src) {
    return new TurbineOp(Opcode.DEREF_BOOL,
        target, src.asArg());
  }

  public static Instruction dereferenceFloat(Var target, Var src) {
    return new TurbineOp(Opcode.DEREF_FLOAT,
        target, src.asArg());
  }

  public static Instruction dereferenceString(Var target, Var src) {
    return new TurbineOp(Opcode.DEREF_STRING,
        target, src.asArg());
  }

  public static Instruction dereferenceBlob(Var target, Var src) {
    return new TurbineOp(Opcode.DEREF_BLOB,
        target, src.asArg());
  }
  
  public static Instruction dereferenceFile(Var target, Var src) {
    return new TurbineOp(Opcode.DEREF_FILE,
        target, src.asArg());
  }
  
  public static Instruction retrieveRef(Var target, Var src) {
    return new TurbineOp(Opcode.LOAD_REF,
        target, src.asArg());
  }
  
  public static Instruction copyRef(Var dst, Var src) {
    return new TurbineOp(Opcode.COPY_REF,
        dst, src.asArg());
        
  }

  public static Instruction arrayCreateNestedFuture(Var arrayResult,
      Var array, Var ix) {
    assert(Types.isArrayRef(arrayResult.type()));
    assert(arrayResult.storage() != Alloc.ALIAS);
    assert(Types.isArray(array.type()));
    assert(Types.isArrayKeyFuture(array, ix));
    // Both arrays are modified, so outputs
    return new TurbineOp(Opcode.ARRAY_CREATE_NESTED_FUTURE,
        Arrays.asList(arrayResult, array), ix.asArg());
  }

  public static Instruction arrayCreateNestedImm(Var arrayResult,
      Var arrayVar, Arg arrIx) {
    assert(Types.isArrayKeyVal(arrayVar, arrIx));
    assert(arrayResult.storage() == Alloc.ALIAS);
    // Both arrays are modified, so outputs
    return new TurbineOp(Opcode.ARRAY_CREATE_NESTED_IMM,
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
    return new TurbineOp(Opcode.ARRAYREF_CREATE_NESTED_FUTURE,
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
    return new TurbineOp(Opcode.ARRAYREF_CREATE_NESTED_IMM,
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
    /* The direct container write functions only mutate their output 
     * argument */
    case STRUCT_INIT_FIELD:
      return this.writesAliasVar();
      
    case ARRAY_BUILD:
    case ARRAY_INSERT_FUTURE:
    case ARRAY_DEREF_INSERT_FUTURE:
    case ARRAY_INSERT_IMM:
    case ARRAY_DEREF_INSERT_IMM:
    case ARRAYREF_INSERT_FUTURE:
    case ARRAYREF_DEREF_INSERT_FUTURE:
    case ARRAYREF_INSERT_IMM:
    case ARRAYREF_DEREF_INSERT_IMM:
      // Effect can be tracked back to original array
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
    
    case STORE_INT:
    case STORE_BOOL:
    case STORE_FLOAT:
    case STORE_STRING:
    case STORE_BLOB:
    case STORE_VOID:
    case STORE_FILE:
    case STORE_ARRAY:
    case STORE_BAG:
    case DEREF_INT:
    case DEREF_BOOL:
    case DEREF_VOID:
    case DEREF_FLOAT:
    case DEREF_STRING:
    case DEREF_BLOB:
    case DEREF_FILE:
    case LOAD_INT:
    case LOAD_BOOL:
    case LOAD_FLOAT:
    case LOAD_STRING:
    case LOAD_BLOB:
    case LOAD_VOID:
    case LOAD_FILE:
    case LOAD_ARRAY:
    case LOAD_BAG:
    case LOAD_RECURSIVE:
      return this.writesAliasVar();
      
    case ARRAY_LOOKUP_REF_IMM:
    case ARRAY_LOOKUP_FUTURE:
    case ARRAYREF_LOOKUP_FUTURE:
    case ARRAYREF_LOOKUP_IMM:
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
    case STRUCTREF_LOOKUP:
    case ARRAY_LOOKUP_IMM:
    case LATEST_VALUE:
        // Always has alias as output because the instructions initialises
        // the aliases
        return false;
        
    case ARRAY_CREATE_NESTED_FUTURE:
    case ARRAYREF_CREATE_NESTED_FUTURE:
    case ARRAY_CREATE_NESTED_IMM:
    case ARRAYREF_CREATE_NESTED_IMM:
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

  public void setInput(int i, Arg arg) {
    this.inputs.set(i, arg);
  }

  @Override
  public Instruction.MakeImmRequest canMakeImmediate(Set<Var> closedVars,
                                         boolean waitForClose) {
    boolean insertRefWaitForClose = waitForClose;
    // Try to take advantage of closed variables 
    switch (op) {
    case ARRAY_LOOKUP_REF_IMM: {
      // If array is closed or this index already inserted,
      // don't need to block on array.  
      // NOTE: could try to reduce other forms to this in one step,
      //      but its probably just easier to do it in multiple steps
      //      on subsequent passes
      Var arr = getInput(0).getVar();
      if (closedVars.contains(arr)) {
        // Don't request to wait for close - whole array doesn't need to be
        // closed
        return new MakeImmRequest(null, Arrays.<Var>asList(arr));
      }
      break;
    }
    case ARRAY_LOOKUP_FUTURE: {
      Var index = getInput(1).getVar();
      if (waitForClose || closedVars.contains(index)) {
        return new MakeImmRequest(null, Arrays.asList(index));
      }
      break;
    }
    case ARRAYREF_LOOKUP_FUTURE: {
      Var arr = getInput(0).getVar();
      Var ix = getInput(1).getVar();
      // We will take either the index or the dereferenced array
      List<Var> req = mkImmVarList(waitForClose, closedVars, arr, ix);
      if (req.size() > 0) {
        return new MakeImmRequest(null, req);
      }
      break;
    }
    case ARRAYREF_LOOKUP_IMM: {
      // Could skip using reference
      Var arrRef = getInput(0).getVar();
      if (waitForClose || closedVars.contains(arrRef)) {
        return new MakeImmRequest(null, Arrays.asList(arrRef));
      }
      break;
    }
    case STRUCTREF_LOOKUP: {
      Var structRef = getInput(0).getVar();
      if (waitForClose || closedVars.contains(structRef)) {
        return new MakeImmRequest(null, structRef.asList());
      }
      break;  
    }
    case ARRAY_DEREF_INSERT_IMM: {
      // See if we can get deref arg
      Var mem = getInput(1).getVar();
      List<Var> vs = mkImmVarList(waitForClose, closedVars, mem.asList());
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case ARRAY_INSERT_FUTURE:
    case ARRAY_DEREF_INSERT_FUTURE: {
      Var ix = getInput(0).getVar();
      Var val = getInput(1).getVar();
      List<Var> vs;
      if (op == Opcode.ARRAY_INSERT_FUTURE) {
        vs = ix.asList();
      } else { 
        assert(op == Opcode.ARRAY_DEREF_INSERT_FUTURE);
        vs = Arrays.asList(ix, val);
      }
      vs = mkImmVarList(waitForClose, closedVars, vs);
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case ARRAYREF_INSERT_IMM: 
    case ARRAYREF_DEREF_INSERT_IMM: {
      List<Var> vs;
      Var innerArrRef = getOutput(1);
      Var mem = getInput(1).getVar();
      if (op == Opcode.ARRAYREF_INSERT_IMM) {
        vs = innerArrRef.asList();
      } else {
        assert(op == Opcode.ARRAYREF_DEREF_INSERT_IMM);
        vs = Arrays.asList(innerArrRef, mem);
      }
      vs = mkImmVarList(insertRefWaitForClose, closedVars, vs);
      
      if (vs.size() > 0) {
        return new MakeImmRequest(null, vs);
      }
      break;
    }
    case ARRAYREF_INSERT_FUTURE: 
    case ARRAYREF_DEREF_INSERT_FUTURE: {
      Var innerArrRef = getOutput(1);
      Var ix = getInput(0).getVar();
      Var mem = getInput(1).getVar();
      List<Var> req;
      if (op == Opcode.ARRAYREF_INSERT_FUTURE) {
        req = Arrays.asList(innerArrRef, ix);
      } else {
        assert(op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE);
        req = Arrays.asList(innerArrRef, ix, mem);
      }
      // We will take either the index or the dereferenced array
      req = mkImmVarList(insertRefWaitForClose, closedVars, req);
      if (req.size() > 0) {
        return new MakeImmRequest(null, req);
      }
      break;
    }
    case ARRAY_CREATE_NESTED_FUTURE: {
      // Try to get immediate index
      Var ix = getInput(0).getVar();
      if (waitForClose || closedVars.contains(ix)) {
        return new MakeImmRequest(null, Arrays.asList(ix));
      }
      break;
    }
    case ARRAYREF_CREATE_NESTED_IMM: {
      Var arrRef = getOutput(2);
      if (waitForClose || closedVars.contains(arrRef)) {
        return new MakeImmRequest(null, Arrays.asList(arrRef));
      }
      break;
    }
    case ARRAYREF_CREATE_NESTED_FUTURE: {
      Var arrRef = getOutput(2);
      Var ix = getInput(0).getVar();
      List<Var> req5 = mkImmVarList(waitForClose, closedVars, arrRef, ix);
      if (req5.size() > 0) {
        return new MakeImmRequest(null, req5);
      }
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
  public Instruction.MakeImmChange makeImmediate(List<Instruction.Fetched<Var>> out,
                                     List<Instruction.Fetched<Arg>> values) {
    switch (op) {
    case ARRAY_LOOKUP_REF_IMM: {
      assert(values.size() == 1);
      // Input should be unchanged
      Var arr = getInput(0).getVar();
      Var newArr = values.get(0).fetched.getVar();
      assert(newArr.equals(arr));
      // Output switched from ref to value
      Var refOut = getOutput(0);
      Var valOut = OptUtil.createDerefTmp(refOut, Alloc.ALIAS);
      Instruction newI = arrayLookupImm(valOut, arr, getInput(1));
      return new MakeImmChange(valOut, refOut, newI);
    }
    case ARRAY_LOOKUP_FUTURE: {
      assert(values.size() == 1);
      Arg newIx = values.get(0).fetched;
      return new MakeImmChange(
              arrayLookupRefImm(getOutput(0), getInput(0).getVar(), newIx));
    }
    case ARRAYREF_LOOKUP_FUTURE: {
      assert(values.size() == 1 || values.size() == 2);
      Var mem = getOutput(0); 
      Var arrRef = getInput(0).getVar();
      Var ix = getInput(1).getVar();
      Arg newIx = Fetched.findFetched(values, ix);
      Var newArr = Fetched.findFetchedVar(values, arrRef);
      
      Instruction inst;
      // Could be either array ref, index, or both
      if (newIx != null && newArr != null) {
        inst = arrayLookupRefImm(mem, newArr, newIx);
      } else if (newIx != null && newArr == null){
        inst = arrayRefLookupImm(mem, arrRef, newIx);
      } else { 
        assert(newIx == null && newArr != null);
        inst = arrayLookupFuture(mem, newArr, ix);
      }
      return new MakeImmChange(inst);
    }
    case ARRAYREF_LOOKUP_IMM: {
      assert(values.size() == 1);
      // Switch from ref to plain array
      Var newArr = values.get(0).fetched.getVar();
      return new MakeImmChange(
          arrayLookupRefImm(getOutput(0), newArr, getInput(1)));
    }
    case STRUCTREF_LOOKUP: {
      assert(values.size() == 1);
      // OUtput switched from ref to value
      Var newStruct = values.get(0).fetched.getVar();
      assert(Types.isRefTo(getInput(0).getVar(), newStruct));
      Var refOut = getOutput(0);
      Var valOut = OptUtil.createDerefTmp(refOut, Alloc.ALIAS);
      String field = getInput(1).getStringLit();
      Instruction newI = structLookup(valOut, newStruct, field);
      return new MakeImmChange(valOut, refOut, newI);
    }
    case ARRAY_DEREF_INSERT_IMM: {
      assert(values.size() == 1);
      Var derefMember = values.get(0).fetched.getVar();
      return new MakeImmChange(
          arrayInsertImm(getOutput(0), getInput(0), derefMember));
    }
    case ARRAY_INSERT_FUTURE: {
      assert(values.size() == 1);
      Arg fetchedIx = values.get(0).fetched;
      return new MakeImmChange(
          arrayInsertImm(getOutput(0), fetchedIx, getInput(1).getVar()));
    }
    case ARRAY_DEREF_INSERT_FUTURE: {
      Var arr = getOutput(0);
      Var ix = getInput(0).getVar();
      Var mem = getInput(1).getVar();
      Arg newIx = Fetched.findFetched(values, ix);
      Var newMem = Fetched.findFetchedVar(values, mem);
      Instruction inst;
      if (newIx != null && newMem != null) {
        inst = arrayInsertImm(arr, newIx, newMem);
      } else if (newIx != null && newMem == null) {
        inst = arrayDerefInsertImm(arr, newIx, mem); 
      } else {
        assert(newIx == null && newMem != null);
        inst = arrayInsertFuture(arr, ix, newMem);
      }
      return new MakeImmChange(inst);
    }
    case ARRAYREF_INSERT_IMM: {
      assert(values.size() == 1);
      Var newOut = values.get(0).fetched.getVar();
      // Switch from ref to plain array
      return new MakeImmChange(arrayInsertImm(
          newOut, getInput(0), getInput(1).getVar()));
    }
    case ARRAYREF_DEREF_INSERT_IMM: {
      Var outerArrRef = getOutput(0);
      Var innerArrRef = getOutput(1);
      Arg ix = getInput(0);
      Var mem = getInput(1).getVar();
      Var newArr = Fetched.findFetchedVar(values, innerArrRef);
      Var newMem = Fetched.findFetchedVar(values, mem);
      Instruction newI;
      if (newArr != null && newMem != null) {
        newI = arrayInsertImm(newArr, ix, newMem);
      } else if (newArr != null && newMem == null) {
        newI = arrayDerefInsertImm(newArr, ix, mem);
      } else {
        assert(newArr == null && newMem != null);
        newI = arrayRefInsertImm(outerArrRef, innerArrRef, ix, newMem);
      }
      
      return new MakeImmChange(newI);
    }
    case ARRAYREF_INSERT_FUTURE:
    case ARRAYREF_DEREF_INSERT_FUTURE: {
      Var outerArr = getOutput(0);
      Var arrRef = getOutput(1);
      Var ix = getInput(0).getVar();
      Var mem = getInput(1).getVar();
      
      // Various combinations are possible
      Var newArr = Fetched.findFetchedVar(values, arrRef);
      Arg newIx = Fetched.findFetched(values, ix);
      Var derefMem = Fetched.findFetchedVar(values, mem);
      
      Instruction inst;
      if (derefMem != null || op == Opcode.ARRAYREF_INSERT_FUTURE) {
        if (derefMem != null) {
          assert(op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE);
          // It was dereferenced
          mem = derefMem;
        }
        if (newArr != null && newIx != null) {
          inst = arrayInsertImm(newArr, newIx, mem);
        } else if (newArr != null && newIx == null) {
          inst = arrayInsertFuture(newArr, ix, mem);
        } else if (newArr == null && newIx != null) {
          inst = arrayRefInsertImm(outerArr, arrRef, newIx, mem);
        } else {
          inst = arrayRefInsertFuture(outerArr, arrRef, ix, mem);
        }
      } else {
        assert(op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE);
        if (newArr != null && newIx != null) {
          inst = arrayDerefInsertImm(newArr, newIx, mem);
        } else if (newArr != null && newIx == null) {
          inst = arrayDerefInsertFuture(newArr, ix, mem);
        } else {
          assert(newArr == null && newIx != null);
          inst = arrayRefDerefInsertImm(outerArr, arrRef, newIx, mem);
        }
      }
      return new MakeImmChange(inst);
    }
    case ARRAY_CREATE_NESTED_FUTURE: {
      assert(values.size() == 1);
      Arg ix = values.get(0).fetched;
      Var oldResult = getOutput(0);
      Var oldArray = getOutput(1);
      assert(Types.isArrayKeyVal(oldArray, ix)) : oldArray + " " + ix.type();
      // Output type of instruction changed from ref to direct
      // array handle
      assert(Types.isArrayRef(oldResult.type()));
      Var newOut = OptUtil.createDerefTmp(oldResult, Alloc.ALIAS);
      return new MakeImmChange(newOut, oldResult,
          arrayCreateNestedImm(newOut, oldArray, ix));
    }
    case ARRAYREF_CREATE_NESTED_FUTURE: {
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
        Var newOut = OptUtil.createDerefTmp(arrResult, Alloc.ALIAS);
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
    case ARRAYREF_CREATE_NESTED_IMM: {
      assert(values.size() == 1);
      Var newArr = values.get(0).fetched.getVar();
      Arg ix = getInput(0);
      Var arrResult = getOutput(0);
      assert(Types.isArray(newArr));
      assert(Types.isArrayRef(arrResult.type()));
      Var newOut3 = OptUtil.createDerefTmp(arrResult, Alloc.ALIAS);
      assert(Types.isArrayKeyVal(newArr, ix));
      return new MakeImmChange(newOut3, arrResult,
          arrayCreateNestedImm(newOut3, newArr, getInput(0)));
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
      case ARRAY_LOOKUP_IMM:
      case ARRAY_CREATE_NESTED_IMM:
      case ARRAY_CREATE_BAG:
      case GET_FILENAME:
      case STRUCT_LOOKUP:
        // Initialises alias
        return Arrays.asList(Pair.create(getOutput(0), InitType.FULL));
        

      case INIT_UPDATEABLE_FLOAT:
        // Initializes updateable
        return Arrays.asList(Pair.create(getOutput(0), InitType.FULL));
      
      case INIT_LOCAL_OUTPUT_FILE:
      case LOAD_FILE:
        // Initializes output file value
        return Arrays.asList(Pair.create(getOutput(0), InitType.FULL));
      case STRUCT_INIT_FIELD:
        // Fills in part of struct
        return Arrays.asList(Pair.create(getOutput(0), InitType.PARTIAL));
      default:
        return Collections.emptyList();
    }
  }

  /**
   * @return list of outputs for which previous value is read
   */
  public List<Var> getReadOutputs(Map<String, Function> fns) {
    switch (op) {
    case ARRAY_CREATE_NESTED_IMM:
    case ARRAY_CREATE_NESTED_FUTURE:
      // In create_nested instructions the 
      // second array being inserted into is needed
      return Arrays.asList(getOutput(1));
    case ARRAY_CREATE_BAG:
      // the array being inserted into
      return getOutput(1).asList();
    case ARRAYREF_CREATE_NESTED_IMM:
    case ARRAYREF_CREATE_NESTED_FUTURE:
      // In ref_create_nested instructions the 
      // second array being inserted into is needed
      return Arrays.asList(getOutput(2));
      default:
        return Var.NONE;
    }
  }
  
  public List<Var> getModifiedOutputs() {
    switch (op) {
    case ARRAY_CREATE_NESTED_IMM:
    case ARRAY_CREATE_NESTED_FUTURE:
    case ARRAYREF_CREATE_NESTED_IMM:
    case ARRAYREF_CREATE_NESTED_FUTURE:
    case ARRAY_CREATE_BAG:
      // In create_nested instructions only the 
      // first output (the created array) is needed
      return Collections.singletonList(getOutput(0));

    case ARRAYREF_INSERT_FUTURE:
    case ARRAYREF_INSERT_IMM:
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
      case ARRAY_INSERT_FUTURE:
      case ARRAY_DEREF_INSERT_FUTURE:
      case ARRAY_INSERT_IMM:
      case ARRAY_DEREF_INSERT_IMM:
      case ARRAYREF_INSERT_FUTURE:
      case ARRAYREF_DEREF_INSERT_FUTURE:
      case ARRAYREF_INSERT_IMM:
      case ARRAYREF_DEREF_INSERT_IMM:
        // All outputs are piecewise assigned
        return getOutputs();
      case ARRAY_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_NESTED_IMM:
      case ARRAY_CREATE_BAG:
      case ARRAYREF_CREATE_NESTED_FUTURE:
      case ARRAYREF_CREATE_NESTED_IMM: {
        // All arrays except the newly created array; 
        List<Var> outputs = getOutputs();
        return outputs.subList(1, outputs.size());
      }
      case STRUCT_INIT_FIELD:
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
            Types.isArray(t) || Types.isPrimUpdateable(t)) {
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
    case STORE_INT:
    case STORE_BOOL:
    case STORE_VOID:
    case STORE_FLOAT:
    case STORE_STRING:
    case STORE_BLOB:
    case STORE_FILE:
    case STORE_ARRAY:
    case STORE_BAG:
    case LOAD_INT:
    case LOAD_BOOL:
    case LOAD_VOID:
    case LOAD_FLOAT:
    case LOAD_STRING:
    case LOAD_BLOB:
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
    case ARRAY_INSERT_IMM:
    case STRUCT_INIT_FIELD:
    case STRUCT_LOOKUP:
    case ARRAY_CREATE_NESTED_IMM:
    case ARRAY_CREATE_BAG:
    case STORE_REF:
    case LOAD_REF:
    case FREE_BLOB:
    case DECR_LOCAL_FILE_REF:
    case GET_FILENAME:
    case GET_LOCAL_FILENAME:
    case IS_MAPPED:
    case COPY_FILE_CONTENTS:
    case ARRAY_LOOKUP_IMM:
    case COPY_REF:
    case CHOOSE_TMP_FILENAME:
    case SET_FILENAME_VAL:
    case INIT_LOCAL_OUTPUT_FILE:
    case ARRAY_BUILD:
    case BAG_INSERT:
    case LOOKUP_CHECKPOINT:
    case WRITE_CHECKPOINT:
    case PACK_VALUES:
    case UNPACK_VALUES:
      return TaskMode.SYNC;
    
    case ARRAY_DEREF_INSERT_IMM:
    case ARRAY_INSERT_FUTURE:
    case ARRAY_DEREF_INSERT_FUTURE:
    case ARRAYREF_INSERT_FUTURE:
    case ARRAYREF_DEREF_INSERT_FUTURE:
    case ARRAYREF_INSERT_IMM:
    case ARRAYREF_DEREF_INSERT_IMM:
    case ARRAYREF_LOOKUP_FUTURE:
    case ARRAYREF_LOOKUP_IMM:
    case ARRAY_LOOKUP_REF_IMM:
    case DEREF_INT:
    case DEREF_BOOL:
    case DEREF_VOID:
    case DEREF_FLOAT:
    case DEREF_STRING:
    case DEREF_BLOB:
    case DEREF_FILE:
    case STRUCTREF_LOOKUP:
    case ARRAY_LOOKUP_FUTURE:
    case ARRAYREF_CREATE_NESTED_FUTURE:
    case ARRAY_CREATE_NESTED_FUTURE:
    case ARRAYREF_CREATE_NESTED_IMM:
      return TaskMode.LOCAL;
    default:
      throw new STCRuntimeError("Need to add opcode " + op.toString()
          + " to getMode");
    }
  }

  @Override
  public List<ValLoc> getResults() {
    switch(op) {
      case LOAD_BOOL:
      case LOAD_FLOAT:
      case LOAD_INT:
      case LOAD_REF:
      case LOAD_STRING: 
      case LOAD_BLOB: 
      case LOAD_VOID: 
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
      case STORE_BOOL:
      case STORE_FLOAT:
      case STORE_INT:
      case STORE_STRING: 
      case STORE_BLOB: 
      case STORE_VOID:
      case STORE_FILE:
      case STORE_ARRAY:
      case STORE_BAG: {

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
      case DEREF_BLOB:
      case DEREF_BOOL:
      case DEREF_VOID:
      case DEREF_FLOAT:
      case DEREF_INT:
      case DEREF_STRING: 
      case DEREF_FILE: {
        return ValLoc.derefCompVal(getOutput(0), getInput(0).getVar(),
                                   IsValCopy.YES, IsAssign.NO).asList();
      }
      case STRUCT_INIT_FIELD: {
        ValLoc lookup = ValLoc.makeStructLookupResult(
            getInput(1).getVar(), getOutput(0), getInput(0).getStringLit());
        return lookup.asList(); 
      }
      case STRUCT_LOOKUP: {
        ValLoc lookup = ValLoc.makeStructLookupResult(
            getOutput(0), getInput(0).getVar(), getInput(1).getStringLit());
        return lookup.asList(); 
      }
      case ARRAY_INSERT_IMM:
      case ARRAY_DEREF_INSERT_IMM:
      case ARRAY_INSERT_FUTURE:
      case ARRAY_DEREF_INSERT_FUTURE:
      case ARRAYREF_INSERT_IMM:
      case ARRAYREF_DEREF_INSERT_IMM: 
      case ARRAYREF_INSERT_FUTURE:
      case ARRAYREF_DEREF_INSERT_FUTURE: {
        // STORE <out array> <in index> <in var>
        // STORE  <in outer array> <out array> <in index> <in var>
        Var arr;
        if (op == Opcode.ARRAYREF_INSERT_FUTURE ||
            op == Opcode.ARRAYREF_INSERT_IMM ||
            op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE ||
            op == Opcode.ARRAYREF_DEREF_INSERT_IMM) {
          arr = getOutput(1);
        } else {
          arr = getOutput(0);
        }
        Arg ix = getInput(0);
        Var member = getInput(1).getVar();
        boolean insertingRef = (op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE ||
                                op == Opcode.ARRAYREF_DEREF_INSERT_IMM ||
                                op == Opcode.ARRAY_DEREF_INSERT_FUTURE ||
                                op == Opcode.ARRAY_DEREF_INSERT_IMM);
        return Arrays.asList(ValLoc.makeArrayResult(arr, ix, member,
                                       insertingRef, IsAssign.TO_VALUE));
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
          Var val = getInput(2 * i + 1).getVar();
          res.add(ValLoc.makeArrayResult(arr, key, val, false, IsAssign.TO_VALUE));
        }
        
        res.add(CommonFunctionCall.makeContainerSizeCV(arr,
                    Arg.createIntLit(elemCount), false, IsAssign.NO));
        return res;
      }
      case ARRAY_LOOKUP_IMM:
      case ARRAY_LOOKUP_REF_IMM:
      case ARRAY_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_FUTURE:
      case ARRAYREF_LOOKUP_IMM: {
        // LOAD <out var> <in array> <in index>
        Var arr = getInput(0).getVar();
        Arg ix = getInput(1);
        Var contents = getOutput(0);
        

        if (op == Opcode.ARRAY_LOOKUP_IMM) {
          // This just retrieves the item immediately
          return Arrays.asList(ValLoc.makeArrayResult(arr, ix, contents, false,
                                                      IsAssign.NO));
        } else {
          assert (Types.isMemberReference(contents, arr));
          List<ValLoc> res = new ArrayList<ValLoc>();
          // Will assign the reference
          res.add(ValLoc.makeArrayResult(arr, ix, contents, true,
                                         IsAssign.TO_LOCATION));
          return res;
        }
      }
      case ARRAY_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_NESTED_IMM:
      case ARRAYREF_CREATE_NESTED_FUTURE:
      case ARRAYREF_CREATE_NESTED_IMM: 
      case ARRAY_CREATE_BAG: {
        // CREATE_NESTED <out inner array> <in array> <in index>
        // OR
        // CREATE_NESTED <out inner array> <outer arr> <in array> <in index>
        // OR
        // CREATE_BAG <out inner bag> <in array> <in index> 
        Var nestedArr = getOutput(0);
        Var arr;
        if (op == Opcode.ARRAYREF_CREATE_NESTED_FUTURE ||
            op == Opcode.ARRAYREF_CREATE_NESTED_IMM) {
          arr = getOutput(2);
        } else {
          arr = getOutput(1);
        }
        Arg ix = getInput(0);
        List<ValLoc> res = new ArrayList<ValLoc>();
        
        boolean returnsRef = op != Opcode.ARRAY_CREATE_NESTED_IMM &&
                             op != Opcode.ARRAY_CREATE_BAG;
        // Mark as not substitutable since this op may have
        // side-effect of creating array
        res.add(ValLoc.makeArrayResult(arr, ix, nestedArr,
                                              returnsRef, IsAssign.NO));
        res.add(ValLoc.makeCreateNestedResult(arr, ix, nestedArr,
            returnsRef));
        return res;
      }
      case COPY_REF: {
        List<ValLoc> res = new ArrayList<ValLoc>();
        Var srcRef = getInput(0).getVar();
        res.add(ValLoc.makeAlias(getOutput(0), srcRef));
        return res;
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
      default:
        return null;
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
    if (op == Opcode.ARRAY_BUILD) {
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
      case ARRAY_BUILD:{
        List<Var> readIncr = new ArrayList<Var>(getInputs().size() / 2);
        for (int i = 0; i < getInputs().size() / 2; i++) {
          // Skip keys and only get values
          Arg elem = getInput(i * 2 + 1);
          // Container gets reference to member
          if (RefCounting.hasReadRefCount(elem.getVar())) {
            readIncr.add(elem.getVar());
          }
        }
        Var arr = getOutput(0);
        return Pair.create(readIncr, Arrays.asList(arr));
      }
      case STORE_BAG:
      case STORE_ARRAY: {
        // Inputs stored into array need to have refcount incremented
        // This finalizes array so will consume refcount
        return Pair.create(getInput(0).getVar().asList(),
                            getOutput(0).asList());
      }
      case DEREF_BLOB:
      case DEREF_VOID:
      case DEREF_BOOL:
      case DEREF_FILE:
      case DEREF_FLOAT:
      case DEREF_INT:
      case DEREF_STRING: {
        // Increment refcount of ref var
        return Pair.create(Arrays.asList(getInput(0).getVar()),
                           Var.NONE);
      }
      case ARRAYREF_LOOKUP_FUTURE:
      case ARRAY_LOOKUP_FUTURE: {
        // Array and index
        return Pair.create(
                Arrays.asList(getInput(0).getVar(), getInput(1).getVar()),
                Var.NONE);
      }
      case ARRAYREF_LOOKUP_IMM:
      case ARRAY_LOOKUP_REF_IMM: {
        // Array only
        return Pair.create(
                  Arrays.asList(getInput(0).getVar()),
                  Var.NONE);
      }
      case ARRAY_INSERT_IMM: {
        Var mem = getInput(1).getVar();
        // Increment reference to member
        return Pair.create(Arrays.asList(mem), Var.NONE);
      }
      case ARRAY_DEREF_INSERT_IMM: {
        // Increment reference to member ref
        // Increment writers count on array
        Var mem = getInput(1).getVar();
        return Pair.create(Arrays.asList(mem),
                           Arrays.asList(getOutput(0)));
      }
      case ARRAY_INSERT_FUTURE: 
      case ARRAY_DEREF_INSERT_FUTURE: {
        // Increment reference to member/member ref and index future
        // Increment writers count on array
        return Pair.create(Arrays.asList(
                getInput(0).getVar(), getInput(1).getVar()),
                Arrays.asList(getOutput(0)));
      }
      case ARRAYREF_INSERT_IMM:
      case ARRAYREF_DEREF_INSERT_IMM:
      case ARRAYREF_INSERT_FUTURE: 
      case ARRAYREF_DEREF_INSERT_FUTURE: {
        Arg ix = getInput(0);
        Var mem = getInput(1).getVar();
        Var outerArr = getOutput(0);
        Var arrayRef = getOutput(1);
        List<Var> readers = new ArrayList<Var>(3);
        readers.add(mem);
        readers.add(arrayRef);
        if (op == Opcode.ARRAYREF_INSERT_FUTURE ||
            op == Opcode.ARRAYREF_DEREF_INSERT_FUTURE) {
          readers.add(ix.getVar());
        } else {
          assert(op == Opcode.ARRAYREF_INSERT_IMM ||
                 op == Opcode.ARRAYREF_DEREF_INSERT_IMM);
        }
        // Maintain slots on outer array
        return Pair.create(readers,
                Arrays.asList(outerArr));
      }
      case ARRAY_CREATE_NESTED_FUTURE: {
        Var srcArray = getOutput(1);
        Var ix = getInput(0).getVar();
        return Pair.create(ix.asList(), srcArray.asList());
      }
      case STRUCTREF_LOOKUP: {
        return Pair.create(Arrays.asList(getInput(0).getVar()),
                           Var.NONE);
      }
      case ARRAYREF_CREATE_NESTED_IMM:
      case ARRAYREF_CREATE_NESTED_FUTURE: {
        Var outerArr = getOutput(1);
        assert(Types.isArray(outerArr.type())): outerArr + " " + this;
        assert(Types.isArray(outerArr.type().memberType()));
        Var arr = getOutput(2);
        Arg ixArg = getInput(0);
        List<Var> readVars;
        if (op == Opcode.ARRAYREF_CREATE_NESTED_IMM) {
          readVars = Arrays.asList(arr);
        } else {
          assert(op == Opcode.ARRAYREF_CREATE_NESTED_FUTURE);
          readVars = Arrays.asList(arr, ixArg.getVar());
        }
        return Pair.create(readVars,
                Arrays.asList(outerArr));
      }
      case BAG_INSERT:
        return Pair.create(getInput(0).getVar().asList(), Var.NONE);
      case STRUCT_INIT_FIELD:
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
      case ARRAY_INSERT_IMM:
      case ARRAY_DEREF_INSERT_IMM: 
      case ARRAY_INSERT_FUTURE: 
      case ARRAY_DEREF_INSERT_FUTURE: {
        Var arr = getOutput(0);
        if (type == RefCountType.WRITERS) {
          long amt = increments.getCount(arr);
          if (amt < 0) {
            assert(getInputs().size() == 2);
            // All except the fully immediate version decrement by 1 by default
            int defaultDecr = op == Opcode.ARRAY_INSERT_IMM ? 0 : 1;
            Arg decrArg = Arg.createIntLit(amt * -1 + defaultDecr);
            this.inputs = Arrays.asList(getInput(0), getInput(1), decrArg);
            return arr.asList();
          }
        }
        break;
      }
      case ARRAY_CREATE_NESTED_IMM:
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
        return tryPiggyBackHelper(increments, type, bag, 1, -1);
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
      case ARRAY_CREATE_NESTED_IMM:
      case ARRAY_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_BAG:
        // From inner object to immediately enclosing
        return Pair.create(getOutput(0), getOutput(1));
      case ARRAYREF_CREATE_NESTED_IMM:
      case ARRAYREF_CREATE_NESTED_FUTURE:
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
      case STRUCTREF_LOOKUP:
        // Output is alias for part of struct
        return Pair.create(getOutput(0), getInput(0).getVar());
      default:
        return null;
    }
  }

  public boolean isIdempotent() {
    switch (op) {
      case ARRAY_CREATE_NESTED_FUTURE:
      case ARRAY_CREATE_NESTED_IMM:
      case ARRAYREF_CREATE_NESTED_FUTURE:
      case ARRAYREF_CREATE_NESTED_IMM:
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
    
    private RefCountOp(Var target, boolean increment, RefCountType type, Arg amount) {
      super(getRefCountOp(increment, type), Var.NONE,
            Arrays.asList(target.asArg(), amount));
    }
    
    public static RefCountOp decrWriters(Var target, Arg amount) {
      return new RefCountOp(target, false, RefCountType.WRITERS, amount);
    }
    
    public static RefCountOp incrWriters(Var target, Arg amount) {
      return new RefCountOp(target, true, RefCountType.WRITERS, amount);
    }
    
    public static RefCountOp decrRef(Var target, Arg amount) {
      return new RefCountOp(target, false, RefCountType.READERS, amount);
    }
    
    public static RefCountOp incrRef(Var target, Arg amount) {
      return new RefCountOp(target, false, RefCountType.READERS, amount);
    }
    
    public static RefCountOp decrRef(RefCountType rcType, Var v, Arg amount) {
      return new RefCountOp(v, false, rcType, amount);
    }
    
    public static RefCountOp incrRef(RefCountType rcType, Var v, Arg amount) {
      return new RefCountOp(v, true, rcType, amount);
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
    
    
    
    private static Opcode getRefCountOp(boolean increment, RefCountType type) {
      if (type == RefCountType.READERS) {
        if (increment) {
          return Opcode.INCR_READERS;
        } else {
          return Opcode.DECR_READERS;
        }
      } else {
        assert(type == RefCountType.WRITERS);
        if (increment) {
          return Opcode.INCR_WRITERS;
        } else {
          return Opcode.DECR_WRITERS;
        }
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
      return new RefCountOp(getRCTarget(this), isIncrement(this.op),
                            getRCType(this.op), getRCAmount(this));
    }
  }


}