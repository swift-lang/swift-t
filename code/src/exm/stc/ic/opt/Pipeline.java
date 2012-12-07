package exm.stc.ic.opt;

import org.apache.log4j.Logger;

import exm.stc.common.lang.ExecContext;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

/**
 * Compile-time pipelining optimization where we merge sequentially dependent
 * tasks.  This reduces scheduling/task dispatch/load balancing overhead, and
 * also eliminates the need to move intermediate data.
 * 
 * This is a pass that should be run once near end of optimization.
 * 
 * Running it multiple times can result in reduction in parallelism 
 */
public class Pipeline {

  public static void pipelineTasks(Logger logger, Program prog) {
    for (Function f: prog.getFunctions()) {
      pipelineTasks(logger, f, f.getMainblock(), ExecContext.CONTROL);
    }
  }

  private static void pipelineTasks(Logger logger, Function f, Block curr,
      ExecContext cx) {
    // TODO Auto-generated method stub
    
  }
  /*
   *
   * 
   * Pseudocode:
   * We do a bottom-up tree walk
   * 
   * doPipeline(Block curr, ExecContext cx):
   *   foreach cont in curr.continuations:
   *     newContext = childContext(cont, cx)
   *     foreach block in cont.blocks:
   *       doPipeline(block, newContext)
   *   
   *   // Find candidates for merging
   *   candidates = [ cont in curr.continuations
   *                  where cont is wait and cont.waitVars.isEmpty 
   *                    and childContext(cont, cx) == cx ]
   *   if candidates.isEmpty
   *     return
   *   
   *   // Inline the candidate that will result in the biggest reduction
   *   // in overhead of data transfer/task dispatch
   *   bestCand = candidates[0]
   *   bestScore = heuristicCost(candidates[0])
   *   for cand in candidates[1:]:
   *     score = heuristicCost(curr, cand)
   *     if score < bestScore
   *       bestScore = score
   *       bestCand = cand
   *    
   *   bestCand.inlineInto(curr)  
   *
   * }
   * 
   * TODO: incorporate more info about reads collected from bottom-up treewalk 
   * heuristicCost(block, wait) {
   *   return sum([costOfPassing(var) for var in cont.usedVars if canSavePassing(block, var, wait)])
   *   //Incorporate cost of task dispatch?
   *   // Meaning of read?  Just read in immediate child? 
   * }
   * 
   * canSavePassing(block, var, wait) {
   *   if (var is read in wait.block)
   *     return true;
   *   else
   *     return false
   * }
   * 
   * costOfPassing(var) {
   *   if var is value:
   *     return 0
   *   if var is blob:
   *     return 5
   *   if var is file:
   *     return 20;
   *   if var is scalar future:
   *     return 1
   *   if var is struct:
   *     return sum(costOfPassing(structElements))
   *   if var is array:
   *     return 1
   * }
   */
}
