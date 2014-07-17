package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.WaitMode;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.ic.WrapUtil;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.Loop;
import exm.stc.ic.tree.ICContinuations.LoopInstructions;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.LoopContinue;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;
import exm.stc.ic.tree.TurbineOp;


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
          cont = wrapper; // Want to recurse on this
        } else {
          optimizeLoop(logger, loop);
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
      loop.setInitClosed(bv.var.asArg(), bv.recursive);
    }
    
    Continuation inner = loop;
    WaitStatement wrapper = null;
    if (!recWaitVars.isEmpty()) {
      String name = loop.loopName() + "-init-recwait";
      wrapper = new WaitStatement(name, recWaitVars, PassedVar.NONE,
                    Var.NONE, WaitMode.WAIT_ONLY, true,
                    ExecTarget.nonDispatchedAny(), new TaskProps());
      wrapper.getBlock().addContinuation(inner);
      inner = wrapper;
    }
    
    if (!waitVars.isEmpty()) {
      String name = loop.loopName() + "-init-wait";
      wrapper = new WaitStatement(name, waitVars, PassedVar.NONE,
                    Var.NONE, WaitMode.WAIT_ONLY, false,
                    ExecTarget.nonDispatchedAny(), new TaskProps());
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
    
    List<BlockingVar> closedVars = loop.closedLoopVars();
    if (closedVars.isEmpty()) {
      return;
    }
    
    Block outerBlock = loop.parent();
    // To put before loop entry point
    ListIterator<Statement> outerInsertPoint = outerBlock.statementEndIterator();
    List<Statement> outerFetches = new ArrayList<Statement>();
    List<Var> outerFetched = new ArrayList<Var>();
    
    // locate block with loop_continue
    LoopInstructions insts = loop.findInstructions();
    Block innerBlock = insts.continueInstBlock;
    ListIterator<Statement> innerInsertPoint = findLoopContinueInsertPoint(
                                            insts.continueInst, innerBlock);
    
    // To put before loop_continue instruction
    List<Statement> innerFetches = new ArrayList<Statement>();
    List<Var> innerFetched = new ArrayList<Var>();
    
    for (BlockingVar closedVar: closedVars) {
      if (replaceLoopVarWithVal(closedVar.var)) {
        // Should be vars, otherwise it doesn't make sense to try to replace
        Var init = loop.getInitVal(closedVar.var).getVar();
        Var subsequent = loop.getUpdateVal(closedVar.var).getVar();

        // add instructions before loop and at loop_continue to fetch values        
        Var outerFetchedV = fetchLoopVar(init, closedVar.recursive, outerBlock,
                                      outerFetches);
        Var innerFetchedV = fetchLoopVar(subsequent, closedVar.recursive,
                                      innerBlock, innerFetches);
        outerFetched.add(outerFetchedV);
        innerFetched.add(innerFetchedV);
        
        // place inner and outer instructions
        placeAndClear(outerInsertPoint, outerFetches);
        placeAndClear(innerInsertPoint, innerFetches);

        // replace the loop var with the new one
        Var oldLoopVar = closedVar.var;
        Var newLoopVar = new Var(outerFetchedV.type(),
            OptUtil.optVPrefix(outerBlock, oldLoopVar),
            outerFetchedV.storage(), DefType.LOCAL_COMPILER,
            VarProvenance.valueOf(oldLoopVar));
        boolean blockOn = Types.canWaitForFinalize(newLoopVar) &&
                          loop.isBlocking(oldLoopVar);
        loop.replaceLoopVar(oldLoopVar, newLoopVar, outerFetchedV.asArg(),
                            innerFetchedV.asArg(), blockOn);

        /*
         * Move declaration of old var to loop body and assign so code is
         * still valid.  Other passes should optimise this out later.
         */
        loop.getLoopBody().addVariable(oldLoopVar);
        loop.getLoopBody().addInstructionFront(
            TurbineOp.storePrim(oldLoopVar, newLoopVar.asArg()));
      }
    }
  }

  /**
   * Position the loop iterator before the loop continue instructions
   * @param insts
   * @param innerBlock
   * @return
   */
  private ListIterator<Statement> findLoopContinueInsertPoint(
      LoopContinue continueInst, Block block) {
    ListIterator<Statement> it = block.statementIterator();
    boolean found = false;
    while (it.hasNext()) {
      Statement stmt = it.next();
      if (stmt == continueInst) {
        found = true;
        it.previous(); // Move to before the loop continue
        break;
      }
    }
    assert(found) : "Could not find loop continue " + continueInst +
                     " in block" + block;
    return it;
  }

  private void placeAndClear(ListIterator<Statement> insertPoint,
                             List<Statement> fetches) {
    for (Statement fetch: fetches) {
      insertPoint.add(fetch);
    }
    fetches.clear();
  }

  private Var fetchLoopVar(Var var, boolean recursive, Block targetBlock,
      List<Statement> fetches) {
    assert(replaceLoopVarWithVal(var));
    String valName = OptUtil.optVPrefix(targetBlock, var);
    return WrapUtil.fetchValueOf(targetBlock, fetches, var,
                                 valName, recursive, false);
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