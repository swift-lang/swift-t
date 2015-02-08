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
import exm.stc.common.exceptions.InvalidWriteException;
import exm.stc.common.exceptions.UserException;
import exm.stc.ic.opt.valuenumber.ValueNumber;
import exm.stc.ic.refcount.RefcountPass;
import exm.stc.ic.tree.ICTree.Program;

public class ICOptimizer {

  /**
   * If true, validate as frequently as possible
   */
  public static final boolean SUPER_DEBUG = true;

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

    long nIterations = Settings.getLongUnchecked(Settings.OPT_MAX_ITERATIONS);

    boolean debug = Settings.getBooleanUnchecked(Settings.COMPILER_DEBUG);

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

    // Cut down size of IR right away
    preprocess.addPass(new PruneFunctions());

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
    boolean canReorder = true;

    for (long iteration = 0; iteration < nIterations; iteration++) {
      OptimizerPipeline pipe = new OptimizerPipeline(icOutput);
      if (SUPER_DEBUG) {
        pipe.setValidator(Validate.standardValidator());
      }

      // First prune and inline any functions
      if (iteration == nIterations / 2) {
        // Only makes sense to do periodically
        pipe.addPass(new PruneFunctions());
      }
      if (iteration == 0 || iteration == 3 || iteration == nIterations - 2) {
        pipe.addPass(inliner);
      }


      if ((iteration % 3) == 2) {
        // Try to merge instructions into array build
        pipe.addPass(new ArrayBuild());
        pipe.addPass(new StructBuild());

        // Try occasionally to unroll loops.  Don't do it on first iteration
        // so the code can be shrunk a little first
        pipe.addPass(new LoopUnroller());
        pipe.addPass(Validate.standardValidator());
      }

      boolean lastHalf = iteration > nIterations * 2;

      // Try to hoist variables out of loops, etc
      // Do before forward dataflow since it may open up new opportunites
      // switch to aggressive hoisting later on when we have probably done all
      // the other optimizations possible
      if (canReorder) {
        pipe.addPass(new HoistLoops(lastHalf));
      }

      // Try to reorder instructions for benefit of forward dataflow
      // Don't do every iteration, instructions are first
      // in original order, then in a different but valid order
      if (canReorder && (iteration % 2 == 1)) {
        pipe.addPass(new ReorderInstructions());
      }

      if (iteration % 3 == 0) {
        pipe.addPass(new PropagateAliases());
      }

      if (iteration == nIterations - 2) {
        // Towards end, inline explicit waits and disallow reordering
        canReorder = false;
      }
      // ValueNumber is a key pass that reduces a lot of redundancy
      pipe.addPass(new ValueNumber(canReorder));

      // This loop optimization depends on info updated by ValueNumber,
      // but can generate dead code
      pipe.addPass(new LoopSimplify());

      // ValueNumber tends to generate most dead code
      pipe.addPass(new DeadCodeEliminator());

      if (iteration % 3 == 0) {
        // Dead code eliminator will have just eliminated references
        pipe.addPass(new DemoteGlobals());
      }

      // ValueNumber adds blocking vars to function
      pipe.addPass(new FunctionSignature());

      // Do this after forward dataflow to improve odds of fusing things
      // one common subexpression elimination has happened
      pipe.addPass(new ContinuationFusion());

      // Can only run this pass once. Do it near end so that
      // results can be cleaned up by forward dataflow
      if (iteration == nIterations - (nIterations / 4) - 1) {
        pipe.addPass(new Pipeline());
        if (debug)
          pipe.addPass(Validate.standardValidator());
      }

      // Expand ops about halfway through
      boolean doInlineOps = iteration == nIterations / 2;
      if (doInlineOps) {
        pipe.addPass(new DataflowOpInline());
      }

      // Do merges near end since it can be detrimental to other optimizations
      boolean doWaitMerges = (iteration >= nIterations - (nIterations / 4) - 2)
                              && iteration % 2 == 0;
      pipe.addPass(new WaitCoalescer(doWaitMerges, canReorder));

      if (debug)
        pipe.addPass(Validate.standardValidator());

      pipe.runPipeline(logger, prog, iteration);

      // Cleanup internal indices, etc.
      prog.cleanup();
    }
  }

  private static void postprocess(PrintStream icOutput, Logger logger,
      boolean debug, Program prog, long nIterations) throws UserException {
    OptimizerPipeline postprocess = new OptimizerPipeline(icOutput);

    // Final dead code elimination to clean up any remaining dead code
    // (from last iteration or constant sharing)
    postprocess.addPass(new DeadCodeEliminator());

    // Final pruning to remove unused functions
    postprocess.addPass(new PruneFunctions());

    // Add in all the variable passing annotations now that instructions,
    // continuations and variables are fixed
    postprocess.addPass(new FixupVariables());
    // Add in reference counting after passing annotations
    postprocess.addPass(new RefcountPass());
    // Refcount pass sometimes adds instructions, do another fixup as a
    // workaround to make sure that passing annotations are still correct
    postprocess.addPass(new FixupVariables());

    postprocess.addPass(Validate.finalValidator());
    postprocess.runPipeline(logger, prog,  nIterations - 1);
  }

}
