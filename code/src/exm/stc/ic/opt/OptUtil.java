package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.List;

import exm.stc.ast.Types;
import exm.stc.ast.Variable;
import exm.stc.ast.Types.SwiftType;
import exm.stc.ast.Variable.DefType;
import exm.stc.ast.Variable.VariableStorage;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.ic.ICInstructions;
import exm.stc.ic.ICContinuations.Continuation;
import exm.stc.ic.ICInstructions.Instruction;
import exm.stc.ic.ICInstructions.Oparg;
import exm.stc.ic.ICInstructions.TurbineOp;
import exm.stc.ic.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.SwiftIC.Block;

public class OptUtil {

  static long unique = 0;


  /**
   * Generate optimiser variable name guaranteed to be unique
   * @param v
   * @return
   */
  public static String optVPrefix(Variable v) {
    String name = Variable.OPT_VALUE_VAR_PREFIX + v.getName() + "-"+ unique;
    unique++;
    return name;
  }
  
  /**
   * Fetch the value of a variable
   * @param block
   * @param instBuffer append fetch instruction to this list
   * @param var the variable to fetch the value of
   * @return variable holding value
   */
  public static Variable fetchValueOf(Block block, List<Instruction> instBuffer,
          Variable var) {
    SwiftType value_t = Types.derefResultType(var.getType());
    if (Types.isScalarValue(value_t)) {
      // The result will be a value
      // Use the OPT_VALUE_VAR_PREFIX to make sure we don't clash with
      //  something inserted by the frontend (this caused problems before)
      Variable value_v = new Variable(value_t,
          optVPrefix(var),
          VariableStorage.LOCAL, DefType.LOCAL_COMPILER, null);
      block.addVariable(value_v);
      instBuffer.add(ICInstructions.retrieveValueOf(value_v, var));
      return value_v;
    } else if (Types.isReference(var.getType())) {
      // The result will be an alias
      Variable deref = new Variable(value_t,
          optVPrefix(var),
          VariableStorage.ALIAS, DefType.LOCAL_COMPILER, null);
      block.addVariable(deref);
      instBuffer.add(TurbineOp.retrieveRef(deref, var));
      return deref;
    } else {
      throw new STCRuntimeError("shouldn't be possible to get here");
    }
  }



  /**
   * Same as fetchValue of, but more times
   * @param block
   * @param instBuffer
   * @param vars
   * @return
   */
  public static List<Oparg> fetchValuesOf(Block block, List<Instruction> instBuffer,
          List<Variable> vars) {
    List<Oparg> inVals = new ArrayList<Oparg>(vars.size());

    for (Variable v: vars) {
      Variable valueV = OptUtil.fetchValueOf(block, instBuffer, v);
      Oparg value = Oparg.createVar(valueV);
      inVals.add(value);
    }
    return inVals;
  }
  
  /**
   * Do the manipulation necessary to allow an old instruction
   * output variable to be replaced with a new one. Assume that
   * newOut is a value type of oldOut
   * @param instBuffer append any fixup instructions here
   * @param newOut
   * @param oldOut
   */
  public static void replaceInstructionOutputVar(Block block,
          List<Instruction> instBuffer, Variable newOut, Variable oldOut) {
    block.declareVariable(newOut);
    if (Types.isReferenceTo(oldOut.getType(),
        newOut.getType())) {
      if (oldOut.getStorage() == VariableStorage.ALIAS) {
        // Will need to initialise variable in this scope as before we
        // were relying on instruction to initialise it
        
        Variable replacement = new Variable(oldOut.getType(),
            oldOut.getName(), VariableStorage.TEMPORARY,
            oldOut.getDefType(), oldOut.getMapping());
        block.replaceVarDeclaration(oldOut, replacement);
      }
      instBuffer.add(TurbineOp.addressOf(oldOut, newOut));
    } else {
      throw new STCRuntimeError("Tried to replace instruction"
          + " output var " + oldOut + " with " + newOut + ": this doesn't make sense"
          + " to optimizer");
    }
  }
  
  public static List<Variable> declareLocalOpOutputVars(Block block,
          List<Variable> localOutputs) {
    if (localOutputs == null) {
      return null;
    }
    List<Variable> outValVars;
    outValVars = new ArrayList<Variable>(localOutputs.size());
    // Need to create output value variables
    for (Variable v : localOutputs) {
      Variable valOut = block.declareVariable(
          Types.derefResultType(v.getType()),
          OptUtil.optVPrefix(v),
          VariableStorage.LOCAL, DefType.LOCAL_COMPILER, null);
      outValVars.add(valOut);
    }
    return outValVars;
  }
  

  public static void fixupImmChange(Block block, MakeImmChange change,
          List<Instruction> instBuffer, List<Variable> newOutVars,
                                        List<Variable> oldOutVars) {
    instBuffer.add(change.newInst);
    // System.err.println("Swapped " + inst + " for " + change.newInst);
    if (!change.isOutVarSame()) {
      // Output variable of instruction changed, need to fix up
      Variable newOut = change.newOut;
      Variable oldOut = change.oldOut;
      
      OptUtil.replaceInstructionOutputVar(block, instBuffer, newOut, oldOut);
    }

    // Now copy back values into future
    if (newOutVars != null) {
      for (int i = 0; i < newOutVars.size(); i++) {
        instBuffer.add(ICInstructions.futureSet(oldOutVars.get(i),
            Oparg.createVar(newOutVars.get(i))));
      }
    }
  }
  
  /**
   * Union of Instruction and Continuation, useful in some cases
   */
  public static class InstOrCont {
    public static enum InstOrContType {
      INSTRUCTION,
      CONTINUATION,
    }
    public InstOrCont(Instruction i) {
      this.inst = i;
      this.cont = null;
      this._type = InstOrContType.INSTRUCTION;
    }
    public InstOrCont(Continuation c) {
      this.inst = null;
      this.cont = c;
      this._type = InstOrContType.CONTINUATION;
    }
    
    private final Instruction inst;
    private final Continuation cont;
    private final InstOrContType _type;
    
    public InstOrContType type() {
      return _type;
    }
    
    public Instruction instruction() {
      if (_type != InstOrContType.INSTRUCTION) {
        throw new STCRuntimeError("InstOrCont not an " +
        		"instruction, was: " + _type); 
            
      }
      return inst;
    }
    
    public Continuation continuation() {
      if (_type != InstOrContType.CONTINUATION) {
        throw new STCRuntimeError("InstOrCont not an " +
            "continuation, was: " + _type); 
            
      }
      return cont;
    }
    
    public String toString() {
      switch (_type) {
        case CONTINUATION:
          StringBuilder sb = new StringBuilder();
          this.cont.prettyPrint(sb, "     ");
          return "Continuation: " + sb.toString();
        case INSTRUCTION:
          assert(inst != null);
          return "Instruction: " + this.inst.toString();
        default:
          throw new STCRuntimeError("invalid tag " + _type);
      }
    }
  }
}


