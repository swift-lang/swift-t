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

import java.util.ArrayDeque;
import java.util.Deque;

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
  
  public static void walkSyncChildren(
      Logger logger, Function fn,
      Block block, boolean includeThisBlock, TreeWalker walker) {
    Deque<Block> stack = new ArrayDeque<Block>();
    if (includeThisBlock) {
      stack.push(block);
    } else {
      for (Continuation c: block.getContinuations()) {
        if (!c.isAsync()) {
          stack.addAll(c.getBlocks());
        }
      }
    }
    while (!stack.isEmpty()) {
      Block curr = stack.pop();
      for (Instruction i: curr.getInstructions()) {
        walker.visit(logger, fn.getName(), i);
      }
      
      for (Continuation c: curr.getContinuations()) {
        walker.visit(logger, fn.getName(), c);
        if (!c.isAsync()) {
          stack.addAll(c.getBlocks());
        }
      }
    }
  }

  public static abstract class TreeWalker {
    public void visit(Logger logger, String functionContext, Continuation cont) {
      visit(cont);
    }
    private void visit(Continuation cont) {
      // Nothing
    }
    public void visit(Logger logger, String functionContext, Instruction inst) {
      visit(inst);
    }
    private void visit(Instruction inst) {
      // nothing
    }
    
  }
}
