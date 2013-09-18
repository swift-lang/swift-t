package exm.stc.ic.opt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.ic.tree.ICInstructions.Opcode;

/**
 * Represent ComputedValue from a function
 */
public class ResultVal {
  private final ComputedValue value;
  /** Storage location or constant value of result */
  private final Arg location;
  private final boolean outClosed; // true if out is known to be closed
  /** If the output can be substituted safely with previous instances of cv */
  private  final boolean substitutable;
  
  public ComputedValue value() {
    return value;
  }

  public Arg location() {
    return location;
  }

  public boolean outClosed() {
    return outClosed;
  }

  public boolean isSubstitutable() {
    return substitutable;
  }

  public ResultVal(ComputedValue value, Arg location,
      boolean outClosed, boolean substitutable) {
    assert(value != null);
    this.value = value;
    this.location = location;
    this.outClosed = outClosed;
    this.substitutable = substitutable;
  }
  
  public static ResultVal buildResult(Opcode op, String subop, int index,
      List<Arg> inputs,
      Arg valLocation, boolean outClosed, boolean substitutable) {
    ComputedValue cv = new ComputedValue(op, subop, index, inputs);
    return new ResultVal(cv, valLocation, outClosed, substitutable);
  }

  public static ResultVal buildResult(Opcode op, String subop, List<Arg> inputs,
      Arg valLocation, boolean outClosed) {
    return buildResult(op, subop, 0, inputs, valLocation, outClosed, true);
  }

  public static ResultVal buildResult(Opcode op, List<Arg> inputs,
      Arg valLocation, boolean outClosed) {
    return buildResult(op, "", inputs, valLocation, outClosed);
  }

  public static ResultVal buildResult(Opcode op, Arg input,
                        Arg valLocation, boolean outClosed) {
    return buildResult(op, "", Collections.singletonList(input), valLocation,
                       outClosed);
  }

  public static ResultVal buildResult(Opcode op, String subop,
        int index, List<Arg> inputs,
      Arg valLocation, boolean outClosed) {
    return buildResult(op, subop, index, inputs, valLocation, outClosed, true);
  }

  public static ResultVal buildResult(Opcode op, String subop, Arg input,
      Arg valLocation, boolean outClosed) {
    return buildResult(op, subop, Arrays.asList(input), valLocation, outClosed);
  }
  public static ResultVal buildResult(Opcode op, String subop, List<Arg> inputs) {
    return buildResult(op, subop, inputs, null, false);
  }

  public static ResultVal buildResult(Opcode op, List<Arg> inputs) {
    return buildResult(op, "", inputs);
  }

  /**
   * @param dst
   * @param src
   * @return Computed value indicating dst has same value as src
   */
  public static ResultVal makeCopy(Var dst, Arg src) {
    return new ResultVal(ComputedValue.makeCopy(src), dst.asArg(),
                         false, true);
  }
  
  /**
   * @param dst
   * @param src
   * @return Computed value indicating dst is alias of src
   */
  public static ResultVal makeAlias(Var dst, Arg src) {
    return new ResultVal(ComputedValue.makeAlias(src), dst.asArg(),
                          false, true);
  }
  
  /**
   * Make a standard computed value for array contents
   * @param arr
   * @param ix
   * @param contents
   * @param refResult if contents is ref
   * @return
   */
  public static ResultVal makeArrayResult(Var arr, Arg ix, Var contents,
        boolean refResult) {
    return makeArrayResult(arr, ix, contents, refResult, true);
  }

  /**
   * Make a standard computed value for array contents
   * @param arr
   * @param ix
   * @param contents
   * @param refResult if contents is ref
   * @param substitutable 
   * @return
   */
  public static ResultVal makeArrayResult(Var arr, Arg ix, Var contents,
        boolean refResult, boolean substitutable) {
    Arg contentsArg = contents.asArg();
    ComputedValue val;
    if (refResult) {
      assert(Types.isMemberReference(contents, arr)) :
            "not member ref: " + contents + " " + arr;
      val = ComputedValue.arrayRefCV(arr, ix);
    } else {
      assert(Types.isMemberType(arr, contents)) :
            "not member: " + contents + " " + arr;
      val = ComputedValue.arrayCV(arr, ix);
    }
    return new ResultVal(val, contentsArg, false, substitutable);
  }
  
  public static ResultVal makeCreateNestedResult(Var arr, Arg ix, Var contents,
      boolean refResult) {
    Arg contentsArg = contents == null ? null : Arg.createVar(contents);
    ComputedValue val;
    if (refResult) {
      val = ComputedValue.arrayRefNestedCV(arr, ix);
    } else {
      assert(contents == null || Types.isMemberType(arr, contents)) :
            "not member: " + contents + " " + arr;
      val = ComputedValue.arrayNestedCV(arr, ix);
    }
    return new ResultVal(val, contentsArg, false, true);
  }
  
  public static ResultVal makeStructLookupResult(Var elem, Var struct,
                                                 String fieldName) {
    return new ResultVal(ComputedValue.structMemberCV(struct, fieldName),
                         elem.asArg(), false, true);
  }
  
  /**
   * Check to see if we can add new computed values to a dereferenced variable
   * @param refContent
   * @return
   */
  public static List<ResultVal> createLoadRefCVs(ComputedValue refContent,
                                                   Var derefDst) {
    if (refContent.op() == Opcode.FAKE && 
        refContent.subop().equals(ComputedValue.REF_TO_ARRAY_CONTENTS)) {
      return makeArrayResult(
          refContent.getInput(0).getVar(), refContent.getInput(1),
          derefDst, false).asList();  
    } 
    return Collections.emptyList();
  }
  

  
  public List<ResultVal> asList() {
    return Collections.singletonList(this);
  }
  
  public String toString() {
    return value.toString() + " => " + location;
  }

}