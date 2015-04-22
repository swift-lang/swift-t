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

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;

public class FlattenNested extends FunctionOptimizerPass {
  @Override
  public String getPassName() {
    return "Flatten nested";
  }

  @Override
  public String getConfigEnabledKey() {
    return null;
  }

  /**
   * Remove all nested blocks from program
   * Precondition: all variable names in functions should be unique
   * @param f
   */
  @Override
  public void optimize(Logger logger, Function f) {
    flattenNestedBlocks(f.mainBlock());
  }
  
  public static void flattenNestedBlocks(Block block) {
    List<Continuation> originalContinuations =
          new ArrayList<Continuation>(block.getContinuations());
    // Stick any nested blocks instructions into the main thing
    for (Continuation c: originalContinuations) {
      switch (c.getType()) {
      case NESTED_BLOCK:
        assert(c.getBlocks().size() == 1);
        if (!c.runLast()) {
          Block inner = c.getBlocks().get(0);
          flattenNestedBlocks(inner);
          c.inlineInto(block, inner);
        }
        break;
      default:
        // Recursively flatten any blocks inside the continuation
        for (Block b: c.getBlocks()) {
          flattenNestedBlocks(b);
        }
      }

    }
  }
}
