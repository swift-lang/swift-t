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
import exm.stc.common.lang.Builtins;
import exm.stc.common.lang.Constants;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.FunctionCall;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.BuiltinFunction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

public class FunctionInline implements OptimizerPass {

  private static int MAX_ITERS_PER_PASS = 10;
  
  /**
   * List of (caller, callee) pairs already inlined.
   */
  private final Set<Pair<String, String>> blacklist =
                              new HashSet<Pair<String, String>>();
  
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
           inst.op == Opcode.CALL_SYNC || inst.op == Opcode.CALL_LOCAL_CONTROL ||
           inst.op == Opcode.CALL_BUILTIN;
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
    int i = 0;
    do {
      FuncCallFinder finder = new FuncCallFinder();
      TreeWalk.walk(logger, program, finder);
      
      pruneBuiltins(logger, program, finder);
      
      Pair<MultiMap<String, String>, Set<String>> actions =
             selectInlineFunctions( program, finder);
      MultiMap<String, String> inlineLocations = actions.val1;
      Set<String> toRemove = actions.val2;

      logger.debug("Inline locs: " + inlineLocations.toString());
      logger.debug("Functions to prune: " + toRemove.toString());
      
      changed = doInlining(logger, program, inlineLocations, toRemove);
      logger.debug("changed=" + changed);
      i++;
    } while (changed && i < MAX_ITERS_PER_PASS);
  }

  private void pruneBuiltins(Logger logger, Program program,
      FuncCallFinder finder) {
    ListIterator<BuiltinFunction> it = program.builtinIterator();
    while (it.hasNext()) {
      BuiltinFunction f = it.next();
      List<String> usages = finder.functionUsages.get(f.getName());
      if (usages.size() == 0 && ! Builtins.hasOpEquiv(f.getName())) {
        logger.debug("Prune builtin: " + f.getName());
        it.remove();
      }
    }
  }

  /**
   * Choose which functions will be removed totally (and remove them now)
   * and calls to which function from where will be inlined.
   * Removes cycles from inlining graph
   * @param program
   * @param finder
   * @return Map of function -> caller functions determining which calls 
   *        to inline
   */
  private Pair<MultiMap<String, String>, Set<String>> selectInlineFunctions(
      Program program, FuncCallFinder finder) {
    MultiMap<String, String> inlineCandidates = new MultiMap<String, String>();
    Set<String> toRemove = new HashSet<String>();
    // Narrow inline candidates by number of calls, remove unused functions
    for (Function f: program.getFunctions()) {
      List<String> callLocs = finder.functionUsages.get(f.getName());
      if (f.getName().equals(Constants.MAIN_FUNCTION)) {
        // Do nothing
      } else if (callLocs == null || callLocs.size() == 0) {
        // Function not referenced - prune it!
        toRemove.add(f.getName());
      } else if (callLocs.size() <= inlineThreshold) {
        inlineCandidates.putAll(f.getName(), callLocs);
      }
    }

    MultiMap<String, String> inlineCandidates2 = new MultiMap<String, String>();
    // remove any loops in inlining
    Set<String> visited = new HashSet<String>();
    for (String toInline: inlineCandidates.keySet()) {
      findCycleFree(inlineCandidates, visited, toRemove,
                      inlineCandidates2, new ArrayDeque<String>(), toInline);
    }
    
    return Pair.create(inlineCandidates2, toRemove);
  }

  /**
   */
  private void findCycleFree(MultiMap<String, String> candidates,
      Set<String> visited, Set<String> toRemove,
      MultiMap<String, String> newCandidates, Deque<String> callStack,
      String curr) {
    if (blacklist.contains(curr))
      return;  // blacklisted
    
    List<String> callers = candidates.get(curr);
    if (callers == null || callers.size() == 0) {
      // not a candidate for inlining
      return;
    }
    
    if (visited.contains(curr))
      return;  // Don't process again
    visited.add(curr);
    
    for (String caller: callers) {
      if (callStack.contains(caller) || caller.equals(curr)) {
        // Adding this would create cycle, do nothing
      } else if (blacklist.contains(Pair.create(caller, curr))) {
        // Already inlined, don't do it again
      } else {
        // Mark for inlining
        newCandidates.put(curr, caller);
        
        callStack.push(curr);
        findCycleFree(candidates, visited, toRemove, newCandidates, callStack,
                        caller);
        callStack.pop();
      }
    }
  }

  private boolean doInlining(Logger logger, Program program,
      MultiMap<String, String> inlineLocations, Set<String> toRemove) {
    boolean changed = false;
    // Functions that will be inlined
    Map<String, Function> toInline = new HashMap<String, Function>();
    // Functions where inlining must occur
    Set<String> callSiteFunctions = new HashSet<String>();
    ListIterator<Function> functionIter = program.functionIterator();
    while (functionIter.hasNext()) {
      Function f = functionIter.next();
      List<String> occurrences = inlineLocations.get(f.getName());
      if (toRemove.contains(f.getName())) {
        changed = true;
        functionIter.remove();      
      } 
      if (occurrences != null && occurrences.size() > 0) {
        changed = true;
        toInline.put(f.getName(), f);
        if (occurrences != null) {
          callSiteFunctions.addAll(occurrences);
        }
      }
    }
    
    // Now do the inlining
    if (!callSiteFunctions.isEmpty()) {
      doInlining(logger, program, callSiteFunctions, inlineLocations, toInline);
    }
    return changed;
  }

  /**
   * 
   * @param logger
   * @param callSiteFunctions Names of functions where inlining must happen
   * @param inlineLocations Only inline these calls (callee -> caller map)
   * @param toInline functions to inline
   */
  private void doInlining(Logger logger, Program program,
      Set<String> callSiteFunctions, MultiMap<String, String> inlineLocations, Map<String, Function> toInline) {
    for (Function f: program.getFunctions()) {
      if (callSiteFunctions.contains(f.getName())) {
        doInlining(logger, program, f, f.getMainblock(), inlineLocations, toInline);
      }
    }
  }

  private void doInlining(Logger logger, Program prog, Function contextFunction,
      Block block, MultiMap<String, String> inlineLocations, Map<String, Function> toInline) {
    // Recurse first to avoid visiting newly inlined continuations and doing
    // extra inlining (required to avoid infinite loops of inlining with 
    // recursive functions)
    for (Continuation c: block.getContinuations()) {
      for (Block cb: c.getBlocks()) {
        doInlining(logger, prog, contextFunction, cb, inlineLocations, toInline);
      }
    }
    
    ListIterator<Instruction> it = block.instructionIterator();
    while (it.hasNext()) {
      Instruction inst = it.next();
      if (isFunctionCall(inst)) {
        FunctionCall fcall = (FunctionCall)inst;
        if (toInline.containsKey(fcall.getFunctionName())) {
          // Check that location is marked for inlining
          List<String> inlineCallers = inlineLocations.get(fcall.getFunctionName());
          if (inlineCallers.contains(contextFunction.getName())) {
            // Do the inlining.  Note that the iterator will be positioned
            // after any newly inlined instructions.
            inlineCall(logger, prog, contextFunction, block, it, fcall,
                       toInline.get(fcall.getFunctionName()));
          }
        }
      }
    }
  }

  /**
   * Do the inlining
   * @param logger
   * @param block
   * @param it iterator positioned at function call instruction
   * @param fnCall
   * @param toInline
   */
  private void inlineCall(Logger logger, Program prog,
      Function contextFunction, Block block,
      ListIterator<Instruction> it, FunctionCall fnCall,
      Function toInline) {
    // Remove function call instruction
    it.remove();
    
    // Create copy of function code so variables can be renamed 
    Block inlineBlock = toInline.getMainblock().clone(BlockType.NESTED_BLOCK,
                                                      null, null);
    
    // rename function arguments
    Map<String, Arg> renames = new HashMap<String, Arg>();
    List<Var> passIn = new ArrayList<Var>();
    List<Var> outArrays = new ArrayList<Var>();
    
    assert(fnCall.getOutputs().size() == toInline.getOutputList().size());
    assert(fnCall.getFunctionInputs().size() == toInline.getInputList().size()) :
           fnCall.getFunctionInputs() + " != " + toInline.getInputList() 
             + " for " + fnCall.getFunctionName();
    for (int i = 0; i < fnCall.getFunctionInputs().size(); i++) {
      Var inputVal = fnCall.getFunctionInput(i);
      Var inArg = toInline.getInputList().get(i);
      renames.put(inArg.name(), Arg.createVar(inputVal));
      passIn.add(inputVal);
      // Remove cleanup actions
      inlineBlock.removeCleanups(inArg);
    }
    for (int i = 0; i < fnCall.getOutputs().size(); i++) {
      Var outVar = fnCall.getOutput(i);
      Var outArg = toInline.getOutputList().get(i);
      renames.put(outArg.name(),
                  Arg.createVar(outVar));
      passIn.add(outVar);
      if (Types.isArray(outVar.type())) {
        outArrays.add(outVar);
      }

      // Remove cleanup actions
      inlineBlock.removeCleanups(outArg);
    }
    
    // TODO: output arrays inside structs
    
    Block insertBlock;
    ListIterator<Instruction> insertPos;
    
    // rename vars
    chooseUniqueNames(prog, contextFunction, inlineBlock, renames);
    
    inlineBlock.renameVars(renames, false);
    
    if (fnCall.getMode() == TaskMode.SYNC) {
      insertBlock = block;
      insertPos = it;
    } else {
      // TODO: should be data_only sometimes.
      WaitMode waitMode = WaitMode.TASK_DISPATCH;
      WaitStatement wait = new WaitStatement(
          contextFunction.getName() + "-" + toInline.getName() + "-call",
          toInline.getBlockingInputs(), passIn, outArrays, fnCall.getPriority(),
          waitMode, false, fnCall.getMode());
      block.addContinuation(wait);
      insertBlock = wait.getBlock();
      insertPos = insertBlock.instructionIterator();
    }
    
    // Do the insertion
    insertBlock.insertInline(inlineBlock, insertPos);
    logger.debug("Call to function " + fnCall.getFunctionName() +
          " inlined into " + contextFunction.getName());
    
    // Prevent repeated inlinings
    blacklist.add(Pair.create(contextFunction.getName(),
                              fnCall.getFunctionName())); 
  }

  /**
   * Set up renames for local variables in inline block
   * @param prog program
   * @param targetFunction function block being inlined into
   * @param inlineBlock block to be inlined
   * @param replacements updated with new renames
   */
  private void chooseUniqueNames(Program prog,
      Function targetFunction, Block inlineBlock,
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
          updateName(targetFunction, replacements, excludedNames, v);
        }
      }
      for (Continuation c: block.getContinuations()) {
        List<Var> constructVars = c.constructDefinedVars();
        if (constructVars != null) {
          for (Var cv: constructVars) {
            updateName(targetFunction, replacements, excludedNames, cv);
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
