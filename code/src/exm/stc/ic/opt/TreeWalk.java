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

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Var;
import exm.stc.common.util.StackLite;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.Statement;

public class TreeWalk {

  /**
   * Top-down tree walk
   * @param logger
   * @param prog
   * @param walker
   */
  public static void walk(Logger logger, Program prog, TreeWalker walker) {
    for (Function function: prog.functions()) {
      walk(logger, function, walker);
    }
  }

  public static void walk(Logger logger, Function function, TreeWalker walker) {
    walk(logger, function.mainBlock(), function, walker, true);
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
    for (Var declared: block.variables()) {
      walker.visitDeclaration(declared);
    }
    for (Statement stmt: block.getStatements()) {
      switch (stmt.type()) {
        case INSTRUCTION:
          walker.visit(logger, function, stmt.instruction());
          break;
        case CONDITIONAL:
          walk(logger, function, stmt.conditional(), recursive, walker);
          break;
        default:
          throw new STCRuntimeError("Unknown statement type" + stmt.type());
      }
    }

    for (Continuation c: block.getContinuations()) {
      walk(logger, function, c, recursive, walker);
    }

    for (CleanupAction ca: block.getCleanups()) {
      walker.visit(logger, function, ca);
    }
  }

  public static void walk(Logger logger, Function function, Continuation cont,
      boolean recursive, TreeWalker walker) {
    walker.visit(logger, function, cont);
    if (recursive) {
      for (Block b: cont.getBlocks()) {
        walk(logger, b, function, walker, recursive);
      }
    }
  }


  /**
   * Visits this continuation and any descendants that execute synchronously
   * @param logger
   * @param fn
   * @param cont
   * @param walker
   */
  public static void walkSyncChildren(Logger logger, Function fn,
      Continuation cont, TreeWalker walker) {
    walker.visit(cont);
    if (!cont.isAsync()) {
      for (Block block: cont.getBlocks()) {
        walkSyncChildren(logger, fn, block, true, walker);
      }
    }
  }

  public static void walkSyncChildren(
      Logger logger, Function fn,
      Block block, boolean includeThisBlock, TreeWalker walker) {
    StackLite<Block> stack = new StackLite<Block>();
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
      for (Statement stmt: curr.getStatements()) {
        switch (stmt.type()) {
          case INSTRUCTION:
            walker.visit(logger, fn, stmt.instruction());
            break;
          case CONDITIONAL:
            assert(!stmt.conditional().isAsync());
            walkSyncChildren(logger, fn, walker, stack, stmt.conditional());
            break;
          default:
            throw new STCRuntimeError("Unknown statement type " + stmt.type());
        }
      }

      for (Continuation c: curr.getContinuations()) {
        walkSyncChildren(logger, fn, walker, stack, c);
      }
    }
  }

  private static void walkSyncChildren(Logger logger, Function fn,
      TreeWalker walker, StackLite<Block> stack, Continuation cont) {
    walker.visit(logger, fn, cont);
    if (!cont.isAsync()) {
      stack.addAll(cont.getBlocks());
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
