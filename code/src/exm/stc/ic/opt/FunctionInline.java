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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Constants;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.WaitMode;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.FunctionCall;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.BuiltinFunction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GlobalConstants;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.Opcode;

public class FunctionInline implements OptimizerPass {

  private static int MAX_ITERS_PER_PASS = 10;
  
  /**
   * List of (caller, callee) pairs already inlined.
   */
  private final Set<Pair<String, String>> blacklist =
                              new HashSet<Pair<String, String>>();
  
  /**
   * Names of functions that should be inlined everywhere
   */
  private final Set<String> alwaysInline = new HashSet<String>();
  
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
           inst.op == Opcode.CALL_FOREIGN;
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
    inlineFunctions(logger, program);
  }

  private void inlineFunctions(Logger logger, Program program) {
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
    Iterator<BuiltinFunction> it = program.builtinIterator();
    while (it.hasNext()) {
      BuiltinFunction f = it.next();
      List<String> usages = finder.functionUsages.get(f.getName());
      if (usages.size() == 0 && ! ForeignFunctions.hasOpEquiv(f.getName()) &&
          !ForeignFunctions.isLocalImpl(f.getName())) {
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
    

    // Map from caller to callee for IC functions only
    Map<String, String> functionCalls = new HashMap<String, String>();
    for (Function callee: program.getFunctions()) {
      for (String caller: finder.functionUsages.get(callee.getName())) {
        functionCalls.put(caller, callee.getName());
      }
    }
    
    MultiMap<String, String> inlineCandidates = new MultiMap<String, String>();
    Set<String> toRemove = new HashSet<String>();
    // Narrow inline candidates by number of calls, remove unused functions
    for (Function f: program.getFunctions()) {
      List<String> callLocs = finder.functionUsages.get(f.getName());
      int functionSize = finder.getFunctionSize(f);
      if (f.getName().equals(Constants.MAIN_FUNCTION)) {
        // Do nothing
      } else if (callLocs == null || callLocs.size() == 0) {
        // Function not referenced - prune it!
        toRemove.add(f.getName());
      } else if (callLocs.size() == 1 && !callLocs.get(0).equals(f.getName())) {
        // Always inline functions that were only called once
        alwaysInline.add(f.getName());
        inlineCandidates.putAll(f.getName(), callLocs);
      } else if (callLocs.size() * functionSize  <= inlineThreshold) {
        inlineCandidates.putAll(f.getName(), callLocs);
        if (!functionCalls.containsKey(f.getName())) {
          // Doesn't call other functions, safe to inline always
          alwaysInline.add(f.getName());
        }
      }
    }

    inlineCandidates = findCycleFree(inlineCandidates, toRemove);
    
    return Pair.create(inlineCandidates, toRemove);
  }

  private MultiMap<String, String> findCycleFree(
          MultiMap<String, String> inlineCandidates, Set<String> toRemove) {
    MultiMap<String, String> inlineCandidates2 = new MultiMap<String, String>();
    // remove any loops in inlining
    Set<String> visited = new HashSet<String>();
    // Start from alwaysInline functions so that they aren't the bit we have
    // to break in circular loop
    for (String toInline: alwaysInline) {
      findCycleFreeRec(inlineCandidates, visited, toRemove,
              inlineCandidates2, new StackLite<String>(), toInline);
    }
    // Now process remaining functions
    for (String toInline: inlineCandidates.keySet()) {
      findCycleFreeRec(inlineCandidates, visited, toRemove,
                      inlineCandidates2, new StackLite<String>(), toInline);
    }
    return inlineCandidates2;
  }

  /**
   */
  private void findCycleFreeRec(MultiMap<String, String> candidates,
      Set<String> visited, Set<String> toRemove,
      MultiMap<String, String> newCandidates, StackLite<String> callStack,
      String curr) {
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
        if (alwaysInline.contains(curr)) {
          Logging.getSTCLogger().warn("Recursive loop of functions with no "
                  + " other callers: " + curr + " " + callStack);
        }
      } else if (blacklist.contains(Pair.create(caller, curr))) {
        // Already inlined, don't do it again
      } else {
        // Mark for inlining
        newCandidates.put(curr, caller);
        
        callStack.push(curr);
        findCycleFreeRec(candidates, visited, toRemove, newCandidates, callStack,
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
    Iterator<Function> functionIter = program.functionIterator();
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
        doInlining(logger, program, f, f.mainBlock(), inlineLocations,
                   toInline, alwaysInline, blacklist);
      }
    }
  }
  
  public static void inlineAllOccurrences(Logger logger, Program prog,
                                    Map<String, Function> toInline) {
    for (Function f: prog.getFunctions()) {
      inlineAllOccurrences(logger, prog, f, toInline);
    }
  }

  private static void inlineAllOccurrences(Logger logger, Program prog,
                Function fn, Map<String, Function> toInline) {
    doInlining(logger, prog, fn, fn.mainBlock(), null, toInline, toInline.keySet(),
                Collections.<Pair<String, String>>emptySet());
  }

  /**
   * 
   * @param logger
   * @param prog
   * @param contextFunction
   * @param block
   * @param inlineLocations which function to inline where, if null,
   *                        inline in all locations
   * @param alwaysInline functions to always inline
   * @param toInline
   */
  private static void doInlining(Logger logger, Program prog, Function contextFunction,
      Block block, MultiMap<String, String> inlineLocations, Map<String, Function> toInline,
      Set<String> alwaysInline, Set<Pair<String, String>> blacklist) {
    // Recurse first to avoid visiting newly inlined continuations and doing
    // extra inlining (required to avoid infinite loops of inlining with 
    // recursive functions)
    for (Continuation c: block.getContinuations()) {
      for (Block cb: c.getBlocks()) {
        doInlining(logger, prog, contextFunction, cb, inlineLocations,
                   toInline, alwaysInline, blacklist);
      }
    }
    
    ListIterator<Statement> it = block.statementIterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      switch (stmt.type()) {
        case INSTRUCTION: {
          Instruction inst = stmt.instruction();;
          if (isFunctionCall(inst)) {
            FunctionCall fcall = (FunctionCall)inst;
            tryInline(logger, prog, contextFunction, block, inlineLocations,
                      toInline, alwaysInline, blacklist, it, fcall);
          }
          break;
        }
        case CONDITIONAL: {
          // TODO: is it possible here to have inlining loop?
          Conditional cnd = stmt.conditional();
          for (Block cb: cnd.getBlocks()) {
            doInlining(logger, prog, contextFunction, cb, inlineLocations,
                       toInline, alwaysInline, blacklist);
          }
          break;
        }
        default:
          throw new STCRuntimeError("Unknown Statemen type " + stmt);
      }
    }
  }

  private static void tryInline(Logger logger, Program prog,
      Function contextFunction, Block block,
      MultiMap<String, String> inlineLocations, Map<String, Function> toInline,
      Set<String> alwaysInline, Set<Pair<String, String>> blacklist,
      ListIterator<Statement> it, FunctionCall fcall) {
    if (toInline.containsKey(fcall.functionName()) ||
            alwaysInline.contains(fcall.functionName())) {
      boolean canInlineHere;
      if (inlineLocations == null) {
        canInlineHere = true;
      } else {
        // Check that location is marked for inlining
        List<String> inlineCallers = inlineLocations.get(fcall.functionName());
        canInlineHere = inlineCallers.contains(contextFunction.getName());
      }
      if (canInlineHere) {
        // Do the inlining.  Note that the iterator will be positioned
        // after any newly inlined instructions.
        inlineCall(logger, prog, contextFunction, block, it, fcall,
                   toInline.get(fcall.functionName()),
                   alwaysInline, blacklist);
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
  private static void inlineCall(Logger logger, Program prog,
      Function contextFunction, Block block,
      ListIterator<Statement> it, FunctionCall fnCall,
      Function toInline,
      Set<String> alwaysInline, Set<Pair<String, String>> blacklist) {
    // Remove function call instruction
    it.remove();
    
    logger.debug("inlining " + toInline.getName() + " into " + contextFunction.getName());
    
    // Create copy of function code so variables can be renamed 
    Block inlineBlock = toInline.mainBlock().clone(BlockType.NESTED_BLOCK,
                                                      null, null);
    
    // rename function arguments
    Map<Var, Arg> renames = new HashMap<Var, Arg>();
    List<Var> passIn = new ArrayList<Var>();
    
    assert(fnCall.getFunctionOutputs().size() == toInline.getOutputList().size());
    assert(fnCall.getFunctionInputs().size() == toInline.getInputList().size()) :
           fnCall.getFunctionInputs() + " != " + toInline.getInputList() 
             + " for " + fnCall.functionName();
    for (int i = 0; i < fnCall.getFunctionInputs().size(); i++) {
      Arg inputVal = fnCall.getFunctionInput(i);
      Var inArg = toInline.getInputList().get(i);
      renames.put(inArg, inputVal);
      if (inputVal.isVar()) {
        passIn.add(inputVal.getVar());
      }
      // Remove cleanup actions
      inlineBlock.removeCleanups(inArg);
    }
    for (int i = 0; i < fnCall.getFunctionOutputs().size(); i++) {
      Var outVar = fnCall.getFunctionOutput(i);
      Var outArg = toInline.getOutputList().get(i);
      renames.put(outArg, Arg.createVar(outVar));
      passIn.add(outVar);

      // Remove cleanup actions
      inlineBlock.removeCleanups(outArg);
    }
    
    Block insertBlock;
    ListIterator<Statement> insertPos;
    
    // rename vars
    chooseUniqueNames(logger, prog.constants(), contextFunction, inlineBlock,
                      renames);
    
    if (logger.isTraceEnabled())
        logger.trace("inlining renames: " + renames);
    inlineBlock.renameVars(contextFunction.getName(), renames, 
                           RenameMode.REPLACE_VAR, true);
    
    if (fnCall.getMode() == TaskMode.SYNC) {
      insertBlock = block;
      insertPos = it;
    } else {
      // In some cases its beneficial to use TASK_DISPATCH to distribute work
      WaitMode waitMode = ProgressOpcodes.isCheap(inlineBlock) ?
                          WaitMode.WAIT_ONLY : WaitMode.TASK_DISPATCH;

      // Find which args are blocking in caller
      List<WaitVar> blockingInputs = new ArrayList<WaitVar>();
      List<WaitVar> blockingFormalArgs = toInline.blockingInputs();
      for (int i = 0; i < toInline.getInputList().size(); i++) {
        Var formalArg = toInline.getInputList().get(i);
        WaitVar blockingFormalArg = WaitVar.find(blockingFormalArgs, formalArg);
        if (blockingFormalArg != null) {
          Arg input = fnCall.getFunctionInputs().get(i);
          if (input.isVar()) {
            blockingInputs.add(new WaitVar(input.getVar(),
                                blockingFormalArg.explicit));
          }
        }
      }
      
      WaitStatement wait = new WaitStatement(
          contextFunction.getName() + "-" + toInline.getName() + "-call",
          blockingInputs, PassedVar.NONE, Var.NONE,
          waitMode, false, fnCall.getMode(), fnCall.getTaskProps());
      block.addContinuation(wait);
      insertBlock = wait.getBlock();
      insertPos = insertBlock.statementIterator();
    }
    
    // Do the insertion
    insertBlock.insertInline(inlineBlock, insertPos);
    logger.debug("Call to function " + fnCall.functionName() +
          " inlined into " + contextFunction.getName());
    
    // Prevent repeated inlinings
    if (!alwaysInline.contains(fnCall.functionName())) {
      blacklist.add(Pair.create(contextFunction.getName(),
                              fnCall.functionName())); 
    }
  }

  /**
   * Set up renames for local variables in inline block
   * @param prog program
   * @param targetFunction function block being inlined into
   * @param inlineBlock block to be inlined
   * @param replacements updated with new renames
   */
  private static void chooseUniqueNames(Logger logger,
      GlobalConstants constants,
      Function targetFunction, Block inlineBlock,
      Map<Var, Arg> replacements) {
    Set<String> excludedNames = new HashSet<String>();
    for (Var globalConst: constants.vars()) {
      excludedNames.add(globalConst.name());
    }
    StackLite<Block> blocks = new StackLite<Block>();
    blocks.add(inlineBlock);
    // Walk block to find local vars
    while(!blocks.isEmpty()) {
      Block block = blocks.pop();
      for (Var v: block.getVariables()) {
        if (v.defType() != DefType.GLOBAL_CONST) {
          updateName(logger, block, targetFunction, replacements, excludedNames, v);
        }
      }
      for (Continuation c: block.allComplexStatements()) {
        for (Var cv: c.constructDefinedVars()) {
          updateName(logger, block, targetFunction, replacements, excludedNames, cv);
        }
        for (Block inner: c.getBlocks()) {
          blocks.push(inner);
        }
      }
    }
  }

  private static void updateName(Logger logger, Block block,
          Function targetFunction, Map<Var, Arg> replacements,
          Set<String> excludedNames, Var var) {
    // Choose unique name (including new names for this block)
    String newName = targetFunction.mainBlock().uniqueVarName(
                                        var.name(), excludedNames);
    Var newVar = var.makeRenamed(newName);
    assert(!replacements.containsKey(newName));
    replacements.put(var, Arg.createVar(newVar));
    excludedNames.add(newName);
    UniqueVarNames.replaceCleanup(block, var, newVar);
    logger.trace("Replace " + var + " with " + newVar
            + " for inline into function " + targetFunction.getName());
  }

  private static class FuncCallFinder extends TreeWalker {

    /**
     * Map of called function -> name of function in which call occurred.
     * Context function may occur multiple times in the list
     */
    MultiMap<String, String> functionUsages = new MultiMap<String, String>();
    
    
    /**
     * Function sizes in instructions
     */
    private Map<String, int[]> functionSizes = new HashMap<String, int[]>();

    @Override
    public void visit(Logger logger, Function functionContext,
                                      Instruction inst) {
      if (isFunctionCall(inst)) {
        String calledFunction = ((FunctionCall)inst).functionName();
        functionUsages.put(calledFunction, functionContext.getName());
      }
      
      int prev[] = functionSizes.get(functionContext.getName());
      if (prev == null) {
        functionSizes.put(functionContext.getName(), new int[] {1}); 
      } else {
        prev[0] = prev[0] + 1;
      }
    }
    
    public int getFunctionSize(Function function) {
      int size[] = functionSizes.get(function.getName());
      if (size == null) {
        return 0;
      } else {
        return size[0];
      }
    }
    
  }
}
