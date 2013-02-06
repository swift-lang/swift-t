/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
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
     
    boolean debug = Settings.getBoolean(Settings.COMPILER_DEBUG);
    
    preprocess(icOutput, logger, debug, prog);
    iterate(icOutput, logger, prog, debug, nIterations);
    postprocess(icOutput, logger, debug, prog, nIterations);

    if (logIC) {
      prog.log(icOutput, "Final optimized IC");
    }
    return prog;
  }

  /**
   * Do preprocessing optimizer steps
   * @param icOutput
   * @param logger
   * @param debug 
   * @param program
   * @throws Exception 
   */
  private static void preprocess(PrintStream icOutput, Logger logger,
                         boolean debug, Program program) throws UserException {
    OptimizerPipeline preprocess = new OptimizerPipeline(icOutput);
    // need variable names to be unique for rest of stages
    preprocess.addPass(new UniqueVarNames());
    // Must fix up variables as frontend doesn't do it
    preprocess.addPass(new FlattenNested());
    if (debug)
      preprocess.addPass(Validate.standardValidator());

    preprocess.runPipeline(logger, program, 0);
  }

  /**
   * Do one iteration of the iterative optimizer passes
   * @param icOutput
   * @param logger
   * @param prog
   * @param debug 
   * @param iteration
   * @param nIterations
   * @throws Exception
   */
  private static void iterate(PrintStream icOutput, Logger logger,
      Program prog, boolean debug, long nIterations) throws UserException {
    
    // FunctionInline is stateful
    FunctionInline inliner = new FunctionInline();
    
    for (long iteration = 0; iteration < nIterations; iteration++) {
      OptimizerPipeline pipe = new OptimizerPipeline(icOutput);
      // First prune any unneeded functions
      pipe.addPass(inliner);
      
      pipe.addPass(new ConstantFold());
      
      if ((iteration % 3) == 2) {
        // Try occasionally to unroll loops.  Don't do it on first iteration
        // so the code can be shrunk a little first
        pipe.addPass(new LoopUnroller());
      }
      
      // Try to hoist variables out of loops, etc
      // Do before forward dataflow since it may open up new opportunites
      // switch to aggressive hoisting later on when we have probably done all
      // the other optimizations possible
      boolean aggressive = iteration > (nIterations * 2 / 3);
      pipe.addPass(new HoistLoops(aggressive));
      
      // Try to reorder instructions for benefit of forward dataflow
      pipe.addPass(new ReorderInstructions());
      
      // Do forward dataflow after const folding so it won't create any
      // new constants, etc to be folded
      pipe.addPass(new ForwardDataflow());
      
      // Do this after forward dataflow to improve odds of fusing things
      // one common subexpression elimination has happened
      pipe.addPass(new ContinuationFusion());
  
      // Can only run this pass once. Do it near end so that
      // results can be cleaned up by forward dataflow
      if (iteration == nIterations - (nIterations / 4) - 3) {
        pipe.addPass(new Pipeline());
        if (debug)
          pipe.addPass(Validate.standardValidator());
      }
      
      // Do merges near end since it can be detrimental to other optimizations
      boolean doWaitMerges = iteration == nIterations - (nIterations / 4) - 2;
      pipe.addPass(new WaitCoalescer(doWaitMerges));
      
      if (debug)
        pipe.addPass(Validate.standardValidator());
      
      pipe.runPipeline(logger, prog, iteration);
    }
  }

  private static void postprocess(PrintStream icOutput, Logger logger,
      boolean debug, Program prog, long nIterations) throws UserException {   
    OptimizerPipeline postprocess = new OptimizerPipeline(icOutput);
    postprocess.addPass(new ConstantSharing());
    
    // Add in all the variable passing annotations now that instructions,
    // continuations and variables are fixed
    postprocess.addPass(new FixupVariables());
    // Add in reference counting after passing annotations
    postprocess.addPass(new RefcountPass());
    postprocess.addPass(Validate.finalValidator());
    postprocess.runPipeline(logger, prog,  nIterations - 1);
  }

}
    