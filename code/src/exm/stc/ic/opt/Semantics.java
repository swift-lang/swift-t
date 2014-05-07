package exm.stc.ic.opt;

import java.util.Set;

import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.StructType;
import exm.stc.common.lang.Types.StructType.StructField;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Types.Typed;
import exm.stc.common.lang.Var;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.valuenumber.ComputedValue;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;

public class Semantics {
  /**
   * True if can pass to child task
   * @param t
   * @return
   */
  public static boolean canPassToChildTask(Typed t) {
    if (Types.isBlobVal(t)) {
      return false;
    } else if (Types.isFileVal(t) &&
               t.type().fileKind().supportsTmpImmediate()) {
      // The current scheme for managing temporary files doesn't
      // allow copying a file value across task boundaries
      return false;
    } else if (Types.isContainerLocal(t)) {
      // Depends on contents
      return canPassToChildTask(Types.containerElemType(t));
    } else if (Types.isStructLocal(t)) {
      // Check if can pass all fields
      StructType st = (StructType)t.type().getImplType();
      for (StructField field: st.getFields()) {
        Type fieldType = field.getType();
        if (!canPassToChildTask(fieldType)) {
          return false;
        }
      }
      return true;
    } else {
      return true;
    }        
  }
  


  /**
   * Check to see if we can get the mapping of an output var without
   * waiting
   * @param closedVars set of variables known to be closed
   * @param closedVars set of locations/aliases known to be closed
   * @param out
   * @return
   */
  public static boolean outputMappingAvail(Set<Var> closedVars, 
                            Set<ArgCV> closedLocations, Var out) {
    // Two cases where we can get mapping right away:
    // - if it's definitely unmapped
    // - if the mapping has been assigned
    // TODO: How to get mapping?
    return out.isMapped() == Ternary.FALSE ||
           closedLocations.contains(ComputedValue.filenameAliasCV(out));
  }

}
