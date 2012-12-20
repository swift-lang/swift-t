package exm.stc.ic;

import java.util.List;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.TurbineOp;
import exm.stc.ic.tree.ICTree.Block;

/**
 * Utility functions used to generate wrappers for local operations
 * @author tim
 *
 */
public class WrapUtil {
  
  /**
   * Fetch the value of a variable
   * @param block
   * @param instBuffer append fetch instruction to this list
   * @param var the variable to fetch the value of
   * @return variable holding value
   */
  public static Var fetchValueOf(Block block, List<Instruction> instBuffer,
          Var var, String valName) {
    Type value_t = Types.derefResultType(var.type());
    if (Types.isScalarValue(value_t)) {
      // The result will be a value
      // Use the OPT_VALUE_VAR_PREFIX to make sure we don't clash with
      //  something inserted by the frontend (this caused problems before)
      Var value_v = new Var(value_t,
          valName,
          VarStorage.LOCAL, DefType.LOCAL_COMPILER, null);
      block.addVariable(value_v);
      instBuffer.add(ICInstructions.retrieveValueOf(value_v, var));
      
      // Add cleanup action if needed
      if (value_t.equals(Types.V_BLOB)) {
        block.addCleanup(value_v, TurbineOp.decrBlobRef(var));
      }
      return value_v;
    } else if (Types.isRef(var.type())) {
      // The result will be an alias
      Var deref = new Var(value_t,
          valName,
          VarStorage.ALIAS, DefType.LOCAL_COMPILER, null);
      block.addVariable(deref);
      instBuffer.add(TurbineOp.retrieveRef(deref, var));
      return deref;
    } else {
      throw new STCRuntimeError("shouldn't be possible to get here");
    }
  }
  
  public static Var declareLocalOutputVar(Block block, Var var,
          String valName) {
    Var valOut = block.declareVariable(
        Types.derefResultType(var.type()),
        valName,
        VarStorage.LOCAL, DefType.LOCAL_COMPILER, null);
    if (valOut.type().equals(Types.V_BLOB)) {
      block.addCleanup(valOut, TurbineOp.freeBlob(valOut));
    }
    return valOut;
  }
}
