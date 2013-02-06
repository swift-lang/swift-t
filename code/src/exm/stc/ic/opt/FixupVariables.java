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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.Pair;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICTree.Block;
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
        fn, fn.getMainblock(), fnargs, referencedGlobals, updateLists);
    
    Set<Var> neededVars = res.val1;
    // Check that all variables referred to are available as args
    neededVars.removeAll(fn.getInputList());
    neededVars.removeAll(fn.getOutputList());
  
    if (neededVars.size() > 0) {
      throw new STCRuntimeError("Reference in IC function "
          + fn.getName() + " to undefined variables " + neededVars.toString());
    }
    
    Set<Var> written = res.val2;
    written.removeAll(fn.getOutputList());
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
      Function function, Block block, HierarchicalSet<Var> visible,
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
    Set<Var> written = new HashSet<Var>();
    Set<Var> neededVars = new HashSet<Var>();
    findBlockNeeded(block, written, neededVars);
    
    for (Continuation c : block.getContinuations()) {
      fixupContinuationRec(logger, function, c, visible, referencedGlobals,
              blockVars, neededVars, written, updateLists);
    }

    // Outer scopes don't have anything to do with vars declared here
    neededVars.removeAll(blockVars);
    written.removeAll(blockVars);
    
    Set<Var> globals = addGlobalImports(block, visible,
                                          neededVars, updateLists);

    referencedGlobals.addAll(globals);
    neededVars.removeAll(globals);
    return Pair.create(neededVars, written);
  }

  /**
   * 
   * @param block
   * @param written accumulate written vars with refcounts
   * @param allNeeded accumulate all referenced vars from this block
   */
  private static void findBlockNeeded(Block block, Set<Var> written,
      Set<Var> allNeeded) {
    block.findThisBlockNeededVars(allNeeded, written, null);
    allNeeded.addAll(written); // written are also needed
    // Remove vars without write refcounts 
    Iterator<Var> wrIt = written.iterator();
    while (wrIt.hasNext()) {
      if (!RefCounting.hasWriteRefCount(wrIt.next())) {
        wrIt.remove();
      }
    }
  }

  /**
   * Update variable passing for nested continuation
   * @param logger
   * @param function
   * @param continuation
   * @param visible
   * @param referencedGlobals
   * @param outerBlockVars
   * @param neededVars
   * @param updateLists 
   */
  private static void fixupContinuationRec(Logger logger, Function function,
          Continuation continuation, HierarchicalSet<Var> visible,
          Set<Var> referencedGlobals, Set<Var> outerBlockVars,
          Set<Var> neededVars, Set<Var> written, boolean updateLists) {
    // First see what variables the continuation defines inside itself
    List<Var> constructVars = continuation.constructDefinedVars();
    
    for (Block innerBlock : continuation.getBlocks()) {
      HierarchicalSet<Var> childVisible = visible.makeChild();
      for (Var v : constructVars) {
        childVisible.add(v);
      }
      
      Pair<Set<Var>, Set<Var>> inner = fixupBlockRec(logger,
          function, innerBlock, childVisible, referencedGlobals, updateLists);
      Set<Var> innerNeededVars = inner.val1;
      Set<Var> innerWritten = inner.val2;
      
      // construct will provide some vars
      if (!constructVars.isEmpty()) {
        innerNeededVars.removeAll(constructVars);
      }

      if (continuation.inheritsParentVars()) {
        // Might be some variables not yet defined in this scope
        innerNeededVars.removeAll(outerBlockVars);
        neededVars.addAll(innerNeededVars);
        innerWritten.removeAll(outerBlockVars);
        written.addAll(innerWritten);
      } else if (updateLists) {
        // Update the passed in vars
        rebuildContinuationPassedVars(function, continuation, visible,
              outerBlockVars, neededVars, innerNeededVars);
        rebuildContinuationKeepOpenVars(function, continuation,
              visible, outerBlockVars, written, innerWritten);
      }
    }
  }

  private static void rebuildContinuationPassedVars(Function function,
          Continuation continuation, HierarchicalSet<Var> visibleVars,
          Set<Var> outerBlockVars, Set<Var> outerNeededVars,
          Set<Var> innerNeededVars) {
    // Rebuild passed in vars
    continuation.clearPassedInVars();
    
    for (Var needed: innerNeededVars) {
      assert(needed.storage() != VarStorage.GLOBAL_CONST);
      if (!outerBlockVars.contains(needed)) {
        outerNeededVars.add(needed);
      }
      if (!visibleVars.contains(needed)) {
        throw new STCRuntimeError("Variable " + needed
            + " should have been " + "visible but wasn't in "
            + function.getName());
      }
      continuation.addPassedInVar(needed);
    }
  }

  private static void rebuildContinuationKeepOpenVars(Function function,
      Continuation continuation, HierarchicalSet<Var> visible,
      Set<Var> outerBlockVars, Set<Var> outerWritten, Set<Var> innerWritten) {
    continuation.clearKeepOpenVars();
    
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
      assert(RefCounting.hasWriteRefCount(v));
      continuation.addKeepOpenVar(v);
    }
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
   * @param neededVars
   * @return set of global vars
   */
  private static Set<Var> addGlobalImports(Block block,
          HierarchicalSet<Var> visible,
          Set<Var> neededVars, boolean updateLists) {
    // if global constant missing, just add it
    Set<Var> globals = new HashSet<Var>();
    for (Var needed: neededVars) {
      if (visible.contains(needed)) {
        if (needed.storage() == VarStorage.GLOBAL_CONST) {
          // Add at top in case used as mapping var
          if (updateLists)
            block.addVariable(needed, true);
          globals.add(needed);
        }
      }
    }
    return globals;
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
