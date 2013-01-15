package exm.stc.ic.opt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Constants;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.util.MultiMap;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.FunctionCall;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

public class FunctionInline implements OptimizerPass {

  public FunctionInline() {
    try {
      inlineThreshold = Settings.getLong(Settings.OPT_FUNCTION_INLINE_THRESHOLD);
    } catch (InvalidOptionException e) {
      e.printStackTrace();
      throw new STCRuntimeError(e.getMessage());
    }
  }
  
  private static boolean isFunctionCall(Instruction inst) {
    return inst.op == Opcode.CALL_CONTROL || inst.op == Opcode.CALL_LOCAL ||
           inst.op == Opcode.CALL_SYNC || inst.op == Opcode.CALL_LOCAL_CONTROL;
  }

  /**
   * Threshold for inlining: if function called less than or equal to
   * this number of times, then inline.
   */
  public final long inlineThreshold;
  
  @Override
  public String getPassName() {
    return "Function inlining";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_FUNCTION_INLINE;
  }

  @Override
  public void optimize(Logger logger, Program program) throws UserException {
    // Do inlining repeatedly until no changes since removing a function
    // can allow more functions to be pruned;
    boolean changed;
    do {
      changed = false;
      FuncCallFinder finder = new FuncCallFinder();
      TreeWalk.walk(logger, program, finder);
      
      // Functions removed from IC tree
      Map<String, Function> removed = new HashMap<String, Function>();
      // Functions where inlining must occur
      Set<String> inlineLocations = new HashSet<String>();
      
      // Find which functions should be inlined/removed
      ListIterator<Function> functionIter = program.functionIterator();
      while (functionIter.hasNext()) {
        Function f = functionIter.next();
        if (f.getName().equals(Constants.MAIN_FUNCTION)) {
          continue;
        }
        List<String> occurrences = finder.functionUsages.get(f.getName());
        if (occurrences == null || occurrences.size() <= inlineThreshold) {
          changed = true;
          functionIter.remove();
          removed.put(f.getName(), f);
          if (occurrences != null) {
            inlineLocations.addAll(occurrences);
          }
        }
      }
      
      // Now do the inlining
      if (!inlineLocations.isEmpty()) {
        doInlining(logger, program, inlineLocations, removed);
      }
    } while (changed);
  }

  /**
   * 
   * @param logger
   * @param inlineLocations Names of functions where inlining must happen
   * @param toInline functions to inline
   */
  private void doInlining(Logger logger, Program program,
      Set<String> inlineLocations, Map<String, Function> toInline) {
    for (Function f: program.getFunctions()) {
      if (inlineLocations.contains(f.getName())) {
        doInlining(logger, program, f.getName(), f.getMainblock(), toInline);
      }
    }
  }

  private void doInlining(Logger logger, Program prog, String contextFunction,
      Block block, Map<String, Function> toInline) {
    ListIterator<Instruction> it = block.instructionIterator();
    while (it.hasNext()) {
      Instruction inst = it.next();
      if (isFunctionCall(inst)) {
        FunctionCall fcall = (FunctionCall)inst;
        if (toInline.containsKey(fcall.getFunctionName())) {
          inlineCall(logger, prog, contextFunction, block, it, fcall,
                     toInline.get(fcall.getFunctionName()));
        }
      }
    }
    for (Continuation c: block.getContinuations()) {
      for (Block cb: c.getBlocks()) {
        doInlining(logger, prog, contextFunction, cb, toInline);
      }
    }
  }

  /**
   * Do the inlining
   * @param logger
   * @param block
   * @param it iterator positioned at function call instruction
   * @param inst
   * @param function
   */
  private void inlineCall(Logger logger, Program prog,
      String contextFunction, Block block,
      ListIterator<Instruction> it, FunctionCall inst, Function function) {
    it.remove();
    
    // rename function arguments
    Map<String, Arg> renames = new HashMap<String, Arg>();
    List<Var> passIn = new ArrayList<Var>();
    List<Var> outArrays = new ArrayList<Var>();
    
    assert(inst.getOutputs().size() == function.getOutputList().size());
    assert(inst.getInputs().size() == function.getInputList().size());
    for (int i = 0; i < inst.getInputs().size(); i++) {
      Arg inputVal = inst.getInput(i);
      renames.put(function.getInputList().get(i).name(), inputVal);
      if (inputVal.isVar()) {
        passIn.add(inputVal.getVar());
      }
    }
    for (int i = 0; i < inst.getOutputs().size(); i++) {
      Var outVar = inst.getOutput(i);
      renames.put(function.getOutputList().get(i).name(),
                  Arg.createVar(outVar));
      passIn.add(outVar);
      if (Types.isArray(outVar.type())) {
        outArrays.add(outVar);
      }
    }
    
    // TODO: output arrays inside structs
    
    Block insertBlock;
    ListIterator<Instruction> insertPos;
    // Create copy of function code so variables can be renamed 
    Block inlineBlock = function.getMainblock().clone(BlockType.NESTED_BLOCK,
                                                      null, null);
    
    // rename vars
    chooseUniqueNames(prog, function, inlineBlock, renames);
    
    inlineBlock.renameVars(renames, false);
    
    if (inst.getMode() == TaskMode.SYNC) {
      insertBlock = block;
      insertPos = it;
    } else {
      // TODO: should be data_only sometimes.
      WaitMode waitMode = WaitMode.TASK_DISPATCH;
      WaitStatement wait = new WaitStatement(
          contextFunction + "-" + function.getName() + "-call",
          function.getBlockingInputs(), passIn, outArrays,
          waitMode, false, inst.getMode());
      block.addContinuation(wait);
      insertBlock = wait.getBlock();
      insertPos = insertBlock.instructionIterator();
    }
    
    // Do the insertion
    insertBlock.insertInline(inlineBlock, insertPos);
  }

  /**
   * Set up renames for local variables in inline block
   * @param inlineBlock
   * @param replacements updated with new renames
   */
  private void chooseUniqueNames(Program prog,
      Function f, Block inlineBlock,
      Map<String, Arg> replacements) {
    Set<String> excludedNames = new HashSet<String>();
    excludedNames.addAll(prog.getGlobalConsts().keySet());
    Deque<Block> blocks = new ArrayDeque<Block>();
    blocks.add(inlineBlock);
    // Walk block to find local vars
    while(!blocks.isEmpty()) {
      Block block = blocks.pop();
      for (Var v: block.getVariables()) {
        if (v.defType() != DefType.GLOBAL_CONST) {
          updateName(f, replacements, excludedNames, v);
        }
      }
      for (Continuation c: block.getContinuations()) {
        List<Var> constructVars = c.constructDefinedVars();
        if (constructVars != null) {
          for (Var cv: constructVars) {
            updateName(f, replacements, excludedNames, cv);
          }
        }
        for (Block inner: c.getBlocks()) {
          blocks.push(inner);
        }
      }
    }
  }

  private void updateName(Function f, Map<String, Arg> replacements,
          Set<String> excludedNames, Var var) {
    // Choose unique name (including new names for this block)
    String newName = f.getMainblock().uniqueVarName(var.name(), excludedNames);
    Var newVar = new Var(var.type(), newName, var.storage(), var.defType(),
                         var.mapping());
    replacements.put(var.name(), Arg.createVar(newVar));
    excludedNames.add(newName);
  }

  private static class FuncCallFinder implements TreeWalker {

    /**
     * Map of called function -> name of function in which call occurred.
     * Context function may occur multiple times in the list
     */
    MultiMap<String, String> functionUsages = new MultiMap<String, String>();
    @Override
    public void visit(Logger logger, String functionContext,
                                      Instruction inst) {
      if (isFunctionCall(inst)) {
        String calledFunction = ((FunctionCall)inst).getFunctionName();
        functionUsages.put(calledFunction, functionContext);
      }
    }
    
  }
}
