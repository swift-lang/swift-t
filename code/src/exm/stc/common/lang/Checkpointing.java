package exm.stc.common.lang;

import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.frontend.Context;

public class Checkpointing {

  /**
   * Check if we can checkpoint function with these inputs and outputs
   * @param functionName name, for any error messages
   * @param ftype
   * @return
   */
  public static void checkCanCheckpoint(Context context,
        String functionName, FunctionType ftype) 
            throws TypeMismatchException {
    // TODO: should we be able to checkpoint all types?
  }
}
