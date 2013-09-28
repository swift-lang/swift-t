package exm.stc.ic.opt.valuenumber;

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
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.CongruenceType;
import exm.stc.ic.tree.Opcode;

/**
 * Represent a ComputedValue with a known value or stored in
 * a known location 
 */
public class ValLoc {
  private final ArgCV value;
  /** Storage location or constant value of result */
  private final Arg location;
  /** if location var is known to be closed */
  private final Closed locClosed;
  /** true if location var is a copy only. I.e. cannot be alias
   * of another value with same CV */
  private final boolean isValCopy;
  
  public ArgCV value() {
    return value;
  }

  public Arg location() {
    return location;
  }

  public Closed locClosed() {
    return locClosed;
  }
  
  public boolean isValCopy() {
    return isValCopy;
  }
  
  public CongruenceType congType() {
    if (isValCopy) {
      // Cannot be alias since a copy happened;
      return CongruenceType.VALUE;
    } else {
      return value.congType();
    }
  }

  private ValLoc(ArgCV value, Arg location,
      Closed locClosed, boolean isValCopy) {
    assert(value != null);
    this.value = value;
    this.location = location;
    this.locClosed = locClosed;
    this.isValCopy = isValCopy;
  }
  
  public ValLoc(ArgCV value, Arg location,
      Closed locClosed,  IsValCopy isValCopy) {
    this(value, location, locClosed,
         isValCopy == IsValCopy.YES);
  }
  
  public static ValLoc build(ArgCV value, Arg location,
                             Closed locClosed) {
    return new ValLoc(value, location, locClosed, IsValCopy.NO);
  }
  
  public static ValLoc buildResult(Opcode op, String subop, List<Arg> inputs,
                      Arg valLocation, Closed locClosed, IsValCopy valCopy) {
    ArgCV cv = new ArgCV(op, subop, inputs);
    return new ValLoc(cv, valLocation, locClosed, valCopy);
  }

  public static ValLoc buildResult(Opcode op, List<Arg> inputs,
      Arg valLocation, Closed locClosed, IsValCopy valCopy) {
    return buildResult(op, "", inputs, valLocation, locClosed,
                       valCopy);
  }

  public static ValLoc buildResult(Opcode op, String subop,
      List<Arg> inputs, Arg valLocation, Closed locClosed) {
    return buildResult(op, subop, inputs, valLocation, locClosed,
                       IsValCopy.NO);  
  }

  public static ValLoc buildResult(Opcode op, List<Arg> inputs,
      Arg valLocation, Closed locClosed) {
    return buildResult(op, "", inputs, valLocation, locClosed);
  }

  public static ValLoc buildResult(Opcode op, Arg input,
                        Arg valLocation, Closed locClosed) {
    return buildResult(op, "", Collections.singletonList(input), valLocation,
                       locClosed);
  }

  public static ValLoc buildResult(Opcode op, String subop, Arg input,
      Arg valLocation, Closed locClosed) {
    return buildResult(op, subop, Arrays.asList(input), valLocation, locClosed);
  }
  public static ValLoc buildResult(Opcode op, String subop, List<Arg> inputs) {
    return buildResult(op, subop, inputs, null, Closed.MAYBE_NOT);
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
    return ValLoc.build(ComputedValue.makeCopy(src), dst.asArg(),
                        Closed.MAYBE_NOT);
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
                       CongruenceType copyType) {
    // See if we can determine that new location is closed
    Closed newLocClosed; 
    if (copyMode == TaskMode.SYNC) {
      newLocClosed = locClosed;
    } else {
      newLocClosed = Closed.MAYBE_NOT;
    }
    // See if we still have an alias
    boolean newIsValCopy = isValCopy || copyType != CongruenceType.ALIAS;
    return new ValLoc(value, copiedTo.asArg(), newLocClosed,
                      newIsValCopy);
  }

  /**
   * Create copy of this ValLoc with original substituted
   * for replacement
   */
  public ValLoc substituteInputs(Var original, Arg replacement,
      CongruenceType copyType) {
    Map<Var, Arg> replace = Collections.singletonMap(original, replacement);
    
    List<Arg> newInputs = new ArrayList<Arg>(value.getInputs());
    ICUtil.replaceArgsInList(replace, newInputs);
    
    boolean newIsValCopy = isValCopy || copyType != CongruenceType.ALIAS;
    ArgCV newVal = value.substituteInputs2(newInputs);
    return new ValLoc(newVal, location, locClosed, newIsValCopy);
  }
  
  /**
   * @param dst
   * @param src
   * @return Computed value indicating dst is alias of src
   */
  public static ValLoc makeAlias(Var dst, Var src) {
    return build(ComputedValue.makeAlias(src.asArg()), dst.asArg(),
                 Closed.MAYBE_NOT);
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
        boolean refResult) {
    Arg contentsArg = contents.asArg();
    ArgCV val;
    if (refResult) {
      assert(Types.isMemberReference(contents, arr)) :
            "not member ref: " + contents + " " + arr;
      val = ComputedValue.arrayRefCV(arr, ix);
    } else {
      assert(Types.isMemberType(arr, contents)) :
            "not member: " + contents + " " + arr;
      val = ComputedValue.arrayCV(arr, ix);
    }
    return new ValLoc(val, contentsArg, Closed.MAYBE_NOT, IsValCopy.NO);
  }
  
  public static ValLoc makeCreateNestedResult(Var arr, Arg ix, Var contents,
      boolean refResult) {
    Arg contentsArg = contents == null ? null : Arg.createVar(contents);
    ArgCV val;
    if (refResult) {
      val = ComputedValue.arrayRefNestedCV(arr, ix);
    } else {
      assert(contents == null || Types.isMemberType(arr, contents)) :
            "not member: " + contents + " " + arr;
      val = ComputedValue.arrayNestedCV(arr, ix);
    }
    return ValLoc.build(val, contentsArg, Closed.MAYBE_NOT);
  }
  
  public static ValLoc makeStructLookupResult(Var elem, Var struct,
                                                 String fieldName) {
    return ValLoc.build(ComputedValue.structMemberCV(struct, fieldName),
                         elem.asArg(), Closed.MAYBE_NOT);
  }
  

  public static ValLoc makeFilename(Arg outFilename, Var inFile) {
    assert(Types.isFile(inFile.type()));
    assert(outFilename.isVar());
    assert(Types.isString(outFilename.getVar().type()));
    return ValLoc.buildResult(Opcode.GET_FILENAME,
        Arrays.asList(inFile.asArg()), outFilename, Closed.MAYBE_NOT);
  }
  
  public static ValLoc makeFilenameVal(Arg file, Arg filenameVal) {
    assert(Types.isFile(file.type()));
    assert(filenameVal == null || filenameVal.isImmediateString());
    return ValLoc.buildResult(Opcode.GET_FILENAME_VAL,
                            file, filenameVal, Closed.YES_NOT_RECURSIVE);
  }

  public static ValLoc makeFilenameLocal(Arg outFilename, Var inFile) {
    assert(Types.isFileVal(inFile));
    assert(outFilename.isImmediateString());
    return ValLoc.buildResult(Opcode.GET_LOCAL_FILENAME,
            inFile.asArg().asList(), outFilename, Closed.YES_NOT_RECURSIVE);
  }

  /**
   * ValLoc representing result of dereference ref
   * @param contents of ref
   * @param ref
   * @param copied if it is a copy of the original
   */
  public static ValLoc derefCompVal(Var v, Var ref, IsValCopy copied) {
    assert(Types.isRefTo(ref, v)) : ref + " should be ref to " + v;
    return new ValLoc(ComputedValue.derefCompVal(ref),
                      v.asArg(), Closed.MAYBE_NOT, copied);
  }
  

  
  public List<ValLoc> asList() {
    return Collections.singletonList(this);
  }
  
  public String toString() {
    return value.toString() + " => " + location;
  }

  /**
   * Use enum instead of boolean for typechecking and to make calls
   * easier to parse
   */
  public static enum Closed {
    YES_RECURSIVE,
    YES_NOT_RECURSIVE,
    MAYBE_NOT;

    public boolean isClosed() {
      return this == YES_NOT_RECURSIVE || this == YES_RECURSIVE;
    }
    
    public boolean isRecClosed() {
      return this == YES_RECURSIVE;
    }
  }
  
  public static enum IsValCopy {
    YES,
    NO;
  }
}