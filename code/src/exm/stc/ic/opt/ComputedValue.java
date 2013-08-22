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
package exm.stc.ic.opt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Opcode;

/**
 * A class that provides a canonical description of a computed value.
 * For each value computed by an instruction, we should be able to
 * represent that with a canonical version of this class.    
 * If cv1.equals(cv2) then that should mean that the expressions are 
 *    for all intents and purposes identical
 *    
 * This is related to the "Global Value Numbering" optimisation in the
 * compiler literature.
 */
public class ComputedValue {
  /** Ordered list of inputs of the expression.
   * The order is treated as important in deciding if the two computed values
   * are the same.  If the order of arguments to an expression doesn't matter,
   * they should be put in a canonical sorted order
   */
  
  public static enum EquivalenceType {
    VALUE, // ValLocation contains the same value as the canonical instance of 
           // expression
    REFERENCE, // ValLocation is a direct reference to the canonical instance of
               // expression (i.e. writes to any instance of the computedvalue
               //       are visible in all instances of it)
  };
  
  final Opcode op;
  final String subop;
  final int index; // Index of output if multiple outputs (0 is default);
  final List<Arg> inputs;
  
  private final int hashCode; // Cache hashcode 
  
  public ComputedValue(Opcode op, String subop, int index, List<Arg> inputs) {
    super();
    assert(op != null);
    assert(subop != null);
    assert(inputs != null);
    this.op = op;
    this.subop = subop;
    this.index = index;
    this.inputs = inputs;
    this.hashCode = calcHashCode();
  }
  
  public ComputedValue(Opcode op, String subop, List<Arg> inputs) {
    this(op, subop, 0, inputs);
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
        this.index == other.index &&
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
    result = 37 * result + ((Integer)index).hashCode();
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
    return new ComputedValue(op, subop, index, newInputs);
  }

  public static ComputedValue create(Instruction inst) {
    // TODO create unique expression for RHS of any kind of instruction
    // that we want to be able to eliminate
    /*
    if (inst.op == Opcode.LOCAL_ARITH_OP && 
        ((LocalArithOp)inst).localop == ArithOpcode.PLUS_INT)
    {
      return new ComputedValue(inst.op, ((LocalArithOp)inst).localop.toString(), 
                                  sortArgs(inst.getInputs()),
                                  inst.getOutputs().get(0).getVariable());
    } else if (inst.op == Opcode.ASSIGN_INT) {
      return new ComputedValue(inst.op, "", inst.getInputs(),
          inst.getOutputs().get(0).getVariable());
    } else if (inst.op == Opcode.ARRAY_LOAD_COMPUTED
        || inst.op == Opcode.ARRAY_LOAD_IMM_IX ||
        inst.op ==  Opcode.ARRAYREF_LOAD_COMPUTED || inst.op == 
        Opcode.ARRAY_LOAD_IMM_IX || inst.op == Opcode.DEREFERENCE_BLOB
        || inst.op == Opcode.DEREFERENCE_BOOL || inst.op == Opcode.DEREFERENCE_FLOAT
        || inst.op == Opcode.DEREFERENCE_INT || inst.op == Opcode.DEREFERENCE_STRING
        || inst.op == Opcode.STRUCT_LOOKUP || inst.op == Opcode.STRUCTREF_LOOKUP) {
      return new ComputedValue(inst.op, "", inst.getInputs(), 
          inst.getOutputs().get(0).getVariable());
    } else if (inst.op == Opcode.STRUCT_INSERT) {
      return new ComputedValue(Opcode.STRUCT_LOOKUP, "",
          Arrays.asList(inst.getOutputs().get(0), inst.getInputs().get(0)),
          inst.getInputs().get(1).getVariable());
    }
    */
    
    return null;
  }   
  
  public static ComputedValue makeCopy(Arg src) {
    return new ComputedValue(Opcode.FAKE, ComputedValue.COPY_OF, src.asList());
  }
  
  public boolean isCopy() {
    return this.op == Opcode.FAKE && this.subop.equals(COPY_OF);
  }
  
  public static boolean isAlias(ResultVal r) {
    return r.value().isCopy() && r.equivType() == EquivalenceType.REFERENCE;
  }
  
  /* Special subop strings to use with fake opcode */
  public static final String ARRAY_SIZE_FUTURE = "array_size_future";
  public static final String ARRAY_SIZE_VAL = "array_size_val";
  public static final String ARRAY_CONTENTS = "array_contents";
  public static final String REF_TO_ARRAY_CONTENTS = "ref_to_array_contents";
  public static final String ARRAY_NESTED = "autocreated_nested";
  public static final String REF_TO_ARRAY_NESTED = "ref_to_autocreated_nested";
  public static final String COPY_OF = "copy_of";

  public static ComputedValue arrayCV(Var arr, Arg ix) {
    return new ComputedValue(Opcode.FAKE, ComputedValue.ARRAY_CONTENTS,
                              Arrays.asList(arr.asArg(), ix));
  }
  
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
}