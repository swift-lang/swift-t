/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package exm.stc.ic.opt.valuenumber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Var;
import exm.stc.ic.opt.valuenumber.ValLoc.Closed;
import exm.stc.ic.opt.valuenumber.ValLoc.IsAssign;
import exm.stc.ic.tree.Opcode;

/**
 * A class that provides a canonical description of a computed value.
 * For each value computed by an instruction, we should be able to
 * represent that with a canonical version of this class.
 * If cv1.equals(cv2) then that should mean that the expressions are
 *    for all intents and purposes identical
 *
 * This is related to the "Value Numbering" optimisation in the
 * compiler literature.
 */
public class ComputedValue<T> {
  /** Ordered list of inputs of the expression.
   * The order is treated as important in deciding if the two computed values
   * are the same.  If the order of arguments to an expression doesn't matter,
   * they should be put in a canonical sorted order
   */

  public static final Object NO_SUBOP = "";

  /**
   * Define a notion of congruence between locations with the same computed
   * value.
   */
  public static enum CongruenceType {
    // Matching ComputedValues indicates that locations have same value
    VALUE,
    // Matching ComputedValues indicates that locations are aliases for
    // each other.  E.g. writing to either location gives same result.
    ALIAS,
  }

  final Opcode op;

  /**
   * Other identifier to disambiguate within op.  Should
   * implement equals() and hashcode().
   */
  final Object subop;
  final List<T> inputs;

  private final int hashCode; // Cache hashcode

  public ComputedValue(Opcode op, Object subop, List<T> inputs) {
    assert(op != null);
    assert(subop != null);
    assert(inputs != null);
    this.op = op;
    this.subop = subop;
    this.inputs = inputs;
    this.hashCode = calcHashCode();
  }

  public ComputedValue(Opcode op, List<T> inputs) {
    this(op, NO_SUBOP, inputs);
  }

  public ComputedValue(Opcode op, T input) {
    this(op, NO_SUBOP, Collections.singletonList(input));
  }


  public Opcode op() {
    return op;
  }

  public Object subop() {
    return subop;
  }

  public List<T> getInputs() {
    return inputs;
  }

  public T getInput(int i) {
    return inputs.get(i);
  }

  /**
   * Check if it exactly matches another expression
   */
  @Override
  public boolean equals(Object otherO) {
    if (!(otherO instanceof ComputedValue)) {
      throw new STCRuntimeError("Compared ComputedValue to " +
                                otherO.getClass());
    }
    ComputedValue<?> other = (ComputedValue<?>) otherO;
    if (this.op == other.op &&
        this.subop.equals(other.subop) &&
        this.inputs.size() == other.inputs.size()) {
      for (int i = 0; i < inputs.size(); i++) {
        Object otherInput = other.inputs.get(i);
        if (!this.inputs.get(i).equals(otherInput)) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }


  @Override
  public int hashCode() {
    return hashCode;
  }

  public int calcHashCode() {
    int result = this.op.hashCode();
    result = 37 * result + this.subop.hashCode();
    for (T o: this.inputs) {
      if (o == null) {
        throw new STCRuntimeError("Null input in " + this);
      }
      result = 37 * result + o.hashCode();
    }
    return result;
  }

  @Override
  public String toString() {
    return op.toString() + "." + subop + inputs.toString();
  }

  public List<ComputedValue<T>> asList() {
    return Collections.singletonList(this);
  }

  /**
   * Make a copy with a different list of inputs
   * @param newInputs
   * @return
   */
  public ComputedValue<T> substituteInputs(List<T> newInputs) {
    return new ComputedValue<T>(op, subop, newInputs);
  }

  public static ArgCV makeCopy(Arg src) {
    return new ArgCV(Opcode.FAKE, ComputedValue.COPY_OF,
                                  src.asList());
  }

  public static ArgCV makeAlias(Arg src) {
    return new ArgCV(Opcode.FAKE, ComputedValue.ALIAS_OF,
                                  src.asList());
  }

  /**
   * Return the canonical ComputedValue representation for
   * retrieving the value of this type
   * @param src
   * @return null if cannot be fetched
   */
  public static ArgCV retrieveCompVal(Var src, boolean recursive) {
    Opcode op = Opcode.retrieveOpcode(src, recursive);
    if (op == null) {
      return null;
    }
    return new ArgCV(op, Arrays.asList(src.asArg()));
  }

  public static ArgCV assignCompVal(Typed dst, Arg src, boolean recursive) {
    if (recursive) {
      assert(src.type().assignableTo(
             Types.unpackedType(dst)));

    } else {
      assert(Types.storeResultType(src, false)
          .assignableTo(dst.type())) :
            dst.type() + " <= " + src.type();
    }
    Opcode op = Opcode.assignOpcode(dst, recursive);
    if (op != null) {
      assert(op != Opcode.STORE_FILE); // STORE_FILE has extra info, can't handle here

      return new ArgCV(op, src.asList());
    }

    throw new STCRuntimeError("Don't know how to assign to " + dst
                              + (recursive ? " recursively" : NO_SUBOP));
  }

  public static ArgCV assignFileCompVal(Arg src, Arg setFilename) {
    assert(Types.isFileVal(src));
    assert(Types.isBoolVal(setFilename));
    return new ArgCV(Opcode.STORE_FILE, Arrays.asList(src, setFilename));
  }

  public static ValLoc assignValLoc(Var dst, Arg val,
                           IsAssign isAssign, boolean recursive) {
    Arg dstArg = dst.asArg();
    ArgCV cv = assignCompVal(dst, val, recursive);
    assert(cv != null) : val;

    Closed closed = recursive ? Closed.YES_RECURSIVE: Closed.YES_NOT_RECURSIVE;
    return ValLoc.build(cv, dstArg, closed, isAssign);
  }

  /**
   * Computed value representing result of dereference ref
   * @param ref
   */
  public static ArgCV derefCompVal(Var ref) {
    assert(Types.isRef(ref));
    return new ArgCV(Opcode.LOAD_REF, ref.asArg().asList());
  }

  public boolean isDerefCompVal() {
    return this.op == Opcode.LOAD_REF;
  }

  public boolean isRetrieve(boolean includeRecursive) {
    return this.op.isRetrieve(includeRecursive);
  }

  /**
   * Computed value to indicate that something is a copy
   * @param arr
   * @param ix
   * @return
   */
  public static ArgCV arrayValCopyCV(Var arr, Arg ix) {
    return new ArgCV(Opcode.FAKE, ComputedValue.ARRAY_ELEM_COPY,
                              Arrays.asList(arr.asArg(), ix));
  }

  /**
   * Computed value to indicate that something is a direct alias
   * @param arr
   * @param ix
   * @return
   */
  public static ArgCV arrayValAliasCV(Var arr, Arg ix) {
    return new ArgCV(Opcode.FAKE, ComputedValue.ARRAY_ELEM_ALIAS,
                              Arrays.asList(arr.asArg(), ix));
  }

  /**
   * Computed value to indicate that something is a reference to a direct
   * handle to array contents
   * @param arr
   * @param ix
   * @return
   */
  public static ArgCV arrayValCV(Var arr, Arg ix) {
    String subop = arrayValSubop(arr);
    return new ArgCV(Opcode.FAKE, subop, Arrays.asList(arr.asArg(), ix));
  }

  /**
   * We have different subops depending on array type, in order to distinguish
   * between the casees where alias and value congruence occur.
   * @param arrType
   * @return
   */
  private static String arrayValSubop(Typed arrType) {
    if (Types.isRef(Types.containerElemType(arrType))) {
      return ARRAY_ELEM_VALUE_REF;
    } else {
      return ARRAY_ELEM_VALUE_SCALAR;
    }
  }

  public static ArgCV arrayRefNestedCV(Var arr, Arg ix) {
    return new ArgCV(Opcode.FAKE,
        ComputedValue.ARRAY_NESTED_REF, Arrays.asList(arr.asArg(), ix));
  }

  public static ArgCV arrayNestedCV(Var arr, Arg ix) {
    return new ArgCV(Opcode.FAKE, ComputedValue.ARRAY_NESTED,
        Arrays.asList(arr.asArg(), ix));
  }

  public boolean isCopy() {
    return this.op == Opcode.FAKE && this.subop.equals(COPY_OF);
  }

  public boolean isAlias() {
    return this.op == Opcode.FAKE && this.subop.equals(ALIAS_OF);
  }


  public boolean isArrayMemberAlias() {
    return (op == Opcode.FAKE && subop.equals(ARRAY_ELEM_ALIAS));
  }

  public boolean isArrayMemberVal() {
    return isArrayMemberValScalar() || isArrayMemberValRef();
  }

  /**
   * If it represents an array member that is a reference to something
   * @return
   */
  public boolean isArrayMemberValRef() {
    return (op == Opcode.FAKE && subop.equals(ComputedValue.ARRAY_NESTED)) ||
           (op == Opcode.FAKE &&
                         subop.equals(ComputedValue.ARRAY_ELEM_VALUE_REF));
  }

  /**
   * If it represents a scalar array member
   * @return
   */
  public boolean isArrayMemberValScalar() {
    return (op == Opcode.FAKE &&
            subop.equals(ComputedValue.ARRAY_ELEM_VALUE_SCALAR));
  }

  public boolean isArrayMember() {
    return (op == Opcode.FAKE &&
          subop.equals(ComputedValue.ARRAY_NESTED_REF)) ||
        (op == Opcode.FAKE &&
          subop.equals(ComputedValue.ARRAY_ELEM_COPY)) ||
          isArrayMemberAlias();
  }

  /**
   * Convert a Array Member to an Array Member value.
   * @return
   */
  public static ArgCV derefArrayMember(ArgCV mem) {
    assert(mem.isArrayMember());
    Object newSubop;
    if (mem.subop.equals(ARRAY_NESTED_REF)) {
      newSubop = ARRAY_NESTED;
    } else {
      assert(mem.subop.equals(ARRAY_ELEM_COPY) ||
             mem.subop.equals(ARRAY_ELEM_ALIAS));
      newSubop = arrayValSubop(mem.getInput(0));
    }
    return new ArgCV(Opcode.FAKE, newSubop, mem.inputs);
  }

  public static ArgCV arrayContainsCV(Var arr, Arg key) {
    assert(Types.isArray(arr) || Types.isArrayRef(arr) ||
           Types.isArrayLocal(arr)) : arr;
    assert(Types.isArrayKeyVal(arr, key) ||
            Types.isArrayKeyFuture(arr, key));
    Opcode op = Types.isArray(arr) ? Opcode.ARR_CONTAINS :
                               Opcode.ARR_LOCAL_CONTAINS;
    ArgCV cv = new ArgCV(op, Arrays.asList(arr.asArg(), key));
    return cv;
  }

  public boolean isArrayContains() {
    return op == Opcode.ARR_CONTAINS || op == Opcode.ARR_LOCAL_CONTAINS;
  }

  /**
   * Computed Value for alias of struct
   * @param struct
   * @param fieldName
   * @return
   */
  public static ArgCV structFieldAliasCV(Var struct, List<Arg> fieldNames) {
    List<Arg> inputs = structFieldInputs(struct, fieldNames);
    return new ArgCV(Opcode.STRUCT_CREATE_ALIAS, inputs);
  }


  /**
   * Computed Value for something with same value as field of struct
   * @param struct
   * @param fieldName
   * @return
   */
  public static ArgCV structFieldCopyCV(Var struct, List<Arg> fieldNames) {
    List<Arg> inputs = structFieldInputs(struct, fieldNames);
    return new ArgCV(Opcode.STRUCT_COPY_OUT, inputs);
  }

  /**
   * Computed Value for value of field of struct
   * @param struct
   * @param fieldName
   * @return
   */
  public static ArgCV structFieldValCV(Var struct, List<Arg> fieldNames) {
    List<Arg> inputs = structFieldInputs(struct, fieldNames);

    Typed fieldType;
    try {
      fieldType = Types.structFieldType(struct,
                      Arg.extractStrings(fieldNames));
    } catch (TypeMismatchException e) {
      throw new STCRuntimeError(e.getMessage());
    }
    if (Types.isRef(fieldType)) {
      return new ArgCV(Opcode.FAKE, STRUCT_FIELD_VALUE_REF,  inputs);
    } else {
      return new ArgCV(Opcode.STRUCT_RETRIEVE_SUB, inputs);
    }
  }

  public static ArgCV structNestedCV(Var struct, List<Arg> fields) {
    List<Arg> args = new ArrayList<Arg>(fields.size() + 1);
    args.add(struct.asArg());
    args.addAll(fields);
    return new ArgCV(Opcode.FAKE, ComputedValue.STRUCT_NESTED, args);
  }

  /**
   * Helper to convert struct fields to input list
   * @param struct
   * @param fieldNames
   * @return
   */
  private static List<Arg> structFieldInputs(Var struct, List<Arg> fieldNames) {
    List<Arg> inputs = new ArrayList<Arg>(fieldNames.size() + 1);
    inputs.add(struct.asArg());
    for (Arg fieldName: fieldNames) {
      assert(fieldName.isString()) : fieldName;
      inputs.add(fieldName);
    }
    return inputs;
  }

  public boolean isStructFieldAlias() {
    return op == Opcode.STRUCT_CREATE_ALIAS;
  }

  public boolean isStructFieldCopy() {
    return op == Opcode.STRUCT_COPY_OUT;
  }

  /**
   * If it represents an struct member that is a reference to something
   * @return
   */
  public boolean isStructFieldVal() {
    return op == Opcode.STRUCT_RETRIEVE_SUB;
  }

  /**
   * If it represents an array member that is a reference to something
   * @return
   */
  public boolean isStructFieldValRef() {
    return (op == Opcode.FAKE &&
                         subop.equals(ComputedValue.STRUCT_FIELD_VALUE_REF));
  }

  public boolean canSubstituteInputs(CongruenceType congType) {
    if (op == Opcode.GET_FILENAME_ALIAS || op == Opcode.GET_FILENAME_VAL) {
      if (congType == CongruenceType.VALUE) {
        // Even if two file variables are congruent by value, they may
        // have different filename
        return false;
      }
    }
    // In most cases we have referential transparency, so can freely substitute
    return true;
  }


  /**
   * Filename future
   * @param file
   * @return
   */
  public static ArgCV filenameAliasCV(Var file) {
    assert(Types.isFile(file));
    return new ArgCV(Opcode.GET_FILENAME_ALIAS, file.asArg().asList());
  }

  /**
   * Filename value (the string stored in the filename future)
   * @param file
   * @return
   */
  public static ArgCV filenameValCV(Var file) {
    assert(Types.isFile(file));
    return new ArgCV(Opcode.GET_FILENAME_VAL, file.asArg().asList());
  }

  /**
   * Local file name (filename of a local file handle)
   * @param file
   * @return
   */
  public static ArgCV localFilenameCV(Var file) {
    assert(Types.isFileVal(file));
    return new ArgCV(Opcode.GET_LOCAL_FILENAME, file.asArg().asList());
  }

  public boolean isFilenameAliasCV() {
    return op == Opcode.GET_FILENAME_ALIAS;
  }

  public boolean isFilenameValCV() {
    return op == Opcode.GET_FILENAME_VAL;
  }

  public boolean isLocalFilenameCV() {
    return op == Opcode.GET_LOCAL_FILENAME;
  }

  public static ArgCV containerSizeCV(Var arr, boolean async) {
    boolean isLocal = Types.isContainerLocal(arr);
    assert(Types.isContainer(arr) ||
           isLocal) : arr;

    if (async) {
      assert(!isLocal);
      return new ArgCV(Opcode.FAKE, CONTAINER_SIZE_FUTURE,
                       arr.asArg().asList());
    } else {
      Opcode op = isLocal ? Opcode.CONTAINER_LOCAL_SIZE :
                            Opcode.CONTAINER_SIZE;
      return new ArgCV(op, arr.asArg());
    }
  }

  /**
   * @return the equivalence type of this computed value,
   *         assuming it wasn't copied
   */
  public CongruenceType congType() {
    if (isAlias() || op == Opcode.LOAD_REF ||
        op == Opcode.GET_FILENAME_ALIAS) {
      return CongruenceType.ALIAS;
    } else if (isArrayMemberAlias()) {
      return CongruenceType.ALIAS;
    } else if (isArrayMemberValRef()) {
      return CongruenceType.ALIAS;
    } else if (isStructFieldAlias()) {
      return CongruenceType.ALIAS;
    } else if (isStructFieldValRef()) {
      return CongruenceType.ALIAS;
    }
    // Assume value equivalence unless otherwise known
    return CongruenceType.VALUE;
  }

  /* Special subop strings to use with fake opcode */

  /**
   * Direct alias of array member
   */
  public static final String ARRAY_ELEM_ALIAS = "array_elem_alias";

  /**
   * Copy of array member
   */
  public static final String ARRAY_ELEM_COPY = "array_elem_copy";

  /**
   * Value of array element when scalar
   */
  public static final String ARRAY_ELEM_VALUE_SCALAR = "array_elem_value_scalar";
  /**
   * Value of array element when reference to something else
   */
  public static final String ARRAY_ELEM_VALUE_REF = "array_elem_value_ref";

  public static final String STRUCT_FIELD_VALUE_REF = "struct_field_value_ref";

  public static final String ARRAY_NESTED = "autocreated_nested";
  public static final String ARRAY_NESTED_REF = "array_autocreated_nested_ref";
  public static final String STRUCT_NESTED = "struct_autocreated_nested_ref";
  public static final String COPY_OF = "copy_of";
  public static final String ALIAS_OF = "alias_of";

  private static final String CONTAINER_SIZE_FUTURE = "container_size_future";

  /**
   * Shorter class name for ComputedValue parameterized with Args
   */
  public static class ArgCV extends ComputedValue<Arg> {

    public ArgCV(Opcode op, List<Arg> inputs) {
      super(op, inputs);
    }

    public ArgCV(Opcode op, Object subop, List<Arg> inputs) {
      super(op, subop, inputs);
    }

    public ArgCV(Opcode op, Arg input) {
      super(op, Collections.singletonList(input));
    }

    @Override
    public ArgCV substituteInputs(List<Arg> newInputs) {
      return new ArgCV(op, subop, newInputs);
    }

  }

  /**
   * Tagged union for Arg or recursive ComputedValue
   */
  public static class ArgOrCV {
    public ArgOrCV(ArgCV cv) {
      this.cv = cv;
      this.arg = null;
      this.hashCode = calcHashCode();
    }

    public ArgOrCV(Opcode op, Object subop, List<Arg> inputs) {
      this(new ArgCV(op, subop, inputs));
    }

    public ArgOrCV(Opcode op, List<Arg> inputs) {
      this(new ArgCV(op, inputs));
    }

    public ArgOrCV(Opcode op, Arg input) {
      this(new ArgCV(op, input));
    }

    public ArgOrCV(Arg arg) {
      this.cv = null;
      this.arg = arg;
      this.hashCode = calcHashCode();
    }

    private final ArgCV cv;
    private final Arg arg;
    private final int hashCode;

    public boolean isCV() {
      return cv != null;
    }

    public boolean isArg() {
      return arg != null;
    }

    public List<ArgOrCV> asList() {
      return Collections.singletonList(this);
    }

    public Arg arg() {
      assert(arg != null) : "Not an arg " + this;
      return arg;
    }

    public ArgCV cv() {
      assert(cv != null) : "Not a cv " + this;
      return cv;
    }

    @Override
    public String toString() {
      if (isArg()) {
        return arg.toString();
      } else {
        return cv.toString();
      }
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    private int calcHashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((arg == null) ? 0 : arg.hashCode());
      result = prime * result + ((cv == null) ? 0 : cv.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (!(obj instanceof ArgOrCV))
        throw new STCRuntimeError("Comparing " + this.getClass().getName() +
                  " with " + obj.getClass().getName());
      ArgOrCV other = (ArgOrCV) obj;
      if (arg != null) {
        if (other.arg == null) {
          return false;
        }
        return arg.equals(other.arg);
      } else {
        assert(cv != null);
        if (other.cv == null) {
          return false;
        }
        return cv.equals(other.cv);
      }
    }

    public ArgOrCV substituteInputs(List<Arg> newInputs) {
      return new ArgOrCV(cv.substituteInputs(newInputs));
    }
  }
}