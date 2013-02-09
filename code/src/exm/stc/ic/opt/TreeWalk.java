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

import exm.stc.common.lang.Var;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
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
      walk(logger, f.getMainblock(), f, walker, true);
    }
  }
  
  /**
   * Walk pre-order
   * @param logger
   * @param block
   * @param function
   * @param walker
   * @param recursive if false, don't visit child blocks
   */
  public static void walk(Logger logger, Block block, Function function,
                          TreeWalker walker, boolean recursive) {
    for (Var declared: block.getVariables()) {
      walker.visitDeclaration(declared);
    }
    for (Instruction i: block.getInstructions()) {
      walker.visit(logger, function, i);
    }
    
    for (Continuation c: block.getContinuations()) {
      walker.visit(logger, function, c);
      if (recursive) {
        for (Block b: c.getBlocks()) {
          walk(logger, b, function, walker, recursive);
        }
      }
    }
    
    for (CleanupAction ca: block.getCleanups()) {
      walker.visit(logger, function, ca);
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
        walker.visit(logger, fn, i);
      }
      
      for (Continuation c: curr.getContinuations()) {
        walker.visit(logger, fn, c);
        if (!c.isAsync()) {
          stack.addAll(c.getBlocks());
        }
      }
    }
  }

  public static abstract class TreeWalker {
    public void visit(Logger logger, Function functionContext, Continuation cont) {
      visit(cont);
    }
    protected void visit(Continuation cont) {
      // Nothing
    }
    
    public void visit(Logger logger, Function functionContext, Block block) {
      visit(block);
    }
    protected void visit(Block block) {
      // Nothing
    }
    
    public void visitDeclaration(Logger logger, Function functionContext, Var declared) {
      visitDeclaration(declared);
    }
    protected void visitDeclaration(Var declared) {
      // Nothing
    }
    
    public void visit(Logger logger, Function functionContext, Instruction inst) {
      visit(inst);
    }
    protected void visit(Instruction inst) {
      // nothing
    }
    
    public void visit(Logger logger, Function functionContext, CleanupAction cleanup) {
      visit(cleanup);
    }
    protected void visit(CleanupAction cleanup) {
      // nothing
    }
  }
}
