package exm.stc.ic.opt;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.ic.tree.ICTree.Program;


public class OptimizerPipeline {
  
  public OptimizerPipeline(PrintStream icOutput) {
    this.icOutput = icOutput;
  }

  private final List<OptimizerPass> passes = new ArrayList<OptimizerPass>();
  private final PrintStream icOutput;
  
  public void addPass(OptimizerPass pass) {
    passes.add(pass);
  }
  
  public void runPipeline(Logger logger, Program program, long iteration) throws UserException {
    for (OptimizerPass pass: passes) {
      if (passEnabled(pass)) {
        logger.debug("Iteration: " + iteration + " Pass: "
                   + pass.getPassName());
        pass.optimize(logger, program);
        if (icOutput != null) {
          program.log(icOutput, "Iteration " + iteration + " IC after " +
				pass.getPassName());
        }
      }
    }
  }
  
  public boolean passEnabled(OptimizerPass pass) {
    try {
      String key = pass.getConfigEnabledKey();
      return key == null || Settings.getBoolean(key);
    } catch (InvalidOptionException e) {
      throw new STCRuntimeError("Expected config key " + pass.getConfigEnabledKey() 
          + " to exist");
    }
  }
}
