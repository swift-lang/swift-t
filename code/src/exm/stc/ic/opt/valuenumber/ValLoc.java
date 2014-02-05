package exm.stc.ic.opt.valuenumber;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import exm.stc.common.lang.Arg;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
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
  
  /** Whether this represents a store operation */
  private final IsAssign isAssign;
  
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

  public IsAssign isAssign() {
    return isAssign;
  }

  private ValLoc(ArgCV value, Arg location,
      Closed locClosed, boolean isValCopy, IsAssign isAssign) {
    assert(value != null);
    this.value = value;
    this.location = location;
    this.locClosed = locClosed;
    this.isValCopy = isValCopy;
    this.isAssign = isAssign;
  }
  
  public ValLoc(ArgCV value, Arg location,
      Closed locClosed,  IsValCopy isValCopy, IsAssign isAssign) {
    this(value, location, locClosed,
         isValCopy == IsValCopy.YES, isAssign);
  }
  
  public static ValLoc build(ArgCV value, Arg location,
                             Closed locClosed, IsAssign isAssign) {
    return new ValLoc(value, location, locClosed, IsValCopy.NO, isAssign);
  }
  
  public static ValLoc buildResult(Opcode op, Object subop, List<Arg> inputs,
                      Arg valLocation, Closed locClosed, IsValCopy valCopy,
                      IsAssign isAssign) {
    ArgCV cv = new ArgCV(op, subop, inputs);
    return new ValLoc(cv, valLocation, locClosed, valCopy, isAssign);
  }
  
  public static ValLoc buildResult(Opcode op, List<Arg> inputs, Arg valLocation,
      Closed locClosed, IsAssign isAssign) {
    return buildResult(op, "", inputs, valLocation, locClosed,
                       IsValCopy.NO, isAssign);
  }
  
  public static ValLoc buildResult(Opcode op, Object subop,
      List<Arg> inputs, Arg valLocation, Closed locClosed, IsAssign isAssign) {
    return buildResult(op, subop, inputs, valLocation, locClosed,
                       IsValCopy.NO, isAssign);
  }

  public static ValLoc buildResult(Opcode op, Object subop, Arg input,
      Arg valLocation, Closed locClosed, IsAssign isAssign) {
    return buildResult(op, subop, Arrays.asList(input), valLocation, locClosed,
                       isAssign);
  }
  
  /**
   * @param dst
   * @param src
   * @return Computed value indicating dst has same value as src
   */
  public static ValLoc makeCopy(Var dst, Arg src, IsAssign isAssign) {
    assert(isAssign != IsAssign.TO_VALUE);
    return ValLoc.build(ComputedValue.makeCopy(src), dst.asArg(),
                        Closed.MAYBE_NOT, isAssign);
  }
  
  /**
   * Make copy of ValLoc to reflect that location was copied
   * to somewhere  
   * @param copiedTo
   * @param copyMode if copy happens immediately
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
                      newIsValCopy, IsAssign.TO_LOCATION);
  }
  
  /**
   * @param dst
   * @param src
   * @return Computed value indicating dst is alias of src
   */
  public static ValLoc makeAlias(Var dst, Var src) {
    return build(ComputedValue.makeAlias(src.asArg()), dst.asArg(),
                 Closed.MAYBE_NOT, IsAssign.NO);
  }
  

  /**
   * Make a standard computed value for array contents
   * @param arr
   * @param ix
   * @param contents
   * @param refResult if contents is of value type of elem
   * @return
   */
  public static ValLoc makeArrayResult(Var arr, Arg ix,
          Arg contents, boolean valResult, IsAssign isAssign) {
    ArgCV val;
    if (valResult) {
      assert(Types.isElemValType(arr, contents)) :
            "not member val: " + contents.toStringTyped() + " " + arr;
      val = ComputedValue.arrayValCV(arr, ix);
    } else {
      assert(Types.isElemType(arr, contents)) :
            "not member: " + contents.toStringTyped() + " " + arr;
      val = ComputedValue.arrayValCopyCV(arr, ix);
    }
    return new ValLoc(val, contents, Closed.MAYBE_NOT, IsValCopy.NO, isAssign);
  }
  
  public static ValLoc makeCreateNestedResult(Var arr, Arg ix, Var contents,
      boolean nonRefResult) {
    Arg contentsArg = contents == null ? null : Arg.createVar(contents);
    ArgCV val;
    if (nonRefResult) {
      assert(contents == null || Types.isElemValType(arr, contents)) :
        "not elem: " + contents + " " + arr;
      val = ComputedValue.arrayNestedCV(arr, ix);
    } else {
      assert(contents == null || Types.isElemType(arr, contents)) :
        "not elem: " + contents + " " + arr;
      val = ComputedValue.arrayRefNestedCV(arr, ix);
    }
    return ValLoc.build(val, contentsArg, Closed.MAYBE_NOT, IsAssign.NO);
  }
  
  public static ValLoc makeStructLookupResult(Var elem, Var struct,
                                                 String fieldName) {
    return ValLoc.build(ComputedValue.structMemberCV(struct, fieldName),
                         elem.asArg(), Closed.MAYBE_NOT, IsAssign.NO);
  }

  public static ValLoc makeContainerSizeCV(Var arr, Arg size, boolean future,
                                IsAssign isAssign) {
    assert(Types.isContainer(arr) ||
           Types.isContainerLocal(arr)) : arr;
    assert((!future && size.isImmediateInt()) ||
           (future && Types.isInt(size.type())));
    Opcode op = Types.isContainer(arr) ? Opcode.CONTAINER_SIZE :
                                         Opcode.CONTAINER_LOCAL_SIZE;
    return ValLoc.buildResult(op,
            arr.asArg().asList(), size, Closed.MAYBE_NOT, isAssign);
  }

  public static ValLoc makeArrayContainsCV(Var arr, Arg key, Arg out, 
                        boolean future, IsAssign isAssign) {
    assert(Types.isArray(arr) ||
            Types.isArrayLocal(arr)) : arr;
     assert((!future && Types.isArrayKeyVal(arr, key)) ||
            (future && Types.isArrayKeyFuture(arr, key)));
    ArgCV cv = ComputedValue.arrayContainsCV(arr, key);
    return ValLoc.build(cv, out, Closed.MAYBE_NOT, isAssign);
  }

  
  public static ValLoc makeFilename(Arg outFilename, Var inFile) {
    assert(Types.isFile(inFile.type()));
    assert(outFilename.isVar());
    assert(Types.isString(outFilename.getVar().type()));
    return build(ComputedValue.filenameCV(inFile),
                      outFilename, Closed.MAYBE_NOT, IsAssign.NO);
  }
  
  public static ValLoc makeFilenameVal(Var file, Arg filenameVal,
                                       IsAssign isAssign) {
    assert(Types.isFile(file));
    assert(filenameVal == null || filenameVal.isImmediateString());
    return build(ComputedValue.filenameValCV(file),
                          filenameVal, Closed.YES_NOT_RECURSIVE, isAssign);
  }

  public static ValLoc makeFilenameLocal(Arg outFilename, Var inFile,
          IsAssign isAssign) {
    assert(isAssign != IsAssign.TO_VALUE);
    assert(Types.isFileVal(inFile));
    assert(outFilename.isImmediateString());
    return build(ComputedValue.localFilenameCV(inFile),
                        outFilename, Closed.YES_NOT_RECURSIVE, isAssign);
  }

  /**
   * ValLoc representing result of dereference ref
   * @param v contents of ref
   * @param ref
   * @param copied if it is a copy of the original
   * @param isAssign
   */
  public static ValLoc derefCompVal(Var v, Var ref, IsValCopy copied,
                                    IsAssign isAssign) {
    assert(Types.isRefTo(ref, v)) : ref + " should be ref to " + v;
    return new ValLoc(ComputedValue.derefCompVal(ref),
                      v.asArg(), Closed.MAYBE_NOT, copied, isAssign);
  }
  

  
  public List<ValLoc> asList() {
    return Collections.singletonList(this);
  }
  
  public String toString() {
    String res = value.toString() + " => " + location;
    if (isValCopy) {
      res += " VAL_COPY";
    }
    if (locClosed != Closed.MAYBE_NOT) {
      res += " " + locClosed;
    }
    if (isAssign != IsAssign.NO) {
      res += " " + isAssign;
    }
    
    return res;
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
  
  /**
   * Whether the congruence is a copy of a value
   */
  public static enum IsValCopy {
    YES,
    NO;
  }
  
  /**
   * Whether we assigned a single assignment location here
   * TO_VALUE if:
   * - The computed value represents a single-assignment location such as
   *    a future array index
   * - The instruction creating this ValLoc assigned that location.
   * TO_LOCATION if:
   *  - This instruction assigns the output.
   */
  public static enum IsAssign {
    TO_LOCATION,
    TO_VALUE,
    NO;
  }
}