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
 * Copyright [yyyy] [name of copyright owner]
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
 * limitations under the License..
 */
package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.ic.WrapUtil;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.TurbineOp;
import exm.stc.ic.tree.ICTree.Block;

public class OptUtil {

  /**
   * Generate optimiser variable name guaranteed to be unique
   * @param v
   * @return
   */
  public static String optVPrefix(Block b, Var v) {
    return b.uniqueVarName(Var.OPT_VALUE_VAR_PREFIX + v.name());
  }
  
  /**
   * Same as fetchValue of, but more times
   * @param block
   * @param instBuffer
   * @param vars
   * @return
   */
  public static List<Arg> fetchValuesOf(Block block, List<Instruction> instBuffer,
          List<Var> vars) {
    List<Arg> inVals = new ArrayList<Arg>(vars.size());

    for (Var v: vars) {
      String name = optVPrefix(block, v);
      Var valueV = WrapUtil.fetchValueOf(block, instBuffer, v, name);
      Arg value = Arg.createVar(valueV);
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
          List<Instruction> instBuffer, Var newOut, Var oldOut) {
    block.declareVariable(newOut);
    if (Types.isRefTo(oldOut.type(),
        newOut.type())) {
      if (oldOut.storage() == VarStorage.ALIAS) {
        // Will need to initialise variable in this scope as before we
        // were relying on instruction to initialise it
        
        Var replacement = new Var(oldOut.type(),
            oldOut.name(), VarStorage.TEMP,
            oldOut.defType(), oldOut.mapping());
        block.replaceVarDeclaration(oldOut, replacement);
      }
      instBuffer.add(TurbineOp.addressOf(oldOut, newOut));
    } else {
      throw new STCRuntimeError("Tried to replace instruction"
          + " output var " + oldOut + " with " + newOut + ": this doesn't make sense"
          + " to optimizer");
    }
  }
  
  public static List<Var> declareLocalOpOutputVars(Block block,
          List<Var> localOutputs) {
    if (localOutputs == null) {
      return null;
    }
    List<Var> outValVars;
    outValVars = new ArrayList<Var>(localOutputs.size());
    // Need to create output value variables
    for (Var v : localOutputs) {
      String name = optVPrefix(block, v);
      outValVars.add(WrapUtil.declareLocalOutputVar(block, v, name));
    }
    return outValVars;
  }  

  public static void fixupImmChange(Block block, MakeImmChange change,
          List<Instruction> instBuffer, List<Var> newOutVars,
                                        List<Var> oldOutVars) {
    instBuffer.add(change.newInst);
    // System.err.println("Swapped " + inst + " for " + change.newInst);
    if (!change.isOutVarSame()) {
      // Output variable of instruction changed, need to fix up
      Var newOut = change.newOut;
      Var oldOut = change.oldOut;
      
      OptUtil.replaceInstructionOutputVar(block, instBuffer, newOut, oldOut);
    }

    // Now copy back values into future
    if (newOutVars != null) {
      for (int i = 0; i < newOutVars.size(); i++) {
        Var oldOut = oldOutVars.get(i);
        Var newOut = newOutVars.get(i);
        instBuffer.add(ICInstructions.futureSet(oldOut, Arg.createVar(newOut)));
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

  public static Var fetchValueOf(Block block, List<Instruction> instBuffer,
      Var var) {
    return WrapUtil.fetchValueOf(block, instBuffer, var,
                               OptUtil.optVPrefix(block, var));
  }
}


