package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Collections;
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
import exm.stc.ic.WrapUtil;
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
    if (req != null && 
        (req.in.size() > 0 || req.wait.size() > 0)) {
      if (logger.isTraceEnabled()) {
        logger.trace("Exploding " + inst + " in function " + fn.getName());
      }
      
      // Remove old instruction now that we're certain to replace it
      it.remove();
      
      // We have to initialize mapping if instruction doesn't do it
      boolean mustInitOutputMapping = !req.initsOutputMapping;
      
      Pair<List<WaitVar>, Map<Var, Var>> r;
      r = WrapUtil.buildWaitVars(block, it, req.in, req.wait, req.out,
                                 mustInitOutputMapping);
      
      List<WaitVar> waitVars = r.val1;
      Map<Var, Var> filenameMap = r.val2;
      
      WaitMode waitMode;
      if (req.mode == TaskMode.SYNC || req.mode == TaskMode.LOCAL ||
            (req.mode == TaskMode.LOCAL_CONTROL &&
              execCx == ExecContext.CONTROL)) {
        waitMode = WaitMode.WAIT_ONLY;
      } else {
        waitMode = WaitMode.TASK_DISPATCH;
      }
      
      TaskProps props = inst.getTaskProps();
      if (props == null) {
        props = new TaskProps();
      }
      
      WaitStatement wait = new WaitStatement(
              fn.getName() + "-" + inst.shortOpName(),
              waitVars, PassedVar.NONE, Var.NONE,
              waitMode, req.recursive, req.mode, props);
      block.addContinuation(wait);
      
      if (props.containsKey(TaskPropKey.PARALLELISM)) {
        //TODO: different output var conventions
        throw new STCRuntimeError("Don't know how to explode parallel " +
            "instruction yet: " + inst);
      }
      
      // Instructions to add inside wait
      List<Statement> instBuffer = new ArrayList<Statement>();
      
      // Fetch the inputs
      List<Arg> inVals = WrapUtil.fetchLocalOpInputs(wait.getBlock(), req.in,
                                                     instBuffer, true);
            
      // Create local instruction, copy out outputs
      List<Var> localOutputs = WrapUtil.createLocalOpOutputs(
                              wait.getBlock(), req.out, filenameMap,
                              instBuffer, true, mustInitOutputMapping,
                              req.recursive); 

      boolean storeOutputMapping = req.initsOutputMapping;
      MakeImmChange change = inst.makeImmediate(
                                  new OptVarCreator(wait.getBlock()),
                                  Fetched.makeList(req.out, localOutputs),
                                  Fetched.makeList(req.in, inVals));
      OptUtil.fixupImmChange(block, wait.getBlock(), inst, change, instBuffer,
                                 localOutputs, req.out, storeOutputMapping,
                                 req.recursive);
      
      // Remove old instruction, add new one inside wait block
      wait.getBlock().addStatements(instBuffer);
      return true;
    }
    return false;
  }

}
