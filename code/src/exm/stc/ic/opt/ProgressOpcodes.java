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

import exm.stc.common.lang.Builtins;
import exm.stc.common.lang.TaskMode;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.LocalFunctionCall;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;

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
    } else if (i.op == Opcode.CALL_BUILTIN_LOCAL) {
      TaskMode fnMode = Builtins.getTaskMode(((LocalFunctionCall)i).getFunctionName());
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
  
  /**
   * block
   * @param rootBlock
   * @return
   */
  public static boolean blockProgress(Block rootBlock, Category type) {
    Deque<Block> stack = new ArrayDeque<Block>();
    stack.add(rootBlock);
    while (!stack.isEmpty()) {
      Block block = stack.pop();
      
      for (Statement stmt: block.getStatements()) {
        if (stmt.type() == StatementType.INSTRUCTION) {
          Instruction i = stmt.instruction();
          if (type == Category.CHEAP) {
            if (!isCheapOpcode(i.op)) {
              return false;
            }
          } else if (type == Category.CHEAP_WORKER) {
            if (!isCheapWorkerInst(i)) {
              return false;
            }
          } else {
            assert(type == Category.NON_PROGRESS);
            if (!isNonProgressOpcode(i.op)) {
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
   * won't enable further work to run
   */
  private static HashSet<Opcode> nonProgressOpcodes = initNonProgress();
  
  private static HashSet<Opcode> initNonProgress() { 
    HashSet<Opcode> opcodes = new HashSet<Opcode>();
    opcodes.add(Opcode.DECR_WRITERS);
    opcodes.add(Opcode.FREE_BLOB);
    opcodes.add(Opcode.DECR_REF);
    opcodes.add(Opcode.INCR_REF);
    opcodes.add(Opcode.INCR_WRITERS);
    opcodes.add(Opcode.LOCAL_OP);
    opcodes.add(Opcode.COPY_REF);
    opcodes.add(Opcode.STORE_REF);
    opcodes.add(Opcode.LOAD_BOOL);
    opcodes.add(Opcode.LOAD_VOID);
    opcodes.add(Opcode.LOAD_FLOAT);
    opcodes.add(Opcode.LOAD_INT);
    opcodes.add(Opcode.LOAD_REF);
    opcodes.add(Opcode.LOAD_STRING);
    opcodes.add(Opcode.LOAD_BLOB);
    opcodes.add(Opcode.LOAD_FILE);
    opcodes.add(Opcode.GET_FILENAME);
    opcodes.add(Opcode.GET_OUTPUT_FILENAME);
    opcodes.add(Opcode.CHOOSE_TMP_FILENAME);
    opcodes.add(Opcode.INIT_LOCAL_OUTPUT_FILE);
    opcodes.add(Opcode.COMMENT);
    opcodes.add(Opcode.STRUCT_INSERT);
    opcodes.add(Opcode.STRUCT_LOOKUP);
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
    opcodes.add(Opcode.DECR_REF);
    opcodes.add(Opcode.INCR_REF);
    opcodes.add(Opcode.INCR_WRITERS);
    opcodes.add(Opcode.CALL_LOCAL);
    opcodes.add(Opcode.STORE_BOOL);
    opcodes.add(Opcode.STORE_VOID);
    opcodes.add(Opcode.STORE_INT);
    opcodes.add(Opcode.STORE_FLOAT);
    opcodes.add(Opcode.STORE_STRING);
    opcodes.add(Opcode.STORE_BLOB);
    opcodes.add(Opcode.COPY_REF);
    opcodes.add(Opcode.STORE_REF);
    opcodes.add(Opcode.LOAD_BOOL);
    opcodes.add(Opcode.LOAD_VOID);
    opcodes.add(Opcode.LOAD_FILE);
    opcodes.add(Opcode.LOAD_FILE);
    opcodes.add(Opcode.LOAD_FLOAT);
    opcodes.add(Opcode.LOAD_INT);
    opcodes.add(Opcode.LOAD_REF);
    opcodes.add(Opcode.LOAD_STRING);
    opcodes.add(Opcode.LOAD_BLOB);
    opcodes.add(Opcode.LOAD_FILE);
    opcodes.add(Opcode.ARRAY_CREATE_NESTED_IMM);
    opcodes.add(Opcode.ARRAY_INSERT_FUTURE);
    opcodes.add(Opcode.ARRAY_DEREF_INSERT_FUTURE);
    opcodes.add(Opcode.ARRAY_LOOKUP_FUTURE);
    opcodes.add(Opcode.ARRAY_INSERT_IMM);
    opcodes.add(Opcode.ARRAY_DEREF_INSERT_IMM);
    opcodes.add(Opcode.ARRAY_LOOKUP_IMM);
    opcodes.add(Opcode.ARRAY_LOOKUP_REF_IMM);
    opcodes.add(Opcode.ARRAYREF_INSERT_IMM);
    opcodes.add(Opcode.ARRAYREF_DEREF_INSERT_IMM);
    opcodes.add(Opcode.ARRAYREF_INSERT_FUTURE);
    opcodes.add(Opcode.ARRAYREF_DEREF_INSERT_FUTURE);
    opcodes.add(Opcode.COPY_REF);
    opcodes.add(Opcode.LOCAL_OP);
    return opcodes;
  }
  
  private static HashSet<Opcode> initCheap() { 
    HashSet<Opcode> opcodes = initCheapWorker();

    opcodes.add(Opcode.CALL_LOCAL_CONTROL);
    opcodes.add(Opcode.GET_FILENAME);
    opcodes.add(Opcode.GET_OUTPUT_FILENAME);
    opcodes.add(Opcode.SET_FILENAME_VAL);
    opcodes.add(Opcode.ARRAY_CREATE_NESTED_FUTURE);
    opcodes.add(Opcode.ARRAYREF_CREATE_NESTED_IMM);
    opcodes.add(Opcode.ARRAYREF_CREATE_NESTED_FUTURE);
    opcodes.add(Opcode.ARRAYREF_INSERT_FUTURE);
    opcodes.add(Opcode.ARRAYREF_LOOKUP_FUTURE);
    opcodes.add(Opcode.ARRAYREF_LOOKUP_IMM);
    
    // Spawning tasks is cheap
    opcodes.add(Opcode.ASYNC_OP);
    opcodes.add(Opcode.CALL_CONTROL);
    opcodes.add(Opcode.CALL_LOCAL);
    opcodes.add(Opcode.CALL_LOCAL_CONTROL);
    opcodes.add(Opcode.CALL_BUILTIN);
    return opcodes;
  }
}

