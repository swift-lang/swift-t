package exm.stc.ic.opt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.ic.opt.ComputedValue.EquivalenceType;
import exm.stc.ic.tree.ICInstructions.Opcode;

/**
 * Represent ComputedValue from a function
 */
public class ResultVal {
  private final ComputedValue value;
  /** Storage location or constant value of result */
  private final Arg location;
  private final boolean outClosed; // true if out is known to be closed
  private final EquivalenceType equivType;
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

  public EquivalenceType equivType() {
    return equivType;
  }

  public boolean isSubstitutable() {
    return substitutable;
  }

  public ResultVal(ComputedValue value, Arg location,
      boolean outClosed, EquivalenceType equivType, boolean substitutable) {
    assert(value != null);
    this.value = value;
    this.location = location;
    this.outClosed = outClosed;
    assert(equivType != null);
    this.equivType = equivType;
    this.substitutable = substitutable;
  }
  
  public static ResultVal buildResult(Opcode op, String subop, int index,
      List<Arg> inputs,
      Arg valLocation, boolean outClosed, EquivalenceType equivType, boolean substitutable) {
    ComputedValue cv = new ComputedValue(op, subop, index, inputs);
    return new ResultVal(cv, valLocation, outClosed, equivType, substitutable);
  }

  public static ResultVal buildResult(Opcode op, String subop, List<Arg> inputs,
      Arg valLocation, boolean outClosed, EquivalenceType equivType, boolean substitutable) {
    return buildResult(op, subop, 0, inputs, valLocation, outClosed, equivType, substitutable);
  }

  public static ResultVal buildResult(Opcode op, String subop, List<Arg> inputs,
      Arg valLocation, boolean outClosed, EquivalenceType equivType) {
    return buildResult(op, subop, inputs, valLocation, outClosed, equivType, true);
  }

  public static ResultVal buildResult(Opcode op, String subop, List<Arg> inputs,
      Arg valLocation, boolean outClosed) {
    return buildResult(op, subop, inputs, valLocation, outClosed, EquivalenceType.VALUE);
  }

  public static ResultVal buildResult(Opcode op, List<Arg> inputs,
      Arg valLocation, boolean outClosed) {
    return buildResult(op, "", inputs, valLocation, outClosed);
  }

  public static ResultVal buildResult(Opcode op, Arg input,
      Arg valLocation, boolean outClosed, EquivalenceType equivType) {
    return buildResult(op, "", Collections.singletonList(input), valLocation,
                       outClosed, equivType);
  }

  public static ResultVal buildResult(Opcode op, String subop,
        int index, List<Arg> inputs,
      Arg valLocation, boolean outClosed) {
    return buildResult(op, subop, index, inputs, valLocation, outClosed,
                       EquivalenceType.VALUE, true);
  }

  public static ResultVal buildResult(Opcode op, String subop, Arg input,
      Arg valLocation, boolean outClosed) {
    return buildResult(op, subop, Arrays.asList(input), valLocation, outClosed);
  }

  public static ResultVal buildResult(Opcode op, Arg input,
      Arg valLocation, boolean outClosed) {
    return buildResult(op, "", input, valLocation, outClosed);
  }

  public static ResultVal buildResult(Opcode op, String subop, List<Arg> inputs) {
    return buildResult(op, subop, inputs, null, false);
  }

  public static ResultVal buildResult(Opcode op, List<Arg> inputs) {
    return buildResult(op, "", inputs);
  }

  public static ResultVal buildResult(Opcode op, List<Arg> inputs,
      Arg valLocation, boolean outClosed, EquivalenceType equivType) {
    return buildResult(op, "", inputs, valLocation, outClosed, equivType);
  }

  /**
   * @param dst
   * @param src
   * @return Computed value indicating dst has same value as src
   */
  public static ResultVal makeCopy(Var dst, Arg src) {
    return new ResultVal(ComputedValue.makeCopy(src), dst.asArg(),
                        false, EquivalenceType.VALUE, true);
  }
  
  /**
   * @param dst
   * @param src
   * @return Computed value indicating dst is alias of src
   */
  public static ResultVal makeAlias(Var dst, Arg src) {
    return new ResultVal(ComputedValue.makeCopy(src), dst.asArg(),
                          false, EquivalenceType.REFERENCE, true);
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
      assert(Types.isMemberType(contents, arr)) :
            "not member: " + contents + " " + arr;
      val = ComputedValue.arrayCV(arr, ix);
    }
    return new ResultVal(val, contentsArg, false,
                          EquivalenceType.VALUE, substitutable);
  }
  
  public static ResultVal makeCreateNestedResult(Var arr, Arg ix, Var contents,
      boolean refResult) {
    Arg contentsArg = contents == null ? null : Arg.createVar(contents);
    ComputedValue val;
    EquivalenceType et;
    if (refResult) {
      val = ComputedValue.arrayRefNestedCV(arr, ix);
      et = EquivalenceType.VALUE;
    } else {
      assert(contents == null || Types.isMemberType(contents, arr)) :
            "not member: " + contents + " " + arr;
      val = ComputedValue.arrayNestedCV(arr, ix);
      et = EquivalenceType.REFERENCE;
    }
    return new ResultVal(val, contentsArg, false, et, true);
  }
  
  /**
   * Check to see if we can add new computed values to a derferenced variable
   * @param refContent
   * @return
   */
  public static List<ResultVal> createDereferencedCVs(ComputedValue refContent,
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