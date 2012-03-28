package exm.parser.ic;

import java.io.PrintStream;

import org.apache.log4j.Logger;

import exm.parser.Settings;
import exm.parser.ic.ICContinuations.Continuation;
import exm.parser.ic.SwiftIC.Block;
import exm.parser.ic.SwiftIC.CompFunction;
import exm.parser.ic.SwiftIC.Program;
import exm.parser.ic.opt.ConstantFinder;
import exm.parser.ic.opt.Flattener;
import exm.parser.ic.opt.ForwardDataflow;
import exm.parser.util.InvalidOptionException;
import exm.parser.util.InvalidWriteException;
import exm.parser.util.ParserRuntimeException;

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
        
        // Do this at end as it won't create any new constants, etc to be folded
        if (Settings.getBoolean(Settings.OPT_FORWARD_DATAFLOW)) {
          ForwardDataflow.forwardDataflow(logger, prog);
          if (logIC) {
            prog.log(icOutput, "Pass " + pass + ":" +
                          "IC after available var analysis");
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
      throw new ParserRuntimeException("Optimizer setting not correct in "
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
        Flattener.makeVarNamesUnique(f);
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
    