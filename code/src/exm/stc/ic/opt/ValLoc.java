package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.ic.ICUtil;
import exm.stc.ic.opt.ComputedValue.EquivalenceType;
import exm.stc.ic.tree.ICInstructions.Opcode;

/**
 * Represent a ComputedValue with a known value or stored in
 * a known location 
 */
public class ValLoc {
  private final ComputedValue value;
  /** Storage location or constant value of result */
  private final Arg location;
  /** true if location var is known to be closed */
  private final boolean locClosed;
  /** true if location var is a copy only. I.e. cannot be alias
   * of another value with same CV */
  private final boolean isValCopy;
  /** If the output can be substituted safely with previous instances of cv */
  private final boolean substitutable;
  
  public ComputedValue value() {
    return value;
  }

  public Arg location() {
    return location;
  }

  public boolean locClosed() {
    return locClosed;
  }

  public boolean isSubstitutable() {
    return substitutable;
  }
  
  public EquivalenceType equivType() {
    if (isValCopy) {
      // Cannot be alias since a copy happened;
      return EquivalenceType.VALUE;
    } else {
      return value.equivType();
    }
  }

  private ValLoc(ComputedValue value, Arg location,
      boolean locClosed, boolean substitutable,
      boolean isValCopy) {
    assert(value != null);
    this.value = value;
    this.location = location;
    this.locClosed = locClosed;
    this.substitutable = substitutable;
    this.isValCopy = isValCopy;
  }
  
  public ValLoc(ComputedValue value, Arg location,
      boolean locClosed, boolean substitutable) {
    this(value, location, locClosed, substitutable, false);
  }
  
  public static ValLoc buildResult(Opcode op, String subop, int index,
      List<Arg> inputs,
      Arg valLocation, boolean locClosed, boolean substitutable) {
    ComputedValue cv = new ComputedValue(op, subop, index, inputs);
    return new ValLoc(cv, valLocation, locClosed, substitutable);
  }

  public static ValLoc buildResult(Opcode op, String subop, List<Arg> inputs,
      Arg valLocation, boolean locClosed) {
    return buildResult(op, subop, 0, inputs, valLocation, locClosed, true);
  }

  public static ValLoc buildResult(Opcode op, List<Arg> inputs,
      Arg valLocation, boolean locClosed) {
    return buildResult(op, "", inputs, valLocation, locClosed);
  }

  public static ValLoc buildResult(Opcode op, Arg input,
                        Arg valLocation, boolean locClosed) {
    return buildResult(op, "", Collections.singletonList(input), valLocation,
                       locClosed);
  }

  public static ValLoc buildResult(Opcode op, String subop,
        int index, List<Arg> inputs,
      Arg valLocation, boolean locClosed) {
    return buildResult(op, subop, index, inputs, valLocation, locClosed, true);
  }

  public static ValLoc buildResult(Opcode op, String subop, Arg input,
      Arg valLocation, boolean locClosed) {
    return buildResult(op, subop, Arrays.asList(input), valLocation, locClosed);
  }
  public static ValLoc buildResult(Opcode op, String subop, List<Arg> inputs) {
    return buildResult(op, subop, inputs, null, false);
  }

  public static ValLoc buildResult(Opcode op, List<Arg> inputs) {
    return buildResult(op, "", inputs);
  }

  /**
   * @param dst
   * @param src
   * @return Computed value indicating dst has same value as src
   */
  public static ValLoc makeCopy(Var dst, Arg src) {
    return new ValLoc(ComputedValue.makeCopy(src), dst.asArg(),
                         false, true);
  }
  
  /**
   * Make copy of ValLoc to reflect that location was copied
   * to somewhere  
   * @param copiedTo
   * @param immediateCopy if copy happens immediately
   * @param copyType method of copying (i.e. whether it is an alias) 
   * @return
   */
  public ValLoc copyOf(Var copiedTo, TaskMode copyMode,
                       EquivalenceType copyType) {
    // See if we can determine that new location is closed
    boolean newLocClosed = locClosed && copyMode == TaskMode.SYNC;
    // See if we still have an alias
    boolean newIsValCopy = isValCopy || copyType != EquivalenceType.ALIAS;
    return new ValLoc(value, copiedTo.asArg(), newLocClosed,
                      substitutable, newIsValCopy);
  }

  /**
   * Create copy of this ValLoc with original substituted
   * for replacement
   */
  public ValLoc substituteInputs(Var original, Arg replacement,
      EquivalenceType copyType) {
    Map<Var, Arg> replace = Collections.singletonMap(original, replacement);
    
    List<Arg> newInputs = new ArrayList<Arg>(value.getInputs());
    ICUtil.replaceArgsInList(replace, newInputs);
    
    boolean newIsValCopy = isValCopy || copyType != EquivalenceType.ALIAS;
    ComputedValue newVal = value.substituteInputs(newInputs);
    return new ValLoc(newVal, location, locClosed, substitutable, newIsValCopy);
  }
  
  /**
   * @param dst
   * @param src
   * @return Computed value indicating dst is alias of src
   */
  public static ValLoc makeAlias(Var dst, Arg src) {
    return new ValLoc(ComputedValue.makeAlias(src), dst.asArg(),
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
  public static ValLoc makeArrayResult(Var arr, Arg ix, Var contents,
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
  public static ValLoc makeArrayResult(Var arr, Arg ix, Var contents,
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
    return new ValLoc(val, contentsArg, false, substitutable);
  }
  
  public static ValLoc makeCreateNestedResult(Var arr, Arg ix, Var contents,
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
    return new ValLoc(val, contentsArg, false, true);
  }
  
  public static ValLoc makeStructLookupResult(Var elem, Var struct,
                                                 String fieldName) {
    return new ValLoc(ComputedValue.structMemberCV(struct, fieldName),
                         elem.asArg(), false, true);
  }
  
  /**
   * Check to see if we can add new computed values to a dereferenced variable
   * @param refContent
   * @return
   */
  public static List<ValLoc> createLoadRefCVs(ValLoc refContent,
                                                   Var derefDst) {
    ComputedValue cv = refContent.value();
    if (cv.op() == Opcode.FAKE && 
        cv.subop().equals(ComputedValue.REF_TO_ARRAY_CONTENTS)) {
      return makeArrayResult(
          cv.getInput(0).getVar(), cv.getInput(1),
          derefDst, false).asList();  
    } 
    return Collections.emptyList();
  }
  

  
  public List<ValLoc> asList() {
    return Collections.singletonList(this);
  }
  
  public String toString() {
    return value.toString() + " => " + location;
  }

}