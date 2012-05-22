package exm.parser.ic.opt;

import java.util.Arrays;
import java.util.List;

import exm.ast.Variable;
import exm.parser.ic.ICInstructions.Instruction;
import exm.parser.ic.ICInstructions.Oparg;
import exm.parser.ic.ICInstructions.Opcode;
import exm.parser.util.STCRuntimeError;

/**
 * A class that provides a canonical description of a computed value.
 * For each value computed by an instruction, we should be able to
 * represent that with a canonical version of this class.    
 * If cv1.equals(cv2) then that should mean that the expressions are 
 *    for all intents and purposes identical
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
  final List<Oparg> inputs;
  final Oparg valLocation; // The constant expression or variable where it can be found
  final boolean outClosed; // true if out is known to be closed
  final EquivalenceType equivType;
  
  public ComputedValue(Opcode op, String subop, List<Oparg> inputs,
      Oparg valLocation, boolean outClosed, EquivalenceType equivType) {
    super();
    assert(op != null);
    assert(subop != null);
    assert(inputs != null);
    this.op = op;
    this.subop = subop;
    this.inputs = inputs;
    this.valLocation = valLocation;
    this.outClosed = outClosed;
    this.equivType = equivType;
  }
  
  public ComputedValue(Opcode op, String subop, List<Oparg> inputs,
      Oparg valLocation, boolean outClosed) {
    this(op, subop, inputs, valLocation, outClosed, EquivalenceType.VALUE);
  }
  public ComputedValue(Opcode op, String subop, Oparg input,
      Oparg valLocation, boolean outClosed) {
    this(op, subop, Arrays.asList(input), valLocation, outClosed);
  }
  
  public ComputedValue(Opcode op, String subop, Oparg input) {
    this(op, subop, Arrays.asList(input));
  }
  
  public ComputedValue(Opcode op, String subop, List<Oparg> inputs) {
    this(op, subop, inputs, null, false);
  }
  
  public Opcode getOp() {
    return op;
  }

  public String getSubop() {
    return subop;
  }

  public List<Oparg> getInputs() {
    return inputs;
  }
  
  public Oparg getInput(int i) {
    return inputs.get(i);
  }

  public Oparg getValLocation() {
    return valLocation;
  }

  public boolean isOutClosed() {
    return outClosed;
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
    int result = this.op.hashCode();
    result ^= this.subop.hashCode(); 
    for (Oparg o: this.inputs) {
      if (o == null) {
        throw new STCRuntimeError("Null oparg in " + this);
      }
      result ^= o.hashCode();
    }
    return result;
  }
  
  public String toString() {
    return op.toString() + "." + subop + inputs.toString();
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
  public static boolean isCopy(ComputedValue cv) {
    return cv.op == Opcode.FAKE && cv.subop.equals(COPY_OF);
  }
  
  /* Special subop strings to use with fake opcode */
  public static final String ARRAY_CONTENTS = "array_contents";
  public static final String REF_TO_ARRAY_CONTENTS = "ref_to_array_contents";
  private static final String COPY_OF = "copy_of";

  public static ComputedValue makeCopyCV(Variable dst, Oparg src) {
    return new ComputedValue(Opcode.FAKE, COPY_OF, 
                  src, Oparg.createVar(dst), false);
  }
}