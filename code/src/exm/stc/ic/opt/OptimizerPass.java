package exm.stc.ic.opt;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.UserException;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

/**
 * An optimizer pass
 */
public interface OptimizerPass {
  public abstract String getPassName();
  /**
   * @return Key indicating whether pass is enabled.  If null, always enabled
   */
  public abstract String getConfigEnabledKey();
  public abstract void optimize(Logger logger, Program program)
                                              throws UserException;
  
  public static abstract class FunctionOptimizerPass implements OptimizerPass {

    @Override
    public void optimize(Logger logger, Program program) throws UserException {
      for (Function f: program.getFunctions()) {
        optimize(logger, f);
      }
    }
    
    public abstract void optimize(Logger logger, Function f) throws UserException;
  }
}
