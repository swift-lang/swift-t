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
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
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
public class ComputedValue {
  /** Ordered list of inputs of the expression.
   * The order is treated as important in deciding if the two computed values
   * are the same.  If the order of arguments to an expression doesn't matter,
   * they should be put in a canonical sorted order
   */
  
  /**
   * Define a notion of equivalence between locations with the same computed
   * value.
   */
  public static enum EquivalenceType {
    // Matching ComputedValues indicates that locations have same value
    VALUE,
    // Matching ComputedValues indicates that locations are aliases for
    // each other.  E.g. writing to either location gives same result.
    ALIAS,
  };
  
  final Opcode op;
  final String subop;
  final List<Arg> inputs;
  
  private final int hashCode; // Cache hashcode 
  
  public ComputedValue(Opcode op, String subop, List<Arg> inputs) {
    assert(op != null);
    assert(subop != null);
    assert(inputs != null);
    this.op = op;
    this.subop = subop;
    this.inputs = inputs;
    this.hashCode = calcHashCode();
  }
  
  public ComputedValue(Opcode op, List<Arg> inputs) {
    this(op, "", inputs);
  }


  public Opcode op() {
    return op;
  }

  public String subop() {
    return subop;
  }

  public List<Arg> getInputs() {
    return inputs;
  }
  
  public Arg getInput(int i) {
    return inputs.get(i);
  }

  /**
   * Check if it exactly matches another expression
   */
  @Override
  public boolean equals(Object otherO) {
    ComputedValue other = (ComputedValue) otherO;
    if (this.op == other.op && 
        this.subop.equals(other.subop) &&
        this.inputs.size() == other.inputs.size()) {
      for (int i = 0; i < inputs.size(); i++) {
        if (!this.inputs.get(i).equals(other.inputs.get(i))) {
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
    for (Arg o: this.inputs) {
      if (o == null) {
        throw new STCRuntimeError("Null oparg in " + this);
      }
      result = 37 * result + o.hashCode();
    }
    return result;
  }
  
  public String toString() {
    return op.toString() + "." + subop + inputs.toString();
  }
  
  public List<ComputedValue> asList() {
    return Collections.singletonList(this);
  }

  /**
   * Make a copy with a different list of inputs
   * @param newInputs
   * @return
   */
  public ComputedValue substituteInputs(List<Arg> newInputs) {
    return new ComputedValue(op, subop, newInputs);
  }

  public static ComputedValue makeCopy(Arg src) {
    return new ComputedValue(Opcode.FAKE, ComputedValue.COPY_OF, src.asList());
  }
  
  public static ComputedValue makeAlias(Arg src) {
    return new ComputedValue(Opcode.FAKE, ComputedValue.ALIAS_OF, src.asList());
  }
  
  /**
   * Return the canonical ComputedValue representation for
   * retrieving the value of this type
   * @param src
   * @return null if cannot be fetched
   */
  public static ComputedValue retrieveCompVal(Var src) {
    Type srcType = src.type();
    Opcode op = Opcode.retrieveOpcode(srcType);
    if (op == null) {
      return null;
    }
    return new ComputedValue(op, Arrays.asList(src.asArg()));
  }
  
  /**
   * Computed value to indicate that something is a direct handle
   * to array contents
   * @param arr
   * @param ix
   * @return
   */
  public static ComputedValue arrayCV(Var arr, Arg ix) {
    return new ComputedValue(Opcode.FAKE, ComputedValue.ARRAY_CONTENTS,
                              Arrays.asList(arr.asArg(), ix));
  }

  /**
   * Computed value to indicate that something is a reference to a direct
   * handle to array contents
   * @param arr
   * @param ix
   * @return
   */
  public static ComputedValue arrayRefCV(Var arr, Arg ix) {
    return new ComputedValue(Opcode.FAKE, ComputedValue.REF_TO_ARRAY_CONTENTS,
                              Arrays.asList(arr.asArg(), ix));
  }

  public static ComputedValue arrayRefNestedCV(Var arr, Arg ix) {
    return new ComputedValue(Opcode.FAKE, ComputedValue.REF_TO_ARRAY_NESTED,
        Arrays.asList(arr.asArg(), ix));
  }

  public static ComputedValue arrayNestedCV(Var arr, Arg ix) {
    return new ComputedValue(Opcode.FAKE, ComputedValue.ARRAY_NESTED,
        Arrays.asList(arr.asArg(), ix));
  }
  
  public static ComputedValue structMemberCV(Var struct, String fieldName) {
    return new ComputedValue(Opcode.STRUCT_LOOKUP, 
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
  
  public boolean isStructMember() {
    return op == Opcode.STRUCT_LOOKUP;
  }
  
  /**
   * @return the equivalence type of this computed value,
   *         assuming it wasn't copied
   */
  public EquivalenceType equivType() {
    if (isAlias() || isArrayMember() ||
        op == Opcode.LOAD_REF || isStructMember()) {
      return EquivalenceType.ALIAS;
    }
    // Assume value equivalence unless otherwise known
    return EquivalenceType.VALUE;
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

}