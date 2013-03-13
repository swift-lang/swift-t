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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.Pair;
import exm.stc.ic.tree.ICContinuations.ContVarDefType;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

/**
 * Fix up passInVars and keepOpenVars in IC.  Perform validation
 * to make sure variables are visible.
 */
public class FixupVariables implements OptimizerPass {

  @Override
  public String getPassName() {
    return "Fixup variable passing";
  }

  @Override
  public String getConfigEnabledKey() {
    return null;
  }

  @Override
  public void optimize(Logger logger, Program program) {
    fixupProgram(logger, program, true);
  }

  /**
   * Fix up any variables missing from the usedVariables passed through
   * continuations. This is useful because it is easier to write other
   * optimizations if they are allowed to mess up the usedVariables
   * @param updateLists modify tree and update pass and keep open lists
   */
  public static void fixupProgram(Logger logger, Program prog,
                                  boolean updateLists) {
    Set<Var> referencedGlobals = new HashSet<Var>();
    for (Function fn : prog.getFunctions()) {
      fixupFunction(logger, prog, fn, referencedGlobals, updateLists);
    }
    
    if (updateLists)
      removeUnusedGlobals(prog, referencedGlobals);
  }

  public static void fixupFunction(Logger logger, Program prog,
          Function fn, Set<Var> referencedGlobals, boolean updateLists) {
    HierarchicalSet<Var> fnargs = new HierarchicalSet<Var>();
    for (Var v : fn.getInputList()) {
      fnargs.add(v);
    }
    for (Var v : fn.getOutputList()) {
      fnargs.add(v);
    }
    for (Entry<String, Arg> e : prog.getGlobalConsts().entrySet()) {
      Arg a = e.getValue();
      Var v = new Var(a.getType(), e.getKey(),
          VarStorage.GLOBAL_CONST, DefType.GLOBAL_CONST, null);
      fnargs.add(v);
    }
    Pair<Set<Var>, Set<Var>> res = fixupBlockRec(logger,
        fn, fn.getMainblock(), ExecContext.CONTROL, 
        fnargs, referencedGlobals, updateLists);

    Set<Var> read = res.val1;
    Set<Var> written = res.val2;
    
    if (updateLists) {
      // Mark write-only outputs
      for (int i = 0; i < fn.getOutputList().size(); i++) {
        Var output = fn.getOutput(i);
        if (!read.contains(output)) {
          fn.makeOutputWriteOnly(i);
        }
      }
    }
    // Check that all variables referred to are available as args
    read.removeAll(fn.getInputList());
    read.removeAll(fn.getOutputList());
  
    if (read.size() > 0) {
      throw new STCRuntimeError("Reference in IC function "
          + fn.getName() + " to undefined variables " + read.toString());
    }
    
    written.removeAll(fn.getOutputList());
    
    for (Var v: fn.getInputList()) {
      // TODO: should these be passed in through output list instead?
      if (Types.isScalarUpdateable(v.type())) {
        written.remove(v);
      }
    }
    if (written.size() > 0) {
      throw new STCRuntimeError("Unexpected write IC function "
          + fn.getName() + " to variables " + written.toString());
    }
  }

  /**
   * 
   * @param logger
   * @param block 
   * @param visible all
   *          variables logically visible in this block. will be modified in fn
   * @param referencedGlobals updated with names of any globals used
   * @param updateLists 
   * @return
   */
  private static Pair<Set<Var>, Set<Var>> fixupBlockRec(Logger logger,
      Function function, Block block, ExecContext execCx, 
      HierarchicalSet<Var> visible,
      Set<Var> referencedGlobals, boolean updateLists) {

    if (updateLists)
      // Remove global imports to be readded later if needed
      removeGlobalImports(block);
    
    // blockVars: variables defined in this block
    Set<Var> blockVars = new HashSet<Var>();
    
    // update block variables and visible variables
    for (Var v: block.getVariables()) {
      blockVars.add(v);
      visible.add(v);
    }

    // Work out which variables are read/writte which aren't locally declared
    Set<Var> read = new HashSet<Var>();
    Set<Var> written = new HashSet<Var>();
    findBlockNeeded(block, read, written);
    
    for (Continuation c : block.getContinuations()) {
      fixupContinuationRec(logger, function, execCx, c,
              visible, referencedGlobals,
              blockVars, read, written, updateLists);
    }

    // Outer scopes don't have anything to do with vars declared here
    read.removeAll(blockVars);
    written.removeAll(blockVars);
    
    if (execCx == ExecContext.CONTROL) {
      // Global constants can be imported in control blocks only
      Set<Var> globals = addGlobalImports(block, visible, updateLists,
                                          Arrays.asList(read, written));
  
      referencedGlobals.addAll(globals);
      read.removeAll(globals);
      written.removeAll(globals);
    }
    return Pair.create(read, written);
  }

  /**
   * Find all referenced vars in scope
   * @param block
   * @param written accumulate output vars
   * @param read accumulate all input vars from this block
   */
  private static void findBlockNeeded(Block block, Set<Var> read,
      Set<Var> written) {
    for (Var v: block.getVariables()) {
      if (v.isMapped()) {
        read.add(v.mapping());
      }
    }
    
    for (Instruction i: block.getInstructions()) {
      for (Arg in: i.getInputs()) {
        if (in.isVar()) {
          read.add(in.getVar());
        }
      }
      written.addAll(i.getOutputs());
    }
    
    for (Continuation cont: block.getContinuations()) {
      read.addAll(cont.requiredVars(false));
    }
    
    for (CleanupAction cleanup: block.getCleanups()) {
      // ignore outputs - the cleaned up vars should already be in scope
      for (Arg in: cleanup.action().getInputs()) {
        if (in.isVar()) {
          read.add(in.getVar());
        }
      }
    }
  }

  /**
   * Update variable passing for nested continuation
   * @param logger
   * @param function
   * @param outerCx exec context outside of continuation
   * @param continuation
   * @param visible
   * @param referencedGlobals
   * @param outerBlockVars
   * @param neededVars
   * @param updateLists 
   */
  private static void fixupContinuationRec(Logger logger, Function function,
          ExecContext outerCx,
          Continuation continuation, HierarchicalSet<Var> visible,
          Set<Var> referencedGlobals, Set<Var> outerBlockVars,
          Set<Var> read, Set<Var> written, boolean updateLists) {
    // First see what variables the continuation defines inside itself
    List<Var> constructVars = continuation.constructDefinedVars(ContVarDefType.NEW_DEF);
    ExecContext innerCx = continuation.childContext(outerCx);
    
    for (Block innerBlock : continuation.getBlocks()) {
      HierarchicalSet<Var> childVisible = visible.makeChild();
      for (Var v : constructVars) {
        childVisible.add(v);
      }
      
      Pair<Set<Var>, Set<Var>> inner = fixupBlockRec(logger,
          function, innerBlock, innerCx,
          childVisible, referencedGlobals, updateLists);
      Set<Var> innerRead = inner.val1;
      Set<Var> innerWritten = inner.val2;
      
      // construct will provide some vars
      if (!constructVars.isEmpty()) {
        innerRead.removeAll(constructVars);
        innerWritten.removeAll(constructVars);
      }

      if (continuation.inheritsParentVars()) {
        // Might be some variables not yet defined in this scope
        innerRead.removeAll(outerBlockVars);
        read.addAll(innerRead);
        innerWritten.removeAll(outerBlockVars);
        written.addAll(innerWritten);
      } else if (updateLists) {
        // Update the passed in vars
        rebuildContinuationPassedVars(function, continuation, visible,
              outerBlockVars, read, innerRead, written, innerWritten);
        rebuildContinuationKeepOpenVars(function, continuation,
              visible, outerBlockVars, written, innerWritten);
      }
    }
  }

  private static void rebuildContinuationPassedVars(Function function,
          Continuation continuation, HierarchicalSet<Var> visibleVars,
          Set<Var> outerBlockVars, 
          Set<Var> outerRead, Set<Var> innerRead, 
          Set<Var> outerWritten, Set<Var> innerWritten) {
    Set<Var> innerAllNeeded = new HashSet<Var>();
    innerAllNeeded.addAll(innerRead);
    innerAllNeeded.addAll(innerWritten);
    
    // Rebuild passed in vars
    List<PassedVar> passedIn = new ArrayList<PassedVar>();
    for (Var needed: innerAllNeeded) {
      boolean read = innerRead.contains(needed);
      boolean written = innerWritten.contains(needed);
      // Update outer in case outer will need to pass in
      if (!outerBlockVars.contains(needed)) {
        if (read)
          outerRead.add(needed);
        if (written)
          outerWritten.add(needed);
      }
      if (!visibleVars.contains(needed)) {
        throw new STCRuntimeError("Variable " + needed
            + " should have been " + "visible but wasn't in "
            + function.getName());
      }

      assert(read || written);
      boolean writeOnly = !read;
      passedIn.add(new PassedVar(needed, writeOnly));
    }
    continuation.setPassedVars(passedIn);
  }

  private static void rebuildContinuationKeepOpenVars(Function function,
      Continuation continuation, HierarchicalSet<Var> visible,
      Set<Var> outerBlockVars, Set<Var> outerWritten, Set<Var> innerWritten) {
    List<Var> keepOpen = new ArrayList<Var>();
    for (Var v: innerWritten) {
      // If not declared in this scope
      if (!outerBlockVars.contains(v)) {
        outerWritten.add(v);
      }
      if (!visible.contains(v)) {
        throw new STCRuntimeError("Variable " + v
            + " should have been " + "visible but wasn't in "
            + function.getName());
      }
      if (RefCounting.hasWriteRefCount(v)) {
        keepOpen.add(v);
      }
    }
    continuation.setKeepOpenVars(keepOpen);
  }

  private static void removeGlobalImports(Block block) {
    ListIterator<Var> varIt = block.variableIterator();
    while (varIt.hasNext()) {
      Var v = varIt.next();
      if (v.defType() == DefType.GLOBAL_CONST) {
        varIt.remove();
      }
    }
  }

  /**
   * 
   * @param block
   * @param visible
   * @param neededSets sets of vars needed from outside bock
   * @return set of global vars
   */
  private static Set<Var> addGlobalImports(Block block,
          HierarchicalSet<Var> visible,
          boolean updateLists, List<Set<Var>> neededSets) {
    // if global constant missing, just add it
    Set<Var> addedGlobals = new HashSet<Var>();
    for (Set<Var> neededSet: neededSets) {
      for (Var var: neededSet) {
        if (visible.contains(var)) {
          if (var.storage() == VarStorage.GLOBAL_CONST) {
            // Add at top in case used as mapping var
            if (updateLists && !addedGlobals.contains(var))
              block.addVariable(var, true);
            addedGlobals.add(var);
          }
        }
      }
    }
    return addedGlobals;
  }

  private static void removeUnusedGlobals(Program prog,
       Set<Var> referencedGlobals) {
    Set<String> globNames = new HashSet<String>(prog.getGlobalConsts().keySet());
    Set<String> referencedGlobNames = Var.nameSet(referencedGlobals);
    globNames.removeAll(referencedGlobNames);
    for (String unused: globNames) {
      prog.removeGlobalConst(unused);
    }
  }
}
