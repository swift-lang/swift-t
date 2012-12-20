package exm.stc.ic.opt;

import java.io.PrintStream;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.InvalidWriteException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.ic.tree.ICTree.Program;

public class ICOptimizer {

  /**
   * Optimize the program and return a new one
   * 
   * NOTE: the input might be modified in-place
   * @param icOutput where to log IC between optimiation steps.  Null for 
   *              no output 
   * @return
   * @throws InvalidWriteException 
   */
  public static Program optimize(Logger logger, PrintStream icOutput, 
                          Program prog) throws UserException {
    boolean logIC = icOutput != null;
    if (logIC) {
      prog.log(icOutput, "Initial IC before optimization");
    }
    
    long nIterations;
    try {
      nIterations = Settings.getLong(Settings.OPT_MAX_ITERATIONS);
    } catch (InvalidOptionException ex) {
      throw new STCRuntimeError(ex.getMessage());
    }
     
    preprocess(icOutput, logger, prog);

    for (long iteration = 0; iteration < nIterations; iteration++) {
      iterate(icOutput, logger, prog, iteration, nIterations);
    }
    
    postprocess(icOutput, logger, prog, nIterations);

    if (logIC) {
      prog.log(icOutput, "Final optimized IC");
    }
    return prog;
  }

  /**
   * Do preprocessing optimizer steps
   * @param icOutput
   * @param logger
   * @param program
   * @throws Exception 
   */
  private static void preprocess(PrintStream icOutput, Logger logger,
                                       Program program) throws UserException {
    OptimizerPipeline preprocess = new OptimizerPipeline(icOutput);
    // need variable names to be unique for rest of stages
    preprocess.addPass(new UniqueVarNames());
    // Must fix up variables as frontend doesn't get it right
    preprocess.addPass(new FixupVariables());
    preprocess.addPass(new FlattenNested());
    preprocess.runPipeline(logger, program, 0);
  }

  /**
   * Do one iteration of the iterative optimizer passes
   * @param icOutput
   * @param logger
   * @param prog
   * @param iteration
   * @param nIterations
   * @throws Exception
   */
  private static void iterate(PrintStream icOutput, Logger logger,
      Program prog, long iteration, long nIterations) throws UserException {
    OptimizerPipeline pipe = new OptimizerPipeline(icOutput);
    // First prune any unneeded functions
    pipe.addPass(new FunctionInline());
    
    
    pipe.addPass(new ConstantFold());
    
    if (iteration == 0) {
      // Only unroll loops once
      pipe.addPass(new LoopUnroller());
    }
    
    // Try to hoist variables out of loops, etc
    // Do before forward dataflow since it may open up new opportunites
    pipe.addPass(new HoistLoops());
    
    // Do forward dataflow after const folding so it won't create any
    // new constants, etc to be folded
    pipe.addPass(new ForwardDataflow());
    
    // Do this after forward dataflow to improve odds of fusing things
    // one common subexpression elimination has happened
    pipe.addPass(new ContinuationFusion());

    // Can only run this pass once. Do it on penultimate pass so that
    // results can be cleaned up by forward dataflow
    if (iteration == nIterations - 2) {
      pipe.addPass(new Pipeline());
    }
    
    // Do this after forward dataflow since forward dataflow will be
    // able to do strength reduction on many operations without spinning
    // them off into wait statements
    boolean doWaitMerges = (iteration == nIterations - 1);
    pipe.addPass(new WaitCoalescer(doWaitMerges));
    
    pipe.runPipeline(logger, prog, iteration);
  }

  private static void postprocess(PrintStream icOutput, Logger logger,
      Program prog, long nIterations) throws UserException {   
    OptimizerPipeline postprocess = new OptimizerPipeline(icOutput);
    postprocess.addPass(new ConstantSharing());
    postprocess.runPipeline(logger, prog,  nIterations - 1);
  }

}
    