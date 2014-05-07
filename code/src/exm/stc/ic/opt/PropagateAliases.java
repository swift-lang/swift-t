package exm.stc.ic.opt;

import java.util.Arrays;
import java.util.ListIterator;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Unimplemented;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.ic.aliases.AliasKey;
import exm.stc.ic.aliases.AliasTracker;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.Opcode;
import exm.stc.ic.tree.TurbineOp;

/**
 * Try to propagate any aliases created to instructions where they're used,
 * so that lookup/store can be performed directly instead of to opaque alias.
 * 
 * TODO: remove waitedForAliases approach and wait directly on aliases
 */
public class PropagateAliases extends FunctionOptimizerPass {

  @Override
  public String getPassName() {
    return "Propagate aliases";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_PROPAGATE_ALIASES;
  }

  @Override
  public void optimize(Logger logger, Function f) throws UserException {
    propAliasesRec(logger, f.mainBlock(), new AliasTracker(),
                   new HierarchicalSet<Var>());
  }
  
  /**
   * 
   * @param logger
   * @param b
   * @param aliases
   * @param waitedForAliases alias variables that have been waited for
   */
  public static void propAliasesRec(Logger logger, Block b, AliasTracker aliases,
                   HierarchicalSet<Var> waitedForAliases) {
    
    ListIterator<Statement> stmtIt = b.statementIterator();
    while (stmtIt.hasNext()) {
      Statement stmt = stmtIt.next();
      
      switch (stmt.type()) {
        case INSTRUCTION: {
          Instruction inst = stmt.instruction();
          inst = preprocessInst(logger, b, stmtIt, inst);
          
          propAliasesInst(logger, stmtIt, inst, aliases, waitedForAliases);
          
          aliases.update(stmt.instruction());
          break;
        }
        case CONDITIONAL:
          propAliasRecOnCont(logger, aliases, waitedForAliases, stmt.conditional());
          break;
        default:
          throw new STCRuntimeError("Unexpected " + stmt.type());
      }
    }
    
    for (Continuation c: b.getContinuations()) {
      propAliasRecOnCont(logger, aliases, waitedForAliases, c);
    }
  }

  /**
   * Any modifications to instruction
   * @param logger
   * @param b
   * @param stmtIt
   * @param instruction
   * @return
   */
  private static Instruction preprocessInst(Logger logger, Block b,
      ListIterator<Statement> stmtIt, Instruction inst) {
    if (inst.op == Opcode.ARR_COPY_OUT_IMM) {
      return swapArrayAlias(logger, b, stmtIt, inst);
    }
    return inst; // Return unmodified by default
  }

  /**
   * Change array copy out to create array alias then copy,
   * to allow further optimization
   * @param logger
   * @param b
   * @param stmtIt
   * @param inst
   */
  private static Instruction swapArrayAlias(Logger logger, Block b,
      ListIterator<Statement> stmtIt, Instruction inst) {
    assert(inst.op == Opcode.ARR_COPY_OUT_IMM);
    Var dst = inst.getOutput(0);
    Var arr = inst.getInput(0).getVar();
    
    if (!Unimplemented.subscriptAliasSupported(arr)) {
      // Don't change if not supported
      return inst;
    }
    
    Arg ix = inst.getInput(1);
    
    Var alias = OptUtil.createTmpAlias(b, dst);
    
    Instruction aliasInst = TurbineOp.arrayCreateAlias(alias, arr, ix);
    Instruction copyInst = TurbineOp.asyncCopy(dst, alias); 
    
    stmtIt.remove();
    stmtIt.add(aliasInst);
    stmtIt.add(copyInst);
    
    return aliasInst; // Return first instruction
  }

  private static void propAliasRecOnCont(Logger logger, AliasTracker aliases,
      HierarchicalSet<Var> waitedForAliases, Continuation cont) {
    HierarchicalSet<Var> contwaitedForAliases;
    if (cont.getType() == ContinuationType.WAIT_STATEMENT) {
      WaitStatement w = (WaitStatement)cont;
      
      /*
       * This waitedForAliases is a workaround for the fact that other optimization
       * passes aren't aware that waiting on an aliases will wait for an
       * array/struct/whatever member and will optimize out the wait
       * incorrectly even though the member is accessed within the wait.
       * 
       * To avoid this we just avoid substituting any aliases that were
       * waited on
       * 
       * TODO: remove once better solution possible
       * 
       * TODO: we have enough info to merge waits on structs, e.g.
       *      if we wait on x.A and x.B, and those are only two fields
       *      in struct
       */

      contwaitedForAliases = waitedForAliases.makeChild();
      for (WaitVar wv: w.getWaitVars()) {
        AliasKey wvAlias = aliases.getCanonical(wv.var);
        if (wvAlias.pathLength() > 0) {
          contwaitedForAliases.add(wv.var);
        }
      }
    } else {
      contwaitedForAliases = waitedForAliases;
    }
    for (Block cb: cont.getBlocks()) {
      propAliasesRec(logger, cb, aliases.makeChild(), contwaitedForAliases);
    }
  }

  private static void propAliasesInst(Logger logger, ListIterator<Statement> stmtIt,
                Instruction inst, AliasTracker aliases, Set<Var> waitedForAliases) {
    Instruction newInst = tryPropagateAliases(logger, inst, aliases,
                                              waitedForAliases);
    if (newInst != null) {
      stmtIt.set(newInst);
    }  
  }

  /**
   * 
   * TODO: it is possible to extend this to further ops
   * TODO: only aliases so far are structs
   * @param inst
   * @param aliases
   * @return
   */
  private static Instruction tryPropagateAliases(Logger logger,
      Instruction inst, AliasTracker aliases, Set<Var> waitedForAliases) {
    if (logger.isTraceEnabled()) {
      logger.trace("Try propagate aliases for " + inst);
    }
    if (inst.op.isRetrieve(false)) {
      Var src = inst.getInput(0).getVar();
      
      AliasKey srcKey = checkedGetCanonical(logger, aliases, waitedForAliases, src);

      Arg decr = Arg.ZERO;
      if (inst.getInputs().size() > 1) {
        decr = inst.getInput(1);
      }
      
      if (srcKey != null && srcKey.isPlainStructAlias()) {
        
        return TurbineOp.structRetrieveSub(inst.getOutput(0), srcKey.var,
                                Arrays.asList(srcKey.path), decr);
      } else if (srcKey != null && srcKey.isFilenameAlias() && 
                 decr.isIntVal() && decr.getIntLit() == 0) {
        return TurbineOp.getFilenameVal(inst.getOutput(0), srcKey.var);
      }
    } else if (inst.op.isAssign(false)) {
      Var dst = inst.getOutput(0);

      AliasKey dstKey = checkedGetCanonical(logger, aliases, waitedForAliases, dst);
      logger.trace("ALIAS FOR " + dst + ": " + dstKey);
      if (dstKey != null && dstKey.isPlainStructAlias()) {
        return TurbineOp.structStoreSub(dstKey.var, Arrays.asList(dstKey.path),
                                        inst.getInput(0)); 
      } else if (dstKey != null && dstKey.isFilenameAlias()) {
        return TurbineOp.setFilenameVal(dstKey.var, inst.getInput(0));
      }
    }
    return null;
  }

  /**
   * Get alias, checking against waitedForAliases.  If var is in waitedForAliases,
   * return null
   * @param aliases
   * @param waitedForAliases
   * @param var
   * @return
   */
  private static AliasKey checkedGetCanonical(Logger logger,
      AliasTracker aliases, Set<Var> waitedForAliases, Var var) {
    if (waitedForAliases.contains(var)) {
      logger.trace("Can't replace alias " + var + ": waited for");
      return null;
    } else {
      AliasKey canon = aliases.getCanonical(var);
      assert(canon != null);
      return canon;
    }
  }

}
