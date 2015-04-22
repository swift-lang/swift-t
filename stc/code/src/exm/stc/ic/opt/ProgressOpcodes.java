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

import exm.stc.common.Logging;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.util.StackLite;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;

/**
 * Helper functions and data to determine whether given instructions can
 * be counted as doing "meaningful work".  Currently there are two categories:
 * - Opcodes that don't spawn or enable further work, so can be put off
 * - Opcodes that are not computationally intense, so don't need to run in parallel 
 *
 */
public class ProgressOpcodes {
  
  public static boolean isCheapWorkerInst(Instruction i) {
    // TODO: default worker isn't right
    return i.isCheap() && i.execMode().canRunIn(ExecContext.defaultWorker());
  }

  public static enum Category {
    CHEAP,
    CHEAP_WORKER,
    NON_PROGRESS
  }
  
  /**
   * @param rootBlock
   * @return true if the continuation at the root of the block might do
   *          significant work.
   */
  public static boolean isCheap(Block rootBlock) {
    return blockProgress(rootBlock, Category.CHEAP);
  }
  
  public static boolean isCheapWorker(Block rootBlock) {
    return blockProgress(rootBlock, Category.CHEAP_WORKER);
  }
  
  public static boolean isNonProgress(Block rootBlock) {
    return blockProgress(rootBlock, Category.NON_PROGRESS);
  }
  
  public static boolean isNonProgressWorker(Block rootBlock) {
    // These instructions are all fine to run on worker
    return isNonProgress(rootBlock);
  }
  
  public static boolean isNonProgress(Block rootBlock, ExecContext cx) {
    if (cx.isControlContext()) {
      return isNonProgress(rootBlock);
    } else {
      return isNonProgressWorker(rootBlock);
    }
  }
  
  /**
   * @param rootBlock
   * @return true if the block makes progress of the specified type
   */
  public static boolean blockProgress(Block rootBlock, Category type) {
    Logger logger = Logging.getSTCLogger();
    StackLite<Block> stack = new StackLite<Block>();
    stack.push(rootBlock);
    while (!stack.isEmpty()) {
      Block block = stack.pop();
      
      for (Statement stmt: block.getStatements()) {
        if (stmt.type() == StatementType.INSTRUCTION) {
          Instruction i = stmt.instruction();
          if (type == Category.CHEAP) {
            if (!i.isCheap()) {
              if (logger.isTraceEnabled()) {
                logger.trace("non-cheap instruction found: " + i);
              }
              return false;
            }
          } else if (type == Category.CHEAP_WORKER) {
            if (!isCheapWorkerInst(i)) {
              if (logger.isTraceEnabled()) {
                logger.trace("non-cheap-worker instruction found: " + i);
              }
              return false;
            }
          } else {
            assert(type == Category.NON_PROGRESS);
            if (i.isProgressEnabling() || !i.isCheap()) {
              if (logger.isTraceEnabled()) {
                logger.trace("progress instruction found: " + i);
              }
              return false;
            }
          }
        }
      }
      
      for (Continuation c: block.allComplexStatements()) {
        if (!c.isAsync()) {
          for (Block inner: c.getBlocks()) {
            stack.push(inner);
          }
        }
      }
    }
    return true;
  }
}

