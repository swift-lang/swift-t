package exm.stc.ic.opt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;

/**
 * Reorder instructions within a block to try and maximize chances for 
 * successful further optimization.
 * 
 * E.g. if instruction A reads a dataflow variable and instruction B writes it, we
 *    should move A to after B
 */
public class ReorderInstructions extends FunctionOptimizerPass {

  private static int move = 0;
  private static int noMove = 0;
  
  @Override
  public String getPassName() {
    return "Reorder instructions";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_REORDER_INSTS;
  }

  @Override
  public void optimize(Logger logger, Function f) throws UserException {
    optimizeRec(logger, f, f.getMainblock());
  }

  private void optimizeRec(Logger logger, Function f, Block block) {
    reorderInBlock(logger, block);
    
    for (Continuation c: block.getContinuations()) {
      for (Block inner: c.getBlocks()) {
        optimizeRec(logger, f, inner);
      }
    }
  }
  
  /**
   * Try reordering instructions
   * @param logger
   * @param block
   */
  private void reorderInBlock(Logger logger, Block block) {
    logger.trace("tryReorder");
    
    // Copy into an ArrayList for efficient indexing
    ArrayList<Instruction> instructionsCopy =
              new ArrayList<Instruction>(block.getInstructions());
    
    // Accumulate instructions into this array
    ArrayList<Instruction> newInstructions = new ArrayList<Instruction>();
    // Keep track of which instructions should go after (x -> goes after y)
    MultiMap<Instruction, Instruction> after = new MultiMap<Instruction, Instruction>();
    boolean moved = false;
    
    for (int i = 0; i < instructionsCopy.size(); i++) {
      Instruction inst1 = instructionsCopy.get(i);
      boolean move = 
          searchForInputWriter(logger, instructionsCopy, i, inst1, after);
      if (move) {
        // Note that we should put it later
        moved = true;
      } else {
        // Don't move
        newInstructions.add(inst1);
      }
    }
    

    if (moved) {
      rebuildInstructions(block, newInstructions, after);
      block.replaceInstructions(newInstructions);
      move++;
    } else {
      noMove++;
    }
    if (logger.isTraceEnabled())
      logger.trace("reorder instructions: moved " + move + "/" + (move + noMove));
  }

  private void rebuildInstructions(Block block,
      ArrayList<Instruction> newInstructions,
      MultiMap<Instruction, Instruction> after) {
    // Put all instructions back.  We do a topological sort to
    // make sure they end up in correct order
    ArrayDeque<Instruction> stack = new ArrayDeque<Instruction>();
    HashSet<Instruction> visited = new HashSet<Instruction>();
    visited.addAll(newInstructions);
    while (!after.isEmpty()) {
      for (Entry<Instruction, List<Instruction>> e: after.entrySet()) {
        Iterator<Instruction> it = e.getValue().iterator();
        while (it.hasNext()) {
          Instruction inst = it.next();
          // Find instruction with nothing dependent on it
          if (visited.contains(inst)) {
            // Already added
            it.remove();
          } else if (after.get(inst).isEmpty()) {
            stack.push(inst);
            visited.add(inst);
            it.remove();
          }
        }
      }
    } 
    
    
    while (!stack.isEmpty()) {
      newInstructions.add(stack.pop());
    }
    assert(newInstructions.size() == block.getInstructions().size());
  }

  private boolean searchForInputWriter(Logger logger,
          ArrayList<Instruction> instructionsCopy, int i,
          Instruction inst1, MultiMap<Instruction, Instruction> after) {
    List<Var> inst1Inputs = getInputs(inst1);

    if (logger.isTraceEnabled())
      logger.trace("Try to move " + inst1 + " with inputs " + inst1Inputs);
    
    // Find last instruction that writes inputs of inst1
    // Build a DAG of dependences between instructions
    boolean move = false;
    boolean canMoveFurther = true;
    for (int j = i + 1; j < instructionsCopy.size(); j++) {
      Instruction inst2 = instructionsCopy.get(j);
      if (writesInputs(logger, inst2, getInputs(inst2), inst1)) {
        // These edges wont create cycle - forward edge
        after.put(inst1, inst2);
        canMoveFurther = false;
      }
      
      if (canMoveFurther && writesInputs(logger, inst1, inst1Inputs, inst2)) {
        // Check that there isn't a path from inst1 to inst2
        if (pathExists(after, inst1, inst2)) {
          canMoveFurther = false;
        } else {
          after.put(inst2, inst1);
          move = true;
        }
      }
    }
    return move;
  }

  private boolean pathExists(MultiMap<Instruction, Instruction> after,
      Instruction from, Instruction to) {
    ArrayDeque<Instruction> stack = new ArrayDeque<Instruction>();
    stack.push(from);
    while (!stack.isEmpty()) {
      Instruction curr = stack.pop();
      if (curr == to) {
        return true;
      }
      stack.addAll(after.get(curr));
    }
    return false;
  }

  /**
   * Find list of variables that this instruction reads
   * @param inst
   * @return
   */
  private List<Var> getInputs(Instruction inst) {
    List<Var> inst1Inputs = new ArrayList<Var>();
    for (Arg a: inst.getInputs()) {
      if (a.isVar()) {
        inst1Inputs.add(a.getVar());
      }
    }
    for (Var v: inst.getReadOutputs()) {
      inst1Inputs.add(v);
    }
    return inst1Inputs;
  }

  private boolean writesInputs(Logger logger, Instruction inst1,
          List<Var> inst1Inputs, Instruction inst2) {
    if (inst2.op == Opcode.ADDRESS_OF &&
        !inst1.getPiecewiseAssignedOutputs().isEmpty() &&
        inst1.getPiecewiseAssignedOutputs().contains(inst2.getOutput(0))) {
      // Special case for address_of: otherwise looks like they both write it
      return true;
    }
                  
    
    for (Var inst2Output: inst2.getModifiedOutputs()) {
      if (inst1Inputs.contains(inst2Output)) {
        if (logger.isTraceEnabled())
          logger.trace(inst2 + " writes " + inst1);
        
        if (Collections.disjoint(inst1.getModifiedOutputs(),
                                 inst2.getModifiedOutputs())) {
          logger.trace("Disjoint");
          // Check for circular dep
          return true;
        }
        break;
      }
    }
    return false;
  }
}
