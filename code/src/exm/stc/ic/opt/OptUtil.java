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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.ic.WrapUtil;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.VarCreator;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.TurbineOp;

public class OptUtil {

  /**
   * Generate optimiser variable name guaranteed to be unique
   * @param v
   * @return
   */
  public static String optVPrefix(Block b, Var v) {
     return optVPrefix(b, v.name());
  }
  
  public static String optVPrefix(Block b, String name) {
    return b.uniqueVarName(Var.joinPrefix(Var.VALUEOF_VAR_PREFIX, name));
  }
  
  public static String optFilenamePrefix(Block b, Var v) {
    return optFilenamePrefix(b, v.name());
  }
  
  public static String optFilenamePrefix(Block b, String name) {
    return b.uniqueVarName(Var.joinPrefix(Var.OPT_FILENAME_PREFIX, name));
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

  public static List<Arg> fetchValuesOf(Block block, List<Var> vars) {
    List<Instruction> instBuffer = new ArrayList<Instruction>();
    List<Arg> vals = fetchValuesOf(block, instBuffer, vars);
    block.addInstructions(instBuffer);
    return vals;
  }

  /**
   * Do the manipulation necessary to allow an old instruction
   * output variable to be replaced with a new one. Assume that
   * newOut is a value type of oldOut
   * @param targetBlock 
   * @param instBuffer append any fixup instructions here
   * @param newOut
   * @param oldOut
   */
  public static void replaceInstructionOutputVar(Block block,
          Block targetBlock, List<Instruction> instBuffer, Var newOut, Var oldOut) {
            replaceInstOutput(block, targetBlock, instBuffer, newOut, oldOut);
          }

  /**
   * Do the manipulation necessary to allow an old instruction
   * output variable to be replaced with a new one. Assume that
   * newOut is a value type of oldOut
   * @param srcBlock source block for instruction 
   * @param targetBlock target block for instruction
   * @param instBuffer append any fixup instructions here
   * @param newOut
   * @param oldOut
   */
  public static void replaceInstOutput(Block srcBlock,
          Block targetBlock, List<Instruction> instBuffer, Var newOut, Var oldOut) {
    boolean isDerefResult = 
        Types.derefResultType(oldOut).assignableTo(newOut.type());
    if (isDerefResult) {
      Var oldOutReplacement;
      if (oldOut.storage() == Alloc.ALIAS) {
        // Will need to initialise variable in this scope as before we
        // were relying on instruction to initialise it
        
        oldOutReplacement = new Var(oldOut.type(),
            oldOut.name(), Alloc.TEMP,
            oldOut.defType(), oldOut.provenance(), oldOut.mapping());
        
        // Replace variable in block and in buffered instructions
        replaceVarDeclaration(srcBlock, oldOut, oldOutReplacement);
        
        Map<Var, Arg> renames = Collections.singletonMap(
                                oldOut, Arg.createVar(oldOutReplacement));
        for (Instruction inst: instBuffer) {
          inst.renameVars(renames, RenameMode.REPLACE_VAR);
        }
      } else {
        oldOutReplacement = oldOut;
      }

      instBuffer.add(TurbineOp.storeAny(oldOutReplacement, newOut.asArg()));
    } else {
      throw new STCRuntimeError("Tried to replace instruction"
          + " output var " + oldOut + " with " + newOut + ": this doesn't make sense"
          + " to optimizer");
    }
  }
  
  /**
   * Replace variable declaration in block or one of parents
   * @param block
   * @param oldOut
   * @param refVar
   */
  private static void replaceVarDeclaration(Block block, Var oldVar,
      Var newVar) {
    assert(oldVar.type().equals(newVar.type()));
    assert(oldVar.name().equals(newVar.name()));
    Block curr = block;
    while (true) {
      if (curr.replaceVarDeclaration(oldVar, newVar)) {
        // Success
        return;
      }
      if (curr.getType() == BlockType.MAIN_BLOCK) {
        throw new STCRuntimeError("Could not find definition of " + oldVar);
      } else {
        Continuation cont = block.getParentCont();
        assert(!cont.constructDefinedVars().contains(oldVar)) :
            "can't replace construct defined vars";
        curr = cont.parent();
      }
    }
  }

  public static List<Var> createLocalOpOutputVars(Block block,
          ListIterator<Statement> insertPos,
          List<Var> outputFutures, Map<Var, Var> outputFilenames,
          boolean mapOutVars) {
    if (outputFutures == null) {
      return Collections.emptyList();
    }
    
    List<Instruction> instBuffer = new ArrayList<Instruction>();
    
    List<Var> outValVars = WrapUtil.createLocalOpOutputs(block, outputFutures,
                               outputFilenames, instBuffer, true, mapOutVars);
    
    for (Instruction inst: instBuffer) {
      insertPos.add(inst);
    }

    return outValVars;
  }  

  public static void fixupImmChange(Block srcBlock,
          Block targetBlock, Instruction oldInst,
          MakeImmChange change,
          List<Instruction> instBuffer, 
          List<Var> newOutVars, List<Var> oldOutVars,
          boolean mapOutputFiles) {
    instBuffer.addAll(Arrays.asList(change.newInsts));

    Logger logger = Logging.getSTCLogger();
    if (logger.isTraceEnabled()) {
      logger.trace("Swapped " + oldInst + " for " +
                   Arrays.asList(change.newInsts));
    }
    
    if (!change.isOutVarSame()) {
      // Output variable of instruction changed, need to fix up
      Var newOut = change.newOut;
      Var oldOut = change.oldOut;
      
      replaceInstOutput(srcBlock, targetBlock, instBuffer,
                                  newOut, oldOut);
    }

    // Now copy back values into future
    if (change.storeOutputVals) {
      WrapUtil.setLocalOpOutputs(targetBlock, oldOutVars, newOutVars,
                                 instBuffer, !mapOutputFiles);
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

  public static Var fetchForLocalOp(Block block, List<Instruction> instBuffer,
      Var var) {
    return WrapUtil.fetchValueOf(block, instBuffer, var,
                             OptUtil.optVPrefix(block, var));
  }

  public static class OptVarCreator implements VarCreator {
    public OptVarCreator(Block block) {
      this.block = block;
    }

    private final Block block;
    
    @Override
    public Var createDerefTmp(Var toDeref) {
      return OptUtil.createDerefTmp(block, toDeref);
    }
    
  }
  
  /**
   * Create dereferenced variable given a reference
   */
  public static Var createDerefTmp(Block block, Var toDeref) {
    Alloc storage;
    if (Types.isRef(toDeref.type())) {
      storage = Alloc.ALIAS;
    } else {
      storage = Alloc.LOCAL;
    }
    
    Type resType = Types.derefResultType(toDeref);
    String name = block.uniqueVarName(
        Var.joinPrefix(Var.VALUEOF_VAR_PREFIX, toDeref.name()));
    Var res = new Var(resType, name,
        storage, DefType.LOCAL_COMPILER, 
        VarProvenance.valueOf(toDeref));

    block.addVariable(res);
    return res;
  }
}