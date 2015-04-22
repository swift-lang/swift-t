package exm.stc.common.exceptions;

import java.io.IOException;

import exm.stc.frontend.Context;

/**
 * Exception thrown when loading module
 */
public class ModuleLoadException extends UserException {
  private static final long serialVersionUID = 1L;

  public ModuleLoadException(String filePath, IOException cause) {
    super(buildMessage(filePath, cause));
  }
  
  public ModuleLoadException(Context context, String filePath,
                                              IOException cause) {
    super(context, buildMessage(filePath, cause));
  }

  public ModuleLoadException(Context context, String string) {
    super(context, string);
  }

  private static String buildMessage(String filePath, IOException cause) {
    return "Error occured while trying to load Swift source file: "
        + filePath + ": " + cause.getMessage();
  }

}
