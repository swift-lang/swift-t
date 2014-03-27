package exm.stc.ic.opt;

import java.util.Arrays;
import java.util.ListIterator;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.ic.aliases.AliasKey;
import exm.stc.ic.aliases.AliasTracker;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.TurbineOp;

/**
 * Try to propagate any aliases created to instructions where they're used,
 * so that lookup/store can be performed directly
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
    propAliasesRec(logger, f.mainBlock(), new AliasTracker());
  }
  
  public static void propAliasesRec(Logger logger, Block b, AliasTracker aliases) {
    
    ListIterator<Statement> stmtIt = b.statementIterator();
    while (stmtIt.hasNext()) {
      Statement stmt = stmtIt.next();
      
      switch (stmt.type()) {
        case INSTRUCTION:
          propAliasesInst(logger, stmtIt, stmt.instruction(), aliases);
          
          aliases.update(stmt.instruction());
          break;
        case CONDITIONAL:
          for (Block cb: stmt.conditional().getBlocks()) {
            propAliasesRec(logger, cb, aliases.makeChild());
          }
          break;
        default:
          throw new STCRuntimeError("Unexpected " + stmt.type());
      }
    }
    
    for (Continuation c: b.getContinuations()) {
      for (Block cb: c.getBlocks()) {
        propAliasesRec(logger, cb, aliases.makeChild());
      }
    }
  }

  private static void propAliasesInst(Logger logger, ListIterator<Statement> stmtIt,
                                      Instruction inst, AliasTracker aliases) {
    Instruction newInst = tryPropagateAliases(inst, aliases);
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
  private static Instruction tryPropagateAliases(Instruction inst, AliasTracker aliases) {
    if (inst.op.isRetrieve(false)) {
      Var src = inst.getInput(0).getVar();
      AliasKey srcKey = aliases.getCanonical(src);
      if (srcKey.isPlainStructAlias()) {
        Arg decr = Arg.ZERO;
        if (inst.getInputs().size() > 1) {
          decr = inst.getInput(1);
        }
        
        return TurbineOp.structRetrieveSub(inst.getOutput(0), srcKey.var,
                                Arrays.asList(srcKey.structPath), decr);
      }
    } else if (inst.op.isAssign(false)) {
      Var out ;
    }
    return null;
  }

}
