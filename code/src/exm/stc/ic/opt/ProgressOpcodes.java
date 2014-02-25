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
import java.util.HashSet;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.TaskMode;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.LocalFunctionCall;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;
import exm.stc.ic.tree.Opcode;

/**
 * Helper functions and data to determine whether given instructions can
 * be counted as doing "meaningful work".  Currently there are two categories:
 * - Opcodes that don't spawn or enable further work, so can be put off
 * - Opcodes that are not computationally intense, so don't need to run in parallel 
 * @author tim
 *
 */
public class ProgressOpcodes {
  
  public static boolean isNonProgressOpcode(Opcode op) {
    return nonProgressOpcodes.contains(op);
  }
  
  public static boolean isCheapOpcode(Opcode op) {
    return cheapOpcodes.contains(op);
  }
  
  public static boolean isCheapWorkerOpcode(Opcode op) {
    return cheapWorkerOpcodes.contains(op);
  }
  
  public static boolean isCheapWorkerInst(Instruction i) {
    if (isCheapWorkerOpcode(i.op)) {
      return true;
    } else if (i.op == Opcode.CALL_FOREIGN_LOCAL) {
      String fnName = ((LocalFunctionCall)i).functionName();
      TaskMode fnMode = ForeignFunctions.getTaskMode(fnName);
      if (fnMode == TaskMode.LOCAL) {
        return true;
      }
    }
    return false;
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
    if (cx == ExecContext.CONTROL) {
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
    Deque<Block> stack = new ArrayDeque<Block>();
    stack.add(rootBlock);
    while (!stack.isEmpty()) {
      Block block = stack.pop();
      
      for (Statement stmt: block.getStatements()) {
        if (stmt.type() == StatementType.INSTRUCTION) {
          Instruction i = stmt.instruction();
          if (type == Category.CHEAP) {
            if (!isCheapOpcode(i.op)) {
              if (logger.isTraceEnabled()) {
                logger.trace("progress instruction found: " + i);
              }
              return false;
            }
          } else if (type == Category.CHEAP_WORKER) {
            if (!isCheapWorkerInst(i)) {
              if (logger.isTraceEnabled()) {
                logger.trace("progress instruction found: " + i);
              }
              return false;
            }
          } else {
            assert(type == Category.NON_PROGRESS);
            if (!isNonProgressOpcode(i.op)) {
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
  

  /**
   * Opcodes which we don't consider as making "progress", i.e.
   * won't enable further work to run.  There are all ok to
   * use on workers.
   */
  private static HashSet<Opcode> nonProgressOpcodes = initNonProgress();
  
  private static HashSet<Opcode> initNonProgress() { 
    HashSet<Opcode> opcodes = new HashSet<Opcode>();
    opcodes.add(Opcode.DECR_WRITERS);
    opcodes.add(Opcode.FREE_BLOB);
    opcodes.add(Opcode.DECR_READERS);
    opcodes.add(Opcode.INCR_READERS);
    opcodes.add(Opcode.INCR_WRITERS);
    opcodes.add(Opcode.LOCAL_OP);
    opcodes.add(Opcode.COPY_REF);
    opcodes.add(Opcode.STORE_REF);
    opcodes.add(Opcode.LOAD_SCALAR);
    opcodes.add(Opcode.LOAD_REF);
    opcodes.add(Opcode.LOAD_FILE);
    opcodes.add(Opcode.LOAD_ARRAY);
    opcodes.add(Opcode.LOAD_BAG);
    opcodes.add(Opcode.LOAD_RECURSIVE);
    opcodes.add(Opcode.GET_FILENAME);
    opcodes.add(Opcode.GET_LOCAL_FILENAME);
    opcodes.add(Opcode.IS_MAPPED);
    opcodes.add(Opcode.CHOOSE_TMP_FILENAME);
    opcodes.add(Opcode.INIT_LOCAL_OUTPUT_FILE);
    opcodes.add(Opcode.COMMENT);
    opcodes.add(Opcode.STRUCT_RETRIEVE);
    opcodes.add(Opcode.STRUCT_CREATE_ALIAS);
    opcodes.add(Opcode.LOOP_BREAK);
    return opcodes;
  }
  
  /**
   * Opcodes that don't use much time or CPU
   */
  private static HashSet<Opcode> cheapOpcodes = initCheap();
  
  /**
   * Opcodes that don't use much time or CPU and can run on worker
   */
  private static HashSet<Opcode> cheapWorkerOpcodes = initCheapWorker();
  

  private static HashSet<Opcode> initCheapWorker() { 
    HashSet<Opcode> opcodes = new HashSet<Opcode>();
    // Avoid opcodes that involve entering data dependencies
    opcodes.add(Opcode.COMMENT);
    opcodes.add(Opcode.DECR_WRITERS);
    opcodes.add(Opcode.FREE_BLOB);
    opcodes.add(Opcode.DECR_READERS);
    opcodes.add(Opcode.INCR_READERS);
    opcodes.add(Opcode.INCR_WRITERS);
    opcodes.add(Opcode.CALL_LOCAL);
    opcodes.add(Opcode.STORE_SCALAR);
    opcodes.add(Opcode.COPY_REF);
    opcodes.add(Opcode.STORE_REF);
    opcodes.add(Opcode.LOAD_SCALAR);
    opcodes.add(Opcode.LOAD_FILE);
    opcodes.add(Opcode.LOAD_REF);
    opcodes.add(Opcode.LOAD_ARRAY);
    opcodes.add(Opcode.LOAD_BAG);
    opcodes.add(Opcode.LOAD_RECURSIVE);
    opcodes.add(Opcode.ARR_CREATE_NESTED_IMM);
    opcodes.add(Opcode.ARR_STORE_FUTURE);
    opcodes.add(Opcode.ARR_COPY_IN_FUTURE);
    opcodes.add(Opcode.ARR_COPY_OUT_FUTURE);
    opcodes.add(Opcode.ARR_STORE);
    opcodes.add(Opcode.ARR_COPY_IN_IMM);
    opcodes.add(Opcode.ARR_RETRIEVE);
    opcodes.add(Opcode.ARR_COPY_OUT_IMM);
    opcodes.add(Opcode.AREF_STORE_IMM);
    opcodes.add(Opcode.AREF_COPY_IN_IMM);
    opcodes.add(Opcode.AREF_STORE_FUTURE);
    opcodes.add(Opcode.AREF_COPY_IN_FUTURE);
    opcodes.add(Opcode.STRUCT_STORE);
    opcodes.add(Opcode.STRUCT_COPY_IN);
    opcodes.add(Opcode.STRUCTREF_STORE);
    opcodes.add(Opcode.STRUCTREF_COPY_IN);
    opcodes.add(Opcode.STRUCT_RETRIEVE);
    opcodes.add(Opcode.STRUCT_COPY_OUT);
    opcodes.add(Opcode.STRUCTREF_COPY_OUT);
    opcodes.add(Opcode.BAG_INSERT);
    opcodes.add(Opcode.ARRAY_CREATE_BAG);
    opcodes.add(Opcode.COPY_REF);
    opcodes.add(Opcode.LOCAL_OP);
    opcodes.add(Opcode.GET_FILENAME);
    opcodes.add(Opcode.GET_LOCAL_FILENAME);
    opcodes.add(Opcode.IS_MAPPED);
    opcodes.add(Opcode.SET_FILENAME_VAL);
    opcodes.add(Opcode.INIT_LOCAL_OUTPUT_FILE);
    return opcodes;
  }
  
  private static HashSet<Opcode> initCheap() { 
    HashSet<Opcode> opcodes = initCheapWorker();

    opcodes.add(Opcode.CALL_LOCAL_CONTROL);
    opcodes.add(Opcode.ARR_CREATE_NESTED_FUTURE);
    opcodes.add(Opcode.AREF_CREATE_NESTED_IMM);
    opcodes.add(Opcode.AREF_CREATE_NESTED_FUTURE);
    opcodes.add(Opcode.AREF_STORE_FUTURE);
    opcodes.add(Opcode.AREF_COPY_OUT_FUTURE);
    opcodes.add(Opcode.AREF_COPY_OUT_IMM);
    
    // Spawning tasks is cheap
    opcodes.add(Opcode.ASYNC_OP);
    opcodes.add(Opcode.CALL_CONTROL);
    opcodes.add(Opcode.CALL_LOCAL);
    opcodes.add(Opcode.CALL_LOCAL_CONTROL);
    opcodes.add(Opcode.CALL_FOREIGN);

    // Breaking from loop is cheap
    opcodes.add(Opcode.LOOP_BREAK);
    return opcodes;
  }
}

