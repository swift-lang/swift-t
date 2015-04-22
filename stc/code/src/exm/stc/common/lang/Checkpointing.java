package exm.stc.common.lang;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.TypeMismatchException;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Types.FunctionType;
import exm.stc.frontend.Context;

public class Checkpointing {

  /**
   * Check if we can checkpoint function with these inputs and outputs
   * @param id function id, for any error messages
   * @param ftype
   */
  public static void checkCanCheckpoint(Context context,
        FnID id, FunctionType ftype)
            throws TypeMismatchException, UserException {
    // TODO: should we be able to checkpoint all types?

    if (!Settings.getBoolean(Settings.ENABLE_CHECKPOINTING)) {
      throw new UserException(context, "STC checkpointing feature " +
                                         "not enabled");
    }
  }
}
