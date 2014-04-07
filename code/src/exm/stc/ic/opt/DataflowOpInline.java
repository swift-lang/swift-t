package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.TaskProp.TaskPropKey;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.Pair;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.OptUtil.OptVarCreator;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.Fetched;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmRequest;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;


/**
 * Try to expand dataflow operations into simpler forms.
 */
public class DataflowOpInline extends FunctionOptimizerPass {

  @Override
  public String getPassName() {
    return "Inline dataflow ops";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_DATAFLOW_OP_INLINE;
  }

  @Override
  public void optimize(Logger logger, Function f) throws UserException {
    HierarchicalSet<Var> waitedFor = new HierarchicalSet<Var>();
    waitedFor.addAll(WaitVar.asVarList(f.blockingInputs()));
    
    inlineOpsRec(logger, f, ExecContext.CONTROL, f.mainBlock(), waitedFor);
  }
  

  private static boolean inlineOpsRec(Logger logger, Function fn,
        ExecContext currExecCx, Block block, HierarchicalSet<Var> waitedFor) {
    boolean changed = inlineOps(logger, fn, currExecCx, block, waitedFor);
    
    for (Continuation c: block.allComplexStatements()) {
      ExecContext newExecCx = c.childContext(currExecCx);
      HierarchicalSet<Var> newWaitedFor = waitedFor.makeChild();
      for (BlockingVar v: c.blockingVars(true)) {
        newWaitedFor.add(v.var);
      }
      
      for (Block childB: c.getBlocks()) {
        if (inlineOpsRec(logger, fn, newExecCx, childB, newWaitedFor)) {
          changed = true;
        }
      }
    }
    
    return changed;
  }

  /**
   * Convert dataflow f() to local f_l() with wait around it
   * @param logger
   * @param block
   * @return
   */
  private static boolean inlineOps(Logger logger, Function fn,
        ExecContext execCx, Block block, HierarchicalSet<Var> waitedFor) {
    boolean changed = false;
    ListIterator<Statement> it = block.statementIterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      // Only handle instructions: don't recurse here
      if (stmt.type() == StatementType.INSTRUCTION) {
        if (tryExplode(logger, fn, execCx, block, it, stmt.instruction(),
                       waitedFor)) {
          changed = true;
        } 
      }
    }
    return changed;
  }
  
  /**
   * Attempt to explode individual instruction
   * @param logger
   * @param fn
   * @param execCx
   * @param block
   * @param it
   * @param inst
   * @param waitedFor any vars waited for, e.g. in outer exploded.  This prevents
   *      infinite cycles of exploding if we didn't change instruction
   * @return true if exploded
   */
  private static boolean tryExplode(Logger logger, Function fn,
        ExecContext execCx, Block block, ListIterator<Statement> it,
        Instruction inst, HierarchicalSet<Var> waitedFor) {
    MakeImmRequest req = inst.canMakeImmediate(waitedFor,
             Collections.<ArgCV>emptySet(), Collections.<Var>emptySet(),
             true);
    if (req != null && req.in.size() > 0) {
      if (logger.isTraceEnabled()) {
        logger.trace("Exploding " + inst + " in function " + fn.getName());
      }
      
      // Remove old instruction now that we're certain to replace it
      it.remove();
      
      List<Pair<Var, Ternary>> waitVars = new ArrayList<Pair<Var, Ternary>>();
      Map<Var, Var> filenameMap = new HashMap<Var, Var>();
      OptUtil.buildWaitVars(block, it, req.in, req.out, filenameMap, waitVars);
      
      Block insideWaitBlock = enterWaits(fn, execCx, block, inst, req, waitVars);
      
      // Instructions to add inside wait
      List<Statement> instBuffer = new ArrayList<Statement>();
      
      // Fetch the inputs
      List<Arg> inVals = OptUtil.fetchMakeImmInputs(insideWaitBlock, req.in,
                                                    instBuffer);
            
      // Create local instruction, copy out outputs
      List<Var> localOutputs = OptUtil.createMakeImmOutputs(insideWaitBlock,
                                          req.out, filenameMap, instBuffer); 

      MakeImmChange change = inst.makeImmediate(
                                  new OptVarCreator(insideWaitBlock),
                                  Fetched.makeList(req.out, localOutputs),
                                  Fetched.makeList(req.in, inVals));
      OptUtil.fixupImmChange(block, insideWaitBlock, inst, change, instBuffer,
                                 localOutputs, req.out);
      
      // Remove old instruction, add new one inside wait block
      insideWaitBlock.addStatements(instBuffer);
      return true;
    }
    return false;
  }

  /**
   * @param fn
   * @param execCx
   * @param block
   * @param inst
   * @param req
   * @param waitVars
   * @return innermost wait block
   */
  private static Block enterWaits(Function fn, ExecContext execCx, Block block,
      Instruction inst, MakeImmRequest req, List<Pair<Var, Ternary>> waitVars) {

    List<WaitVar> nonRecWaitVars = new ArrayList<WaitVar>();
    List<WaitVar> recWaitVars = new ArrayList<WaitVar>();
    List<WaitVar> eitherWaitVars = new ArrayList<WaitVar>();
    
    /*
     * Need to ensure we correctly non-rec or rec wait if it's important
     */
    for (Pair<Var, Ternary> wv: waitVars) {
      if (wv.val2 == Ternary.FALSE) {
        nonRecWaitVars.add(new WaitVar(wv.val1, false));
      } else if (wv.val2 == Ternary.TRUE) {
        recWaitVars.add(new WaitVar(wv.val1, false));
      } else {
        eitherWaitVars.add(new WaitVar(wv.val1, false));
      }
    }
    
    boolean needRec = recWaitVars.size() > 0;
    boolean needNonRec = nonRecWaitVars.size() > 0;
    
    if (needRec && needNonRec) {
      nonRecWaitVars.addAll(eitherWaitVars);
      
      // First get rec
      block = enterWait(fn, block, inst, true, WaitMode.WAIT_ONLY,
                        TaskMode.LOCAL, recWaitVars);
      // then non-rec
      return enterWait(fn, block, inst, false, 
          selectWaitMode(execCx, req.mode), req.mode, nonRecWaitVars);
    } else if (needRec && !needNonRec) {
      recWaitVars.addAll(eitherWaitVars);
      return enterWait(fn, block, inst, true, selectWaitMode(execCx, req.mode),
                       req.mode, recWaitVars);
    } else {
      // Treat all as non-recursive
      nonRecWaitVars.addAll(eitherWaitVars);
      
      // Must createwait even with no vars to ensure wait mode respected
      // NOTE: also avoids problem with caller being confused by
      //    commodification issues with stmt list in outer block
      return enterWait(fn, block, inst, false, selectWaitMode(execCx, req.mode),
                       req.mode, nonRecWaitVars);
    }
  }

  private static Block enterWait(Function fn, Block block,
      Instruction inst, boolean recursive, WaitMode waitMode, TaskMode taskMode, 
      List<WaitVar> waitVars) {
    TaskProps props = inst.getTaskProps();
    if (props == null) {
      props = new TaskProps();
    }
    
    WaitStatement wait = new WaitStatement(
            fn.getName() + "-" + inst.shortOpName(),
            waitVars, PassedVar.NONE, Var.NONE,
            waitMode, recursive, taskMode, props);
    block.addContinuation(wait);
    
    if (props.containsKey(TaskPropKey.PARALLELISM)) {
      //TODO: different output var conventions
      throw new STCRuntimeError("Don't know how to explode parallel " +
          "instruction yet: " + inst);
    }
    return wait.getBlock();
  }

  private static WaitMode selectWaitMode(ExecContext execCx, TaskMode taskMode) {
    // TODO: this is a little restrictive
    WaitMode waitMode;
    if (taskMode == TaskMode.SYNC || taskMode == TaskMode.LOCAL ||
          (taskMode == TaskMode.LOCAL_CONTROL &&
            execCx == ExecContext.CONTROL)) {
      waitMode = WaitMode.WAIT_ONLY;
    } else {
      waitMode = WaitMode.TASK_DISPATCH;
    }
    return waitMode;
  }

}
