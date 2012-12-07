package exm.stc.ic.opt;

import java.io.PrintStream;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.InvalidWriteException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.ic.tree.ICTree.Program;

public class ICOptimiser {

  /**
   * Optimize the program and return a new one
   * 
   * NOTE: the input might be modified in-place
   * @param icOutput where to log IC between optimizaiton steps.  Null for 
   *              no output 
   * @return
   * @throws InvalidWriteException 
   */
  public static Program optimise(Logger logger, PrintStream icOutput, 
                          Program prog) throws InvalidWriteException {
    boolean logIC = icOutput != null;
    if (logIC) {
      prog.log(icOutput, "Initial IC before optimization");
    }
    try {
      // need variable names to be unique for rest of stages
      Flattener.makeVarNamesUnique(prog);
      FixupVariables.fixupVariablePassing(logger, prog);
      
      if (Settings.getBoolean(Settings.OPT_FLATTEN_NESTED)) {
        prog = Flattener.flattenNestedBlocks(prog);
        if (logIC) {
          prog.log(icOutput, "Nested and flattened IC");
        }
      }
      
      long nPasses = Settings.getLong(Settings.OPT_NUM_PASSES);
      
      for (long pass=0; pass < nPasses; pass++) {
        if (Settings.getBoolean(Settings.OPT_CONSTANT_FOLD)) {
          ConstantFinder.constantFold(logger, prog);
          if (logIC) {
            prog.log(icOutput, "Pass " + pass + ":" + 
           "IC after constant folding/propagation and dead code elimination");
          }
        }
        
        
        if (Settings.getBoolean(Settings.OPT_UNROLL_LOOPS) && 
            pass == 0) {
          // Can only unroll loops once.  Do after constant folding to make sure
          //  that we have a good chance of eliminating branches
          LoopUnroller.unrollLoops(logger, prog);
          if (logIC) {
            prog.log(icOutput, "After unrolling loops");
          }
        }
        
        // Try to hoist variables out of loops, etc
        // Do before forward dataflow since it may open up new opportunites
        if (Settings.getBoolean(Settings.OPT_HOIST)) {
          HoistLoops.hoist(logger, prog);
          if (logIC) {
            prog.log(icOutput, "After hoisting");
          }
        }
        
        // Do this after const folding so it won't create any new constants, 
        // etc to be folded
        if (Settings.getBoolean(Settings.OPT_FORWARD_DATAFLOW)) {
          ForwardDataflow.forwardDataflow(logger, prog);
          if (logIC) {
            prog.log(icOutput, "Pass " + pass + ":" +
                          "IC after available var analysis");
          }
        }
        
        // Do this after forward dataflow to improve odds of fusing things
        // one common subexpression elimination has happened
        if (Settings.getBoolean(Settings.OPT_CONTROLFLOW_FUSION)) {
          ContinuationFusion.fuse(logger, prog);
          if (logIC) {
            prog.log(icOutput, "Pass " + pass + ":" +
                        "IC after continuation fusion");
          }
        }
        
        // Do this after forward dataflow since forward dataflow will be
        // able to do strength reduction on many operations without spinning
        // them off into wait statements
        if (Settings.getBoolean(Settings.OPT_WAIT_COALESCE)) {
          WaitCoalescer.rearrangeWaits(logger, prog);
          if (logIC) {
            prog.log(icOutput, "Pass " + pass + ":" +
                        "IC after wait coalescing");
          }
        }
        
        // Can only run this pass once. Do it on penultimate pass so that
        // results can be cleaned up by forward dataflow
        if (Settings.getBoolean(Settings.OPT_PIPELINE) &&
            pass == nPasses - 2) {
          Pipeline.pipelineTasks(logger, prog);
          if (logIC) {
            prog.log(icOutput, "Pass " + pass + ":" +
                        "IC after pipelining");
          }
        }
      }
      
      
      // Do this at end so we don't end up keeping any unneeded constants
      if (Settings.getBoolean(Settings.OPT_SHARED_CONSTANTS)) {
        ConstantFinder.makeConstantsGlobal(logger, prog);
        if (logIC) {
          prog.log(icOutput, "IC after making constants global");
        }
      }
    } catch (InvalidOptionException e) {
      e.printStackTrace();
      throw new STCRuntimeError("Optimizer setting not correct in "
          + " compiler settings dictionary: " + e.getMessage());
      
    }
    
    if (logIC) {
      prog.log(icOutput, "Final optimized IC");
    }
    return prog;
  }

}
    