package exm.stc.ic.opt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
      
      Instruction writer = 
              searchForInputWriter(logger, instructionsCopy, i, inst1);
      if (writer != null) {
        // Note that we should put it later
        moved = true;
        after.put(writer, inst1);
      } else {
        // Don't move
        newInstructions.add(inst1);
        if (after.containsKey(inst1)) {
          newInstructions.addAll(after.remove(inst1));
        }
      }
    }
    
    // Put all instructions back.  We do a topological sort to
    // make sure they end up in correct order
    ArrayDeque<Instruction> stack = new ArrayDeque<Instruction>();
    while (!after.isEmpty()) {
      for (Entry<Instruction, List<Instruction>> e: after.entrySet()) {
        Iterator<Instruction> it = e.getValue().iterator();
        while (it.hasNext()) {
          Instruction inst = it.next();
          // Find instruction with nothing dependent on it
          if (after.get(inst).isEmpty()) {
            stack.push(inst);
            it.remove();
          }
        }
      }
    } 
    
    
    if (moved) {
      while (!stack.isEmpty()) {
        newInstructions.add(stack.pop());
      }
      assert(newInstructions.size() == block.getInstructions().size());
      block.replaceInstructions(newInstructions);
      move++;
    } else {
      noMove++;
    }
    if (logger.isTraceEnabled())
      logger.trace("reorder instructions: moved " + move + "/" + (move + noMove));
  }

  private Instruction searchForInputWriter(Logger logger,
          ArrayList<Instruction> instructionsCopy, int i,
          Instruction inst1) {
    List<Var> inst1Inputs = getInputs(inst1);

    if (logger.isTraceEnabled())
      logger.trace("Try to move " + inst1 + " with inputs " + inst1Inputs);
    
    // Find last instruction that writes inputs of inst1
    Instruction inputWriter = null;
    for (int j = i + 1; j < instructionsCopy.size(); j++) {
      Instruction inst2 = instructionsCopy.get(j);
      if (writesInputs(logger, inst2, getInputs(inst2), inst1)) {
        // Bail out- can't move before
        return inputWriter;
      }
      if (writesInputs(logger, inst1, inst1Inputs, inst2)) {
        inputWriter = inst2;
      }
    }
    return inputWriter;
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
