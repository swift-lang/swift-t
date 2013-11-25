package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.ic.WrapUtil;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.Loop;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICContinuations.WaitVar;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;


/**
 * Try to simplify iterative loops by swapping futures for values as loop vars
 *
 * This expects that value numbering has been run to fill in information about
 * which loop variables are closed
 * 
 * @author tim
 */
public class LoopSimplify extends FunctionOptimizerPass {

  @Override
  public String getPassName() {
    return "Loop simplify";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_LOOP_SIMPLIFY;
  }

  @Override
  public void optimize(Logger logger, Function f) throws UserException {
    findLoopsAndOptimize(logger, f.mainBlock());
  }

  private void findLoopsAndOptimize(Logger logger, Block block) {
    for (Statement stmt: block.getStatements()) {
      if (stmt.type() == StatementType.CONDITIONAL) {
        for (Block inner: stmt.conditional().getBlocks()) {
          // Recurse
          findLoopsAndOptimize(logger, inner);
        }
      } else {
        assert(stmt.type() == StatementType.INSTRUCTION);
        // Do nothing
      }
    }
    
    ListIterator<Continuation> contIt = block.continuationIterator();
    while (contIt.hasNext()) {
      Continuation cont = contIt.next();
      if (cont.getType() == ContinuationType.LOOP) {
        Loop loop = (Loop)cont;
        WaitStatement wrapper = tryWrapWithWait(logger, loop);
        if (wrapper != null) {
          // Replace loop with wrapper
          contIt.set(wrapper);
        }
      }
      
      // Recurse on inner blocks
      for (Block inner: cont.getBlocks()) {
        findLoopsAndOptimize(logger, inner);
      }
    }
  }

  /**
   * Put a wait around loop for any initial values that are blocking
   * @param logger
   * @param loop
   * @return
   */
  private WaitStatement tryWrapWithWait(Logger logger, Loop loop) {
    List<BlockingVar> vars = loop.unclosedBlockingInitVals();
    if (vars.isEmpty()) {
      return null;
    }
    
    List<WaitVar> waitVars = new ArrayList<WaitVar>();
    List<WaitVar> recWaitVars = new ArrayList<WaitVar>();
    for (BlockingVar bv: vars) {
      if (bv.recursive) {
        recWaitVars.add(new WaitVar(bv.var, bv.explicit));
      } else {
        waitVars.add(new WaitVar(bv.var, bv.explicit));
      }
      loop.setInitClosed(bv.var, bv.recursive);
    }
    
    Continuation inner = loop;
    WaitStatement wrapper = null;
    if (!recWaitVars.isEmpty()) {
      String name = loop.loopName() + "-init-recwait";
      wrapper = new WaitStatement(name, recWaitVars, PassedVar.NONE,
                    Var.NONE, WaitMode.WAIT_ONLY, true, TaskMode.LOCAL,
                    new TaskProps());
      wrapper.getBlock().addContinuation(inner);
      inner = wrapper;
    }
    
    if (!waitVars.isEmpty()) {
      String name = loop.loopName() + "-init-wait";
      wrapper = new WaitStatement(name, waitVars, PassedVar.NONE,
                    Var.NONE, WaitMode.WAIT_ONLY, false, TaskMode.LOCAL,
                    new TaskProps());
      wrapper.getBlock().addContinuation(inner);
      inner = wrapper;
    }
    
    assert(wrapper != null);
    return wrapper;
  }

  /**
   * In cases where variable is closed upon starting loop iterations,
   * switch to passing as value
   *  
   * @param logger
   * @param loop
   */
  private void optimizeLoop(Logger logger, Loop loop) {
    
    List<BlockingVar> closedVars = loop.closedVars();
    if (closedVars.isEmpty()) {
      return;
    }
    
    boolean replacedAll = true;
    
    Block outerBlock = loop.parent();
    // To put before loop entry point
    List<Statement> outerFetches = null; //TODO
    List<Var> outerFetched = new ArrayList<Var>();
    
    // TODO: locate block with loop_continue
    Block innerBlock = null;
    // To put before loop_continue instruction
    List<Statement> innerFetches = null; //TODO
    List<Var> innerFetched = new ArrayList<Var>();
    
    for (BlockingVar closedVar: closedVars) {
      if (replaceLoopVarWithVal(closedVar.var)) {
        // TODO: add instructions before loop and at loop_continue
        //       to fetch values
        outerFetched.add(fetchLoopVar(closedVar, outerBlock, outerFetches));
        innerFetched.add(fetchLoopVar(closedVar, innerBlock, innerFetches));
        
        // TODO: Add instruction at top of loop body to store value.
        //       Replace loop var?
      } else {
        replacedAll = false;
      }
    }
  }

  private Var fetchLoopVar(BlockingVar closedVar, Block targetBlock,
      List<Statement> fetches) {
    assert(replaceLoopVarWithVal(closedVar.var));
    String valName = OptUtil.optVPrefix(targetBlock, closedVar.var);
    return WrapUtil.fetchValueOf(targetBlock, fetches, closedVar.var,
                                 valName, closedVar.recursive);
  }

  /**
   * Whether we should attempt to replace the original loop var with a vlue
   * equivalent
   * @param var
   * @return
   */
  private static boolean replaceLoopVarWithVal(Var var) {
    // TODO: more types, e.g. arrays?
    // TODO: start with just scalars
    // TODO: the passing to child task isn't really necessary if we can replace all
    return Types.isScalarFuture(var) && Semantics.canPassToChildTask(var);
  }
}