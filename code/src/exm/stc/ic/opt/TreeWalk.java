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
 * Copyright [yyyy] [name of copyright owner]
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
 * limitations under the License..
 */
package exm.stc.ic.opt;

import org.apache.log4j.Logger;

import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

public class TreeWalk {

  /**
   * Top-down tree walk
   * @param logger
   * @param prog
   * @param walker
   */
  public static void walk(Logger logger, Program prog, TreeWalker walker) {
    for (Function f: prog.getFunctions()) {
      walk(logger, f.getMainblock(), f.getName(), walker);
    }
  }
  
  public static void walk(Logger logger, Block block, String function,
                          TreeWalker walker) {
    for (Instruction i: block.getInstructions()) {
      walker.visit(logger, function, i);
    }
    for (Continuation c: block.getContinuations()) {
      for (Block b: c.getBlocks()) {
        walk(logger, b, function, walker);
      }
    }
  }

  public interface TreeWalker {
    public void visit(Logger logger, String functionContext, Instruction inst);
  }
}
