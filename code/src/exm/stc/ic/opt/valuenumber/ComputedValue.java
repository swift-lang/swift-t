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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.ic.opt.valuenumber.ValLoc.Closed;
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
  };
  
  final Opcode op;
  final String subop;
  final List<T> inputs;
  
  private final int hashCode; // Cache hashcode 
  
  public ComputedValue(Opcode op, String subop, List<T> inputs) {
    assert(op != null);
    assert(subop != null);
    assert(inputs != null);
    this.op = op;
    this.subop = subop;
    this.inputs = inputs;
    this.hashCode = calcHashCode();
  }
  
  public ComputedValue(Opcode op, List<T> inputs) {
    this(op, "", inputs);
  }


  public Opcode op() {
    return op;
  }

  public String subop() {
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
  
  /**
   * Make a copy with a different list of inputs
   * @param newInputs
   * @return
   */
   public ArgCV substituteInputs2(List<Arg> newInputs) {
     return new ArgCV(op, subop, newInputs);
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
  public static ArgCV retrieveCompVal(Var src) {
    Type srcType = src.type();
    Opcode op = Opcode.retrieveOpcode(srcType);
    if (op == null) {
      return null;
    }
    return new ArgCV(op, Arrays.asList(src.asArg()));
  }

  public static ValLoc assignComputedVal(Var dst, Arg val) {
    Type dstType = dst.type();
    if (Types.isPrimValue(dstType)) {
        BuiltinOpcode op;
        switch(dstType.primType()) {
        case BOOL:
          op = BuiltinOpcode.COPY_BOOL;
          break;
        case INT:
          op = BuiltinOpcode.COPY_INT;
          break;
        case FLOAT:
          op = BuiltinOpcode.COPY_FLOAT;
          break;
        case STRING:
          op = BuiltinOpcode.COPY_STRING;
          break;
        case BLOB:
          op = BuiltinOpcode.COPY_BLOB;
          break;
        case VOID:
          op = BuiltinOpcode.COPY_VOID;
          break;
        default:
          throw new STCRuntimeError("Unhandled type: " + dstType);
        }
        return ValLoc.buildResult(Opcode.LOCAL_OP, 
            op.toString(), Arrays.asList(val), dst.asArg(), Closed.MAYBE_NOT);
    } else {
      Opcode op = Opcode.assignOpcode(dstType);
      if (op != null) {
        return ValLoc.buildResult(op, Arrays.asList(val), dst.asArg(),
                                  Closed.YES_NOT_RECURSIVE);
      }
    }
    throw new STCRuntimeError("DOn't know how to assign to " + dst);
  }
  
  /**
   * Computed value representing result of dereference ref
   * @param ref
   * @param copied if it is a copy of the original
   */
  public static ArgCV derefCompVal(Var ref) {
    assert(Types.isRef(ref));
    return new ArgCV(Opcode.LOAD_REF, ref.asArg().asList());
  }
  
  /**
   * Computed value to indicate that something is a direct handle
   * to array contents
   * @param arr
   * @param ix
   * @return
   */
  public static ArgCV arrayCV(Var arr, Arg ix) {
    return new ArgCV(Opcode.FAKE, ComputedValue.ARRAY_CONTENTS,
                              Arrays.asList(arr.asArg(), ix));
  }

  /**
   * Computed value to indicate that something is a reference to a direct
   * handle to array contents
   * @param arr
   * @param ix
   * @return
   */
  public static ArgCV arrayRefCV(Var arr, Arg ix) {
    return new ArgCV(Opcode.FAKE,
        ComputedValue.REF_TO_ARRAY_CONTENTS, Arrays.asList(arr.asArg(), ix));
  }

  public static ArgCV arrayRefNestedCV(Var arr, Arg ix) {
    return new ArgCV(Opcode.FAKE, 
        ComputedValue.REF_TO_ARRAY_NESTED, Arrays.asList(arr.asArg(), ix));
  }

  public static ArgCV arrayNestedCV(Var arr, Arg ix) {
    return new ArgCV(Opcode.FAKE, ComputedValue.ARRAY_NESTED,
        Arrays.asList(arr.asArg(), ix));
  }
  
  public static ArgCV structMemberCV(Var struct, String fieldName) {
    return new ArgCV(Opcode.STRUCT_LOOKUP, 
            Arrays.asList(struct.asArg(), Arg.createStringLit(fieldName)));
  }
  
  public boolean isCopy() {
    return this.op == Opcode.FAKE && this.subop.equals(COPY_OF);
  }
  
  public boolean isAlias() {
    return this.op == Opcode.FAKE && this.subop.equals(ALIAS_OF); 
  }
  
  public boolean isArrayMember() {
    return (op == Opcode.FAKE && subop.equals(ComputedValue.ARRAY_NESTED)) ||
           (op == Opcode.FAKE && subop.equals(ComputedValue.ARRAY_CONTENTS));
  }
  
  public boolean isArrayMemberRef() {
    return (op == Opcode.FAKE && 
          subop.equals(ComputedValue.REF_TO_ARRAY_NESTED)) ||
        (op == Opcode.FAKE &&
          subop.equals(ComputedValue.REF_TO_ARRAY_CONTENTS));
  }
  
  public boolean isStructMember() {
    return op == Opcode.STRUCT_LOOKUP;
  }
  
  /**
   * @return the equivalence type of this computed value,
   *         assuming it wasn't copied
   */
  public CongruenceType congType() {
    if (isAlias() || isArrayMember() ||
        op == Opcode.LOAD_REF || isStructMember() ||
        op == Opcode.GET_FILENAME) {
      return CongruenceType.ALIAS;
    }
    // Assume value equivalence unless otherwise known
    return CongruenceType.VALUE;
  }
  
  /* Special subop strings to use with fake opcode */
  public static final String ARRAY_SIZE_FUTURE = "array_size_future";
  public static final String ARRAY_SIZE_VAL = "array_size_val";
  public static final String ARRAY_CONTENTS = "array_contents";
  public static final String REF_TO_ARRAY_CONTENTS = "ref_to_array_contents";
  public static final String ARRAY_NESTED = "autocreated_nested";
  public static final String REF_TO_ARRAY_NESTED = "ref_to_autocreated_nested";
  public static final String COPY_OF = "copy_of";
  public static final String ALIAS_OF = "alias_of";

  /**
   * Shorter class name for ComputedValue parameterized with Args
   */
  public static class ArgCV extends ComputedValue<Arg> {

    public ArgCV(Opcode op, List<Arg> inputs) {
      super(op, inputs);
    }
    
    public ArgCV(Opcode op, String subop, List<Arg> inputs) {
      super(op, subop, inputs);
    }
    
  }

  /**
   * Tagged union for Arg or recursive ComputedValue 
   */
  public static class RecCV {
    public RecCV(ComputedValue<RecCV> cv) {
      this.cv = cv;
      this.arg = null;
    }
    
    public RecCV(Opcode op, String subop, List<RecCV> inputs) {
      this(new ComputedValue<RecCV>(op, subop, inputs));
    }

    public RecCV(Arg arg) {
      this.cv = null;
      this.arg = arg;
    }
    
    private final ComputedValue<RecCV> cv;
    private final Arg arg;
    
    public boolean isCV() {
      return cv != null;
    }
    
    public boolean isArg() {
      return arg != null;
    }
    
    public Arg arg() {
      return arg;
    }
    
    public ComputedValue<RecCV> cv() {
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
      if (!(obj instanceof RecCV))
        throw new STCRuntimeError("Comparing " + this.getClass().getName() + 
                  " with " + obj.getClass().getName());
      RecCV other = (RecCV) obj;
      if (arg == null) {
        if (other.arg != null)
          return false;
      } else if (!arg.equals(other.arg))
        return false;
      if (cv == null) {
        if (other.cv != null)
          return false;
      } else if (!cv.equals(other.cv))
        return false;
      return true;
    }
  }
}