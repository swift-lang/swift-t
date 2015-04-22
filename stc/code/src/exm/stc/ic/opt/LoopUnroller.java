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

import java.util.List;
import java.util.ListIterator;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.util.Pair;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

public class LoopUnroller implements OptimizerPass {
  @Override
  public String getPassName() {
    return "Unroll loops";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_UNROLL_LOOPS;
  }

  @Override
  public void optimize(Logger logger, Program prog) {
    for (Function f: prog.functions()) {
      logger.debug("looking to unroll loops in " + f.id());
      if (unrollLoops(logger, prog, f, f.mainBlock())) {
        // Unrolling can introduce duplicate vars
        UniqueVarNames.makeVarNamesUnique(f, prog.allGlobals());
        FlattenNested.flattenNestedBlocks(f.mainBlock());
      }
    }
  }

  private static boolean unrollLoops(Logger logger, Program prog, Function f,
      Block block) {
    boolean unrolled = false;

    ListIterator<Continuation> it = block.continuationIterator();
    while (it.hasNext()) {
      Continuation c = it.next();
      // Doing from bottom up gives us better estimate of inner loop size after expansion
      for (Block b: c.getBlocks()) {
        boolean res = unrollLoops(logger, prog, f, b);
        unrolled = unrolled || res;
      }
      Pair<Boolean, List<Continuation>> cRes;
      cRes = c.tryUnroll(logger, f.id(), block);
      if (cRes.val1) {
        unrolled = true;
        for (Continuation newC: cRes.val2) {
          it.add(newC);
        }
      }
    }
    return unrolled;
  }
}
