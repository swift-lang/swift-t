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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.RefCounting;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.Pair;
import exm.stc.common.util.Sets;
import exm.stc.ic.ICUtil;
import exm.stc.ic.aliases.AliasKey;
import exm.stc.ic.aliases.AliasTracker;
import exm.stc.ic.tree.ICContinuations.ContVarDefType;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GlobalConstants;
import exm.stc.ic.tree.ICTree.GlobalVars;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.Program.AllGlobals;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.Opcode;

/**
 * Fix up passInVars and keepOpenVars in IC.  Perform validation
 * to make sure variables are visible.
 */
public class FixupVariables implements OptimizerPass {

  /**
   * Control whether we update passed var lists.
   */
  public static enum FixupVarMode {
    NO_UPDATE, // Don't change
    REBUILD, // Rebuild from scratch
    ADD, // Only add
  }

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

      if (updateLists) {
        fixupFunction(logger, prog.allGlobals(), fn,
                      referencedGlobals, FixupVarMode.REBUILD);
        // Need to do a second pass to resolve recursive dependencies,
        // e.g. loop_continue instructions that pass things back to
        // the top of the loop
        fixupFunction(logger, prog.allGlobals(), fn,
                      referencedGlobals, FixupVarMode.ADD);
      } else {
        fixupFunction(logger, prog.allGlobals(), fn,
                      referencedGlobals, FixupVarMode.NO_UPDATE);
      }
    }

    if (updateLists)
      removeUnusedGlobals(prog.constants(), prog.globalVars(), referencedGlobals);
  }

  public static void fixupFunction(Logger logger,
      AllGlobals globals,  Function fn, Set<Var> referencedGlobals,
      FixupVarMode fixupMode) {
    HierarchicalSet<Var> fnargs = new HierarchicalSet<Var>();
    for (Var v : fn.getInputList()) {
      fnargs.add(v);
    }
    for (Var v : fn.getOutputList()) {
      fnargs.add(v);
    }
    fnargs.addAll(globals);

    AliasTracker aliases = new AliasTracker();

    Result res = fixupBlockRec(logger, fn, fn.mainBlock(),
                       ExecContext.control(), fnargs,
                       referencedGlobals, aliases, fixupMode);
    if (fixupMode != FixupVarMode.NO_UPDATE) {
      // Mark write-only outputs
      for (int i = 0; i < fn.getOutputList().size(); i++) {
        Var output = fn.getOutput(i);
        if (!res.read.contains(output) && !Types.hasReadableSideChannel(output)) {
          fn.makeOutputWriteOnly(i);
        }
      }
    }

    // Check that all variables referred to are available as args
    res.removeRead(fn.getInputList());
    res.removeReadWrite(fn.getOutputList());
    for (Var v: fn.getInputList()) {
      // TODO: should these be passed in through output list instead?
      if (Types.isPrimUpdateable(v.type())) {
        res.removeWritten(v);
      }
    }

    if (res.read.size() > 0) {
      throw new STCRuntimeError("Reference in IC function "
          + fn.getName() + " to undefined variables " + res.read.toString());
    }

    if (res.written.size() > 0) {
      throw new STCRuntimeError("Unexpected write IC function "
          + fn.getName() + " to variables " + res.written.toString());
    }

    if (res.aliasWritten.size() > 0) {
      throw new STCRuntimeError("Unexpected write IC function "
          + fn.getName() + " to variables " + res.aliasWritten.toString());
    }
  }

  private static class Result {
    final Set<Var> read; /** Variables that were read */
    final Set<Var> written; /** Variables that were written (de-aliased) */
    /** Original aliases for write variables, to make sure that redundant
     * aliases are passed correctly in case of suboptimal code */
    final Set<Var> aliasWritten;

    Result() {
      super();
      this.read = new HashSet<Var>();
      this.written = new HashSet<Var>();
      this.aliasWritten = new HashSet<Var>();
    }

    public List<Set<Var>> allSets() {
      List<Set<Var>> res = new ArrayList<Set<Var>>();
      res.add(read);
      res.add(written);
      res.add(aliasWritten);
      return res;
    }

    Set<Var> allNeeded() {
      return Sets.union(allSets());
    }

    /**
     * Add everything from another result
     */
    void add(Result other) {
      read.addAll(other.read);
      written.addAll(other.written);
      aliasWritten.addAll(other.aliasWritten);
    }

    /**
     * Add everything from another result with exclusions
     */
    void addExcluding(Result other, Collection<Var> exclusion) {
      List<Pair<Set<Var>, Set<Var>>> fromTos =
                      new ArrayList<Pair<Set<Var>, Set<Var>>>();
      fromTos.add(Pair.create(other.read, read));
      fromTos.add(Pair.create(other.written, written));
      fromTos.add(Pair.create(other.aliasWritten, aliasWritten));

      for (Pair<Set<Var>, Set<Var>> fromTo: fromTos) {
        for (Var var: fromTo.val1) {
          if (!exclusion.contains(var)) {
            fromTo.val2.add(var);
          }
        }
      }
    }


    void addRead(Var var) {
      read.add(var);
    }

    void addRead(Collection<Var> vars) {
      for (Var var: vars) {
        addRead(var);
      }
    }

    void removeRead(Var var) {
      this.read.remove(var);
    }

    void removeRead(Collection<Var> vars) {
      for (Var var: vars) {
        removeRead(var);
      }
    }

    Var canonicalWriteVar(Var var, AliasTracker aliases) {
      AliasKey key = aliases.getCanonical(var);
      assert(key != null) : var;
      assert(!key.hasUnknown()) : key + " " + var;
      Var canonical = aliases.findVar(key);
      assert(canonical != null) : var + " " + key;
      return canonical;
    }

    /**
     * Mark variable as written
     * @param var
     * @param aliases
     */
    void addWritten(Var var, AliasTracker aliases) {
      /* Use the canonical variable for a struct field so that we can track
       * the write back to the original struct field in outer scopes where
       * there may be multiple aliases for that field.
       */
      Var key = canonicalWriteVar(var, aliases);
      this.written.add(key);
      if (!key.equals(var)) {
        this.aliasWritten.add(var);
      }
    }

    void addWritten(Collection<Var> vars, AliasTracker aliases) {
      for (Var var: vars) {
        addWritten(var, aliases);
      }
    }

    /**
     * Remove variable without canonicalizing it, e.g. if we're moving
     * up to an outer scope
     * @param var
     */
    void removeWritten(Var var) {
      // Remove the non-canonical var
      this.written.remove(var);
      this.aliasWritten.remove(var);
    }


    void removeWritten(Collection<Var> vars) {
      for (Var var: vars) {
        removeWritten(var);
      }
    }

    /**
     * Remove variable without canonicalizing them
     * @param vars
     */
    void removeReadWrite(Collection<Var> vars) {
      for (Var var: vars) {
        removeReadWrite(var);
      }
    }

    void removeReadWrite(Var var) {
      removeRead(var);
      removeWritten(var);
    }
  }

  /**
   *
   * @param logger
   * @param block
   * @param visible all
   *          variables logically visible in this block. will be modified in fn
   * @param referencedGlobals updated with names of any globals used
   * @param aliases
   * @param fixupMode
   * @return
   */
  private static Result fixupBlockRec(Logger logger,
      Function function, Block block, ExecContext execCx,
      HierarchicalSet<Var> visible, Set<Var> referencedGlobals,
      AliasTracker aliases, FixupVarMode fixupMode) {

    if (fixupMode == FixupVarMode.REBUILD) {
      // Remove global imports to be readded later if needed
      removeGlobalImports(block);
    }

    // blockVars: variables defined in this block
    Set<Var> blockVars = new HashSet<Var>();

    // update block variables and visible variables
    for (Var v: block.getVariables()) {
      blockVars.add(v);
      visible.add(v);
    }

    List<Pair<Var, Var>> createdAliases = new ArrayList<Pair<Var, Var>>();

    // Work out which variables are read/writte which aren't locally declared
    Result result = new Result();
    findBlockNeeded(block, result, aliases, createdAliases);

    for (Continuation c : block.allComplexStatements()) {
      variablePassing(logger, function, execCx, c,
              visible, referencedGlobals, aliases,
              blockVars, result, fixupMode);
    }


    // TODO: Partial fix for copy_refed variable that is then written
    for (Pair<Var, Var> a: createdAliases) {
      if (result.written.contains(a.val2)) {
        result.addWritten(a.val1, aliases);
      }
    }

    // Get ready to return to outer scope
    // Outer scopes don't have anything to do with vars declared here
    result.removeReadWrite(blockVars);

    // Global constants can be imported in control blocks only
    Set<Var> globals = addGlobalImports(block, execCx, visible, fixupMode,
                                        result.read, result.written,
                                        result.aliasWritten);

    referencedGlobals.addAll(globals);

    /*
     * Remove read references to globals.  Retain write since we'll need write
     * refcounts to be passed in.
     */
    result.removeReadWrite(globals);

    return result;
  }

  private static boolean canImportGlobal(ExecContext execCx, Alloc storage) {
    if (storage == Alloc.GLOBAL_CONST) {
      return canImportGlobalConsts(execCx);
    } else {
      assert(storage == Alloc.GLOBAL_VAR);
      return true;
    }
  }

  private static boolean canImportGlobalConsts(ExecContext execCx) {
    return execCx.isControlContext() ||
            !Settings.SEPARATE_TURBINE_ENGINE;
  }

  /**
   * Find all referenced vars in scope
   * @param block
   * @param result accumulate needed vars
   * @param aliases
   * @param createdAliases
   */
  private static void findBlockNeeded(Block block, Result result,
                    AliasTracker aliases, List<Pair<Var, Var>> createdAliases) {

    for (Statement stmt: block.getStatements()) {
      switch (stmt.type()) {
        case INSTRUCTION: {
          Instruction i = stmt.instruction();

          // This line keeps aliases up to data for e.g. struct insertions
          aliases.update(i);

          for (Arg in: i.getInputs()) {
            if (in.isVar()) {
              result.addRead(in.getVar());
            }
          }
          for (Var read: i.getReadOutputs()) {
            result.addRead(read);
          }
          result.addWritten(i.getOutputs(), aliases);


          // TODO: hack to get around aliasing issues
          if (i.op == Opcode.COPY_REF) {
            createdAliases.add(Pair.create(i.getInput(0).getVar(),
                                           i.getOutput(0)));
          }

          break;
        }
        case CONDITIONAL: {
          result.addRead(stmt.conditional().requiredVars(false));
          break;
        }
        default:
          throw new STCRuntimeError("Unknown statement type " + stmt.type());
      }
    }

    for (Continuation cont: block.getContinuations()) {
      result.addRead(cont.requiredVars(false));
    }

    for (CleanupAction cleanup: block.getCleanups()) {
      // ignore outputs - the cleaned up vars should already be in scope
      for (Arg in: cleanup.action().getInputs()) {
        if (in.isVar()) {
          result.addRead(in.getVar());
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
   * @param aliases
   * @param outerBlockVars
   * @param neededVars
   * @param fixupMode !
   */
  private static void variablePassing(Logger logger, Function function,
          ExecContext outerCx,
          Continuation continuation, HierarchicalSet<Var> visible,
          Set<Var> referencedGlobals, AliasTracker outerAliases, Set<Var> outerBlockVars,
          Result result, FixupVarMode fixupMode) {
    // First see what variables the continuation defines inside itself
    List<Var> constructVars = continuation.constructDefinedVars(ContVarDefType.NEW_DEF);
    ExecContext innerCx = continuation.childContext(outerCx);
    AliasTracker contAliases = outerAliases.makeChild();

    for (Block innerBlock : continuation.getBlocks()) {
      HierarchicalSet<Var> childVisible = visible.makeChild();
      for (Var v : constructVars) {
        childVisible.add(v);
      }
      AliasTracker blockAliases = contAliases.makeChild();
      Result inner = fixupBlockRec(logger,
          function, innerBlock, innerCx, childVisible,
          referencedGlobals, blockAliases, fixupMode);

      // construct will provide some vars
      if (!constructVars.isEmpty()) {
        inner.removeReadWrite(constructVars);
      }

      if (continuation.variablePassing().isAutomatic()) {
        // Might be some variables not yet defined in this scope
        inner.removeReadWrite(outerBlockVars);
        result.add(inner);
      } else if (fixupMode != FixupVarMode.NO_UPDATE) {
        // Update the passed in vars
        rebuildContinuationPassedVars(function, continuation, innerCx,
            visible, outerBlockVars, outerAliases, result, inner,
            fixupMode);
        rebuildContinuationKeepOpenVars(function, continuation,
                  visible, outerBlockVars, outerAliases, result, inner,
                  fixupMode);
      }
    }
  }

  private static void rebuildContinuationPassedVars(Function function,
          Continuation continuation, ExecContext contCx,
          HierarchicalSet<Var> visibleVars,
          Set<Var> outerBlockVars, AliasTracker outerAliases,
          Result outer, Result inner, FixupVarMode fixupMode) {
    // Rebuild passed in vars
    List<PassedVar> passedIn = new ArrayList<PassedVar>();
    for (Var needed: inner.allNeeded()) {
      if (!visibleVars.contains(needed)) {
        throw new STCRuntimeError("Variable " + needed
            + " should have been " + "visible but wasn't in "
            + function.getName());
      }

      boolean writeOnly = !inner.read.contains(needed);
      passedIn.add(new PassedVar(needed, writeOnly));
    }

    // Copy out any additional variables
    outer.addExcluding(inner, outerBlockVars);

    // Handle any additional variables that need to be passed in,
    // for example if a variable is waited on but not otherwise passed
    for (PassedVar addtl: continuation.getMustPassVars()) {
      // Check
      boolean mustAdd = true;
      ListIterator<PassedVar> it = passedIn.listIterator();
      while (it.hasNext()) {
        PassedVar existing = it.next();
        if (existing.var.equals(addtl.var)) {
          mustAdd = false;
          if (existing.writeOnly && !addtl.writeOnly) {
            // Must be readable too
            it.set(addtl);
          }
        }
      }
      if (mustAdd) {
        if (addtl.var.storage().isGlobal()) {
          // Only pass global const if needed
          if (canImportGlobal(contCx, addtl.var.storage())) {
            for (Block b: continuation.getBlocks()) {
              if (!b.getVariables().contains(addtl.var)) {
                b.addVariable(addtl.var);
              }
            }
          } else {
            passedIn.add(addtl);
          }
        } else {
          passedIn.add(addtl);
        }
      }
    }

    if (fixupMode == FixupVarMode.REBUILD) {
      continuation.setPassedVars(passedIn);
    } else {
      assert(fixupMode == FixupVarMode.ADD);
      Collection<PassedVar> newPassedVars;
      Collection<PassedVar> oldPassedVars = continuation.getPassedVars();
      newPassedVars = PassedVar.mergeLists(passedIn, oldPassedVars);
      continuation.setPassedVars(newPassedVars);
    }
  }

  private static void rebuildContinuationKeepOpenVars(Function function,
      Continuation continuation, HierarchicalSet<Var> visible,
      Set<Var> outerBlockVars, AliasTracker outerAliases,
      Result outer, Result inner, FixupVarMode fixupMode) {
    List<Var> keepOpen = new ArrayList<Var>();
    for (Var v: inner.written) {
      // If not declared in this scope
      if (!outerBlockVars.contains(v)) {
        outer.addWritten(v, outerAliases);
      }
      if (!visible.contains(v)) {
        throw new STCRuntimeError("Variable " + v
            + " should have been " + "visible but wasn't in "
            + function.getName());
      }
      if (RefCounting.trackWriteRefCount(v)) {
        keepOpen.add(v);
      }
    }

    if (fixupMode == FixupVarMode.REBUILD) {
      continuation.setKeepOpenVars(keepOpen);
    } else {
      assert(fixupMode == FixupVarMode.ADD);
      keepOpen.addAll(continuation.getKeepOpenVars());
      ICUtil.removeDuplicates(keepOpen);
      continuation.setKeepOpenVars(keepOpen);
    }
  }

  private static void removeGlobalImports(Block block) {
    ListIterator<Var> varIt = block.variableIterator();
    while (varIt.hasNext()) {
      Var v = varIt.next();
      if (v.defType().isGlobal()) {
        varIt.remove();
      }
    }
  }

  /**
   *
   * @param block
   * @param execCx
   * @param visible
   * @param neededSets sets of vars needed from outside bock
   * @return set of global vars needed from outside
   */
  private static Set<Var> addGlobalImports(Block block,
          ExecContext execCx, HierarchicalSet<Var> visible,
          FixupVarMode fixupMode, Set<Var> read, Set<Var> written,
          Set<Var> aliasWritten) {
    // if global constant missing, just add it
    Set<Var> existingGlobals = new HashSet<Var>();
    if (fixupMode == FixupVarMode.ADD) {
      for (Var v: block.getVariables()) {
        if (v.storage().isGlobal()) {
          existingGlobals.add(v);
        }
      }
    }

    addGlobalImports(block, execCx, visible, fixupMode, existingGlobals,
                      read, false);
    addGlobalImports(block, execCx, visible, fixupMode, existingGlobals,
                      written, true);
    addGlobalImports(block, execCx, visible, fixupMode, existingGlobals,
                      aliasWritten, true);

    return existingGlobals;
  }

  private static void addGlobalImports(Block block, ExecContext execCx,
      HierarchicalSet<Var> visible, FixupVarMode fixupMode,
      Set<Var> existingGlobals, Set<Var> neededSet, boolean written) {
    for (Var var: neededSet) {
      if (visible.contains(var) &&
          var.storage().isGlobal() &&
          canImportGlobal(execCx, var.storage()) &&
          !forcePassGlobal(block.getType(), var.storage(), written)) {
        // Add at top in case used as mapping var
        if (fixupMode != FixupVarMode.NO_UPDATE
            && !existingGlobals.contains(var))
          block.addVariable(var, true);
        existingGlobals.add(var);
      }
    }
  }

  /**
   * We may want to force passing of globals from parent blocks if written so
   * that reference count management is correct.
   * @param type
   * @param storage
   * @param written
   * @return
   */
  private static boolean forcePassGlobal(BlockType type, Alloc storage, boolean written) {
    if (written) {
      return type != BlockType.MAIN_BLOCK;
    } else {
      return false;
    }
  }

  private static void removeUnusedGlobals(GlobalConstants constants,
       GlobalVars globalVars, Set<Var> referencedGlobals) {
    Set<Var> globalsToRemove = new HashSet<Var>();
    globalsToRemove.addAll(constants.map().keySet());
    globalsToRemove.addAll(globalVars.getVariables());

    globalsToRemove.removeAll(referencedGlobals);
    for (Var unused: globalsToRemove) {
      if (unused.storage() == Alloc.GLOBAL_CONST) {
        constants.remove(unused);
      } else {
        assert(unused.storage() == Alloc.GLOBAL_VAR);
        globalVars.removeVariable(unused);
      }
    }
  }
}
