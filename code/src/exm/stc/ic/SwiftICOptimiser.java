package exm.stc.ic;

import java.io.PrintStream;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.InvalidWriteException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.ic.ICContinuations.Continuation;
import exm.stc.ic.SwiftIC.Block;
import exm.stc.ic.SwiftIC.CompFunction;
import exm.stc.ic.SwiftIC.Program;
import exm.stc.ic.opt.ConstantFinder;
import exm.stc.ic.opt.ContinuationFusion;
import exm.stc.ic.opt.FixupVariables;
import exm.stc.ic.opt.Flattener;
import exm.stc.ic.opt.ForwardDataflow;
import exm.stc.ic.opt.WaitCoalescer;

public class SwiftICOptimiser {

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
          unrollLoops(logger, prog);
          if (logIC) {
            prog.log(icOutput, "After unrolling loops");
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
  
  private static void unrollLoops(Logger logger, Program prog) {
    for (CompFunction f: prog.getComposites()) {
      if (unrollLoops(logger, prog, f, f.getMainblock())) {
        // Unrolling can introduce duplicate vars
        Flattener.makeVarNamesUnique(f, prog.getGlobalConsts().keySet());
        Flattener.flattenNestedBlocks(f.getMainblock());
      }
    }
  }

  private static boolean unrollLoops(Logger logger, Program prog, CompFunction f,
      Block block) {
    logger.debug("looking to unroll loops in " + f.getName());
    boolean unrolled = false;
    for (int i = 0; i < block.getContinuations().size(); i++) {
      Continuation c = block.getContinuation(i);
      
      boolean cRes = c.tryUnroll(logger, block);
      unrolled = unrolled || cRes;

      for (Block b: c.getBlocks()) {
        boolean res = unrollLoops(logger, prog, f, b);
        unrolled = unrolled || res;
      }
    }
    return unrolled;
  }
}
    