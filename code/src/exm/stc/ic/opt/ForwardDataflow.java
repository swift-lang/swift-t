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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend.WaitMode;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.InvalidWriteException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecContext;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.CopyOnWriteSmallSet;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.MultiMap;
import exm.stc.ic.ICUtil;
import exm.stc.ic.opt.ComputedValue.EquivalenceType;
import exm.stc.ic.opt.ProgressOpcodes.Category;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICContinuations.WaitVar;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.CVMap;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmRequest;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;

/**
 * This optimisation pass does a range of optimizations.  The overarching idea
 * is that we move forward through the IC and keep track of, 
 * at each instruction, which variables will be closed, and what values
 * have already been computed.  There are several ways we exploit this:
 * 
 *  - If a future is known to be closed, we can retrieve the value and 
 *      perform operations locally, or we can eliminate a wait
 *  - If the same value is computed twice, we can reuse the earlier value
 *  - If a value has been inserted into an array, and we retrieve a value
 *    from the same index, we can skip the load.  Same for struct loads 
 *    and stores 
 *  - etc.
 * 
 * This optimization pass doesn't remove any instructions: it simply modifies
 * the arguments to each instruction in a way that will hopefully lead a bunch
 * of dead code, which can be cleaned up in a pass of the dead code eliminator
 *
 */
public class ForwardDataflow implements OptimizerPass {
  
  private boolean keepExplicitWaits;

  public ForwardDataflow(boolean keepExplicitWaits) {
    this.keepExplicitWaits = keepExplicitWaits;
  }
  
  @Override
  public String getPassName() {
    return "Forward dataflow";
  }
  
  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_FORWARD_DATAFLOW;
  }
  
  /**
   * State keep tracks of which variables are closed and which computed
   * expressions are available at different points in the IC
   */
  private static class State implements CVMap {
    private final Logger logger;
    
    private final State parent; 
    private final boolean varsPassedFromParent;
    
    /**
     * Map of variable names to value variables or literals which have been
     * created and set in this scope
     */
    private final HashMap<ComputedValue, Arg> availableVals;
    
    /**
     * What computedValues are stored in each value (inverse
     * of availableVals) 
     */
    private final MultiMap<Var, ComputedValue> varContents;

    /** variables which are closed at this point in program */
    private final HierarchicalSet<Var> closed;
    
    /** mappable variables which are unmapped */
    private final HierarchicalSet<Var> unmapped;

    /** variables which are recursively closed at this point in program */
    private final HierarchicalSet<Var> recursivelyClosed;
    
    /**
     * Multimap of var1 -> [ var2, var3]
     * 
     * There should only be an entry in here if var1 is not closed An entry here
     * means that var1 will be closed only after var2 and var3 are closed (e.g.
     * if var1 = var2 + var3)
     * 
     * We maintain this data structure because it lets us infer which variables
     * will be closed if we block on a given variable
     */
    private final HashMap<Var, CopyOnWriteSmallSet<Var>> dependsOn;

    State(Logger logger) {
      this.logger = logger;
      this.parent = null;
      this.varsPassedFromParent = false;
      this.availableVals = new HashMap<ComputedValue, Arg>();
      this.varContents = new MultiMap<Var, ComputedValue>();
      this.closed = new HierarchicalSet<Var>();
      this.unmapped = new HierarchicalSet<Var>();
      this.recursivelyClosed = new HierarchicalSet<Var>();
      this.dependsOn = new HashMap<Var, CopyOnWriteSmallSet<Var>>();
    }

    private State(Logger logger, State parent,
        boolean varsPassedFromParent,
        HashMap<ComputedValue, Arg> availableVals,
        MultiMap<Var, ComputedValue> varContents,
        HierarchicalSet<Var> closed,
        HierarchicalSet<Var> unmapped,
        HierarchicalSet<Var> recursivelyClosed,
        HashMap<Var, CopyOnWriteSmallSet<Var>> dependsOn) {
      this.logger = logger;
      this.parent = parent;
      this.varsPassedFromParent = varsPassedFromParent;
      this.availableVals = availableVals;
      this.varContents = varContents;
      this.closed = closed;
      this.unmapped = unmapped;
      this.recursivelyClosed = recursivelyClosed;
      this.dependsOn = dependsOn;
    }

    public Set<Var> getClosed() {
      return Collections.unmodifiableSet(closed);
    }

    public Set<Var> getRecursivelyClosed() {
      return Collections.unmodifiableSet(recursivelyClosed);
    }

    public boolean isClosed(Var var) {
      return closed.contains(var) || recursivelyClosed.contains(var);
    }
    
    public boolean isRecursivelyClosed(Var var) {
      return recursivelyClosed.contains(var);
    }

    public boolean isAvailable(ComputedValue val) {
      return getLocation(val) != null;
    }

    /**
     * See if the result of a value retrieval is already in scope
     * 
     * @param v
     * @return
     */
    public Arg findRetrieveResult(Var v) {
      ComputedValue cvRetrieve = ICInstructions.retrieveCompVal(v);
      if (cvRetrieve == null) {
        return null;
      } else {
        return getLocation(cvRetrieve);
      }
    }

    /**
     * 
     * @param res
     * @param replace for debugging purposes, set to true if we intend to 
     *        replace
     */
    public void addComputedValue(ResultVal res, boolean replace) {
      boolean outClosed = res.outClosed();
      if (isAvailable(res.value())) {
        if (!replace) {
          throw new STCRuntimeError("Unintended overwrite of "
              + getLocation(res.value()) + " with " + res);
        }
      } else if (replace) {
        throw new STCRuntimeError("Expected overwrite of " + " with "
            + res + " but no existing value");
      }

      Arg valLoc = res.location();
      availableVals.put(res.value(), valLoc);
      if (valLoc.isVar()) {
        varContents.put(valLoc.getVar(), res.value());
        if (outClosed) {
          close(valLoc.getVar(), false);
        }
      }
    }

    /**
     * Return an oparg with the variable or constant for the computed value
     * 
     * @param val
     * @return
     */
    public Arg getLocation(ComputedValue val) {
      boolean passRequired = false;
      State curr = this;
      
      while (curr != null) {
        Arg loc = curr.availableVals.get(val);
        if (loc != null) {
          // Found a value, now see if it is actually visible
          if (!passRequired) {
            return loc;
          } else if (!Semantics.canPassToChildTask(loc.type())) {
            return null;
          } else {
            return loc;
          }
        }
        
        passRequired = passRequired || (!curr.varsPassedFromParent);
        curr = curr.parent;
      }
      
      return null;
    }
    
    public List<ComputedValue> getVarContents(Var v) {
      State curr = this;
      List<ComputedValue> res = null;
      boolean resModifiable = false;
      
      while (curr != null) {
        List<ComputedValue> partRes = curr.varContents.get(v);
        if (!partRes.isEmpty()) {
          if (res == null) {
            res = partRes;
          } else if (resModifiable) {
            res.addAll(partRes);
          } else {
            List<ComputedValue> oldRes = res;
            res = new ArrayList<ComputedValue>();
            res.addAll(oldRes);
            res.addAll(partRes);
            resModifiable = true;
          }
        }
        curr = curr.parent;
      }
      return res == null ? Collections.<ComputedValue>emptyList() : res;
    }

    public void addClosed(UnifiedState closed) {
      for (Var v: closed.closed) {
        close(v, false);
      }
      for (Var v: closed.recursivelyClosed) {
        close(v, true);
      }
    }

    /**
     * Called when we enter a construct that blocked on v
     * 
     * @param var
     */
    public void close(Var var, boolean recursive) {
      // Do DFS on the dependency graph to find all dependencies
      // that are now enabled
      Stack<Var> work = new Stack<Var>();
      work.add(var);
      while (!work.empty()) {
        Var v = work.pop();
        // they might already be in closed, but add anyway
        closed.add(v);
        CopyOnWriteSmallSet<Var> deps = dependsOn.remove(v);
        if (deps != null) {
          work.addAll(deps);
        }
      }
      if (recursive) {
        recursivelyClosed.add(var);
      }
    }
    
    public void setUnmapped(Var var) {
      unmapped.add(var);
    }
    
    public Set<Var> getUnmapped() {
      return Collections.unmodifiableSet(unmapped);
    }

    /**
     * Register that variable future depends on all of the variables in the
     * collection, so that if future is closed, then the other variables must be
     * closed
     * TODO: later could allow specification that something is recursively closed
     * 
     * @param future
     *          a scalar future
     * @param depend
     *          more scalar futures
     */
    public void setDependencies(Var future, Collection<Var> depend) {
      assert (!Types.isScalarValue(future.type()));
      CopyOnWriteSmallSet<Var> depset = dependsOn.get(future);
      if (depset == null) {
        depset = new CopyOnWriteSmallSet<Var>();
        dependsOn.put(future, depset);
      }
      for (Var v: depend) {
        assert (!Types.isScalarValue(v.type()));
        depset.add(v);
      }
    }

    /**
     * Make an exact copy for a nested scope, such that any changes to the new
     * copy aren't reflected in this one
     */
    State makeChild(boolean varsPassedFromParent) {
      HashMap<Var, CopyOnWriteSmallSet<Var>> newDO = 
              new HashMap<Var, CopyOnWriteSmallSet<Var>>();

      for (Entry<Var, CopyOnWriteSmallSet<Var>> e : dependsOn.entrySet()) {
        newDO.put(e.getKey(), new CopyOnWriteSmallSet<Var>(e.getValue()));
      }
      return new State(logger, this, varsPassedFromParent, 
          new HashMap<ComputedValue, Arg>(), 
          new MultiMap<Var, ComputedValue>(), closed.makeChild(),
          unmapped.makeChild(), recursivelyClosed.makeChild(), newDO);
    }
  }

  private static void updateReplacements(
      Logger logger, Function function, Instruction inst,
      State av, List<ResultVal> irs, HierarchicalMap<Var, Arg> replaceInputs, 
      HierarchicalMap<Var, Arg> replaceAll) {
    if (irs != null) {
      if (logger.isTraceEnabled()) {
        logger.trace("irs: " + irs.toString());
      }
      for (ResultVal resVal : irs) {
        if (ComputedValue.isAlias(resVal)) {
          replaceAll.put(resVal.location().getVar(),
                                          resVal.value().getInput(0));
          continue;
        } else if (resVal.value().isCopy()) {
          // Copies are easy to handle: replace output of inst with input 
          // going forward
          replaceInputs.put(resVal.location().getVar(),
                                          resVal.value().getInput(0));
          continue;
        }
        Arg currLoc = resVal.location();
        if (!av.isAvailable(resVal.value())) {
          // Can't replace, track this value
          av.addComputedValue(resVal, false);
        } else if (currLoc.isConstant()) {
          Arg prevLoc = av.getLocation(resVal.value());
          if (prevLoc.isVar()) {
            assert (Types.isScalarValue(prevLoc.getVar().type()));
            // Constants are the best... might as well replace
            av.addComputedValue(resVal, true);
            // System.err.println("replace " + prevLoc + " with " + currLoc);
            replaceInputs.put(prevLoc.getVar(), currLoc);
          } else {
            // Should be same, otherwise bug
            assert (currLoc.equals(prevLoc)) : resVal + " = " + prevLoc +
                    " != " + currLoc + " in " + function.getName() + ".\n" +
                    "This may have been caused by a double-write to a variable. " +
                    "Please look at any previous warnings emitted by compiler. ";
          }
        } else {
          final boolean usePrev;
          assert (currLoc.isVar());
          // See if we should replace
          Arg prevLoc = av.getLocation(resVal.value());
          if (prevLoc.isConstant()) {
            usePrev = true;
          } else {
            assert (prevLoc.isVar());
            boolean currClosed = av.isClosed(currLoc.getVar());
            boolean prevClosed = av.isClosed(prevLoc.getVar());
            if (resVal.equivType() == EquivalenceType.REFERENCE) {
              // The two locations are both references to same thing, so can 
              // replace all references, including writes to currLoc
              replaceAll.put(currLoc.getVar(), prevLoc);
            }
            if (prevClosed || !currClosed) {
              // Use the prev value
              usePrev = true;
            } else {
              /*
               * The current variable is closed but the previous isn't. Its
               * probably better to use the closed one to enable further
               * optimisations
               */
              usePrev = false;
            }
          }

          // Now we've decided whether to use the current or previous
          // variable for the computed expression
          if (usePrev && resVal.isSubstitutable()) {
            // Do it
            if (logger.isTraceEnabled())
              logger.trace("replace " + currLoc + " with " + prevLoc);
            replaceInputs.put(currLoc.getVar(), prevLoc);
          } else {
            if (logger.isTraceEnabled())
              logger.trace("replace " + prevLoc + " with " + currLoc);
            replaceInputs.put(prevLoc.getVar(), currLoc);
          }
        }
      }
    } else {
      logger.trace("no icvs");
    }
  }


  /**
   * Do a kind of dataflow analysis where we try to identify which futures are
   * closed at different points in the program. This allows us to switch to
   * lower-overhead versions of many operations.
   * 
   * @param logger
   * @param program
   * @throws InvalidOptionException
   * @throws InvalidWriteException
   */
  public void optimize(Logger logger, Program program)
      throws InvalidOptionException, InvalidWriteException {
    State globalState = new State(logger);
    
    for (Var v: program.getGlobalVars()) {
      // First, all constants can be treated as being set
      if (v.storage() == VarStorage.GLOBAL_CONST) {
        Arg val = program.lookupGlobalConst(v.name());
        assert (val != null): v.name();
        ResultVal compVal = ICInstructions.assignComputedVal(v, val);
        globalState.addComputedValue(compVal,
              globalState.isAvailable(compVal.value()));
      }
    }
    for (Function f : program.getFunctions()) {
      // Do repeated passes until converged
      boolean changes;
      int pass = 1;
      do {
        logger.trace("closed variable analysis on function " + f.getName()
            + " pass " + pass);
        changes = forwardDataflow(logger, program, f, ExecContext.CONTROL,
            f.mainBlock(), globalState.makeChild(false), new HierarchicalMap<Var, Arg>(),
            new HierarchicalMap<Var, Arg>());
        liftWait(logger, program, f);
        pass++;
      } while (changes);
    }
  }

  /**
   * If we have something of the below form, can just block 
   * on a as far of function call protocol
   * (..) f (a, b, c) {
   *   wait (a) {
   *   
   *   }
   * }
   * @param logger
   * @param program
   * @param f
   */
  private static void liftWait(Logger logger, Program program, Function f) {
    if (!f.isAsync()) {
      // Can only do this optimization if the function runs asynchronously
      return;
    }
    
    Block main = f.mainBlock();
    List<WaitVar> blockingVariables = findBlockingVariables(logger, f, main);
    
    if (blockingVariables != null) {
      List<Var> locals = f.getInputList();
      if (logger.isTraceEnabled()) {
        logger.trace("Blocking " + f.getName() + ": " + blockingVariables);
      }
      for (WaitVar wv: blockingVariables) {
        boolean isConst = program.lookupGlobalConst(wv.var.name()) != null;
        // Global constants are already set
        if (!isConst && locals.contains(wv.var)) {
          // Check if a non-arg
          f.addBlockingInput(wv);
        }
      }
    }
  }
  
  /**
   * Find the set of variables required to be closed (recursively or not)
   * to make progress in block.
   * @param block
   * @return
   */
  private static List<WaitVar> findBlockingVariables(Logger logger,
        Function fn, Block block) {
    /*TODO: could exploit the information we have in getBlockingInputs() 
     *      to explore dependencies between variables and work out 
     *      which variables are needed to make progress */
    
    if (ProgressOpcodes.blockProgress(block, Category.NON_PROGRESS)) {
      // An instruction in block may make progress without any waits
      return null;
    }
    
    // Find blocking variables in instructions and continuations
    BlockingVarFinder walker = new BlockingVarFinder();
    TreeWalk.walkSyncChildren(logger, fn, block, true, walker);
    
    if (walker.blockingVariables == null) {
      return null;
    } else {
      ArrayList<WaitVar> res = 
          new ArrayList<WaitVar>(walker.blockingVariables);
      WaitVar.removeDuplicates(res);
      return res;
    }
  }

  private static final class BlockingVarFinder extends TreeWalker {
    // Set of blocking variables.  May contain duplicates for explicit/not explicit -
    // we eliminate these at end
    HashSet<WaitVar> blockingVariables = null;

    @Override
    protected void visit(Continuation cont) {
      if (cont.isAsync()) {
        List<BlockingVar> waitOnVars = cont.blockingVars(false);
        List<WaitVar> waitOn;
        if (waitOnVars == null) {
          waitOn = WaitVar.NONE; 
        } else {
          waitOn = new ArrayList<WaitVar>(waitOnVars.size());
          for (BlockingVar bv: waitOnVars) {
            waitOn.add(new WaitVar(bv.var, bv.explicit));
          }
        }
  
        updateBlockingSet(waitOn);
        //System.err.println("Blocking so far:" + blockingVariables);
      }
    }

    @Override
    public void visit(Instruction inst) {
      List<WaitVar> waitVars = 
          WaitVar.asWaitVarList(inst.getBlockingInputs(), false);
      updateBlockingSet(waitVars);
    }


    private void updateBlockingSet(List<WaitVar> waitOn) {
      assert(waitOn != null);
      //System.err.println("waitOn: " + waitOn);
      if (blockingVariables == null) {
        blockingVariables = new HashSet<WaitVar>(waitOn);
      } else {
        // Keep only those variables which block all wait statements
        blockingVariables.retainAll(waitOn);
      }
    }
  }

  /**
   * 
   * @param execCx 
   * @param block
   * @param cv
   *          copy of cv from outer scope, or null if it should be initialized
   * @param replaceInputs
   *          : a set of variable replaces to do from this point in IC onwards
   * @return true if this should be called again
   * @throws InvalidOptionException
   * @throws InvalidWriteException
   */
  private boolean forwardDataflow(Logger logger, Program program,
      Function f, ExecContext execCx, Block block, State cv,
      HierarchicalMap<Var, Arg> replaceInputs,
      HierarchicalMap<Var, Arg> replaceAll) throws InvalidOptionException,
      InvalidWriteException {
    if (block.getType() == BlockType.MAIN_BLOCK) {
      for (WaitVar wv: f.blockingInputs()) {
        cv.close(wv.var, false);
      }
      for (Var v: f.getInputList()) {
        if (Types.isScalarUpdateable(v.type())) {
          // Updateables always have a value
          cv.close(v, false);
        }
      }
    }
    for (Var v : block.getVariables()) {
      if (v.isMapped() && Types.isFile(v.type())) {
        ResultVal filenameVal = ICInstructions.filenameCV(
            Arg.createVar(v.mapping()), v);
        cv.addComputedValue(filenameVal, false);
      }
      if (Types.isMappable(v.type()) && !v.isMapped()
                        && v.storage() != VarStorage.ALIAS) {
        // Var is definitely unmapped
        cv.setUnmapped(v);
      }
    }

    boolean inlined = handleStatements(logger, program, f, execCx, block, 
            block.statementIterator(), cv, replaceInputs, replaceAll);

    if (inlined) {
      cleanupAfterInline(block, replaceInputs, replaceAll);
      
      // Rebuild data structures for this block after inlining
      return true;
    }
    
    block.renameCleanupActions(replaceInputs, RenameMode.VALUE);
    block.renameCleanupActions(replaceAll, RenameMode.REFERENCE);

    // might be able to eliminate wait statements or reduce the number
    // of vars they are blocking on
    for (int i = 0; i < block.getContinuations().size(); i++) {
      Continuation c = block.getContinuation(i);
      
      // Replace all variables in the continuation construct
      c.renameVars(replaceInputs, RenameMode.VALUE, false);
      c.renameVars(replaceAll, RenameMode.REFERENCE, false);
      
      Block toInline = c.tryInline(cv.getClosed(), cv.getRecursivelyClosed(),
                                   keepExplicitWaits);
      if (toInline != null) {
        c.inlineInto(block, toInline);
        i--; // compensate for removal of continuation
        inlined = true;
      }
    }
    
    if (inlined) {
      cleanupAfterInline(block, replaceInputs, replaceAll);
      
      // Rebuild data structures for this block after inlining
      return true;
    }

    // Note: assume that continuations aren't added to rule engine until after
    // all code in block has run
    for (Continuation cont : block.getContinuations()) {
      recurseOnContinuation(logger, program, f, execCx, cont, cv,
          replaceInputs, replaceAll);
    }

    // Didn't inline everything, all changes should be propagated ok
    return false;
  }

  /**
   * 
   * @param logger
   * @param program
   * @param fn
   * @param execCx
   * @param cont
   * @param cv
   * @param replaceInputs
   * @param replaceAll
   * @return any variables that are guaranteed to be closed in current
   *        context after continuation is evaluated
   * @throws InvalidOptionException
   * @throws InvalidWriteException
   */
  private UnifiedState recurseOnContinuation(Logger logger, Program program,
      Function fn, ExecContext execCx, Continuation cont, State cv,
      HierarchicalMap<Var, Arg> replaceInputs,
      HierarchicalMap<Var, Arg> replaceAll) throws InvalidOptionException,
      InvalidWriteException {
    State contCV = cv.makeChild(cont.inheritsParentVars());
    // additional variables may be close once we're inside continuation
    List<BlockingVar> contClosedVars = cont.blockingVars(true);
    if (contClosedVars != null) {
      for (BlockingVar bv : contClosedVars) {
        contCV.close(bv.var, bv.recursive);
      }
    }
    
    // For conditionals, find variables closed on all branches
    boolean unifyBranches = cont.isExhaustiveSyncConditional();
    List<State> branchStates = unifyBranches ? new ArrayList<State>() : null;
    
    List<Block> contBlocks = cont.getBlocks();
    for (int i = 0; i < contBlocks.size(); i++) {
      // Update based on whether values available within continuation
      HierarchicalMap<Var, Arg> contReplaceInputs;
      HierarchicalMap<Var, Arg> contReplaceAll;
      if (cont.inheritsParentVars()) {
        contReplaceInputs = replaceInputs;
        contReplaceAll = replaceAll;
      } else {
        contReplaceInputs = replaceInputs.makeChildMap();
        contReplaceAll = replaceAll.makeChildMap();
        purgeUnpassableVars(contReplaceInputs);
        purgeUnpassableVars(contReplaceAll);
      }
      
      State blockCV; 
          
      boolean again;
      int pass = 1;
      do {
        logger.debug("closed variable analysis on nested block pass " + pass);
        blockCV = contCV.makeChild(true);
        again = forwardDataflow(logger, program, fn, cont.childContext(execCx),
            contBlocks.get(i), blockCV,
            contReplaceInputs.makeChildMap(), contReplaceAll.makeChildMap());
        
        // changes within nested scope don't require another pass
        // over this scope
        pass++;
      } while (again);
      
      if (unifyBranches) {
        branchStates.add(blockCV);
      }
    }
    
    if (unifyBranches) {
      return UnifiedState.unify(cv, branchStates);
    } else {
      return UnifiedState.EMPTY;
    }
  }
  
  
  private static class UnifiedState {
    private UnifiedState(Set<Var> closed, Set<Var> recursivelyClosed) {
      super();
      this.closed = closed;
      this.recursivelyClosed = recursivelyClosed;
    }
    
    static final UnifiedState EMPTY = new UnifiedState(
                  Collections.<Var>emptySet(), Collections.<Var>emptySet());
    
    /**
     * Assuming that branches are exhaustive, work out the set of
     * variables closed after the conditional has executed.
     * TODO: unify available values?
     * @param parentState
     * @param branchStates
     * @return
     */
    static UnifiedState unify(State parentState,
                                   List<State> branchStates) {
      if (branchStates.isEmpty()) {
        return EMPTY;
      } else {
        State firstState = branchStates.get(0);
        Set<Var> closed = new HashSet<Var>();
        Set<Var> recClosed = new HashSet<Var>();
        // Start off with variables closed in first branch that aren't
        // closed in parent
        for (Var v: firstState.getClosed()) {
          if (!parentState.isClosed(v)) {
            closed.add(v);
          }
        }
        for (Var v: firstState.getRecursivelyClosed()) {
          if (!parentState.isRecursivelyClosed(v)) {
            recClosed.add(v);
          }
        }
        
        for (int i = 1; i < branchStates.size(); i++) {
          closed.retainAll(branchStates.get(i).getClosed());
          recClosed.retainAll(branchStates.get(i).getRecursivelyClosed());
        }

        return new UnifiedState(closed, recClosed);
      }
    }
    
    final Set<Var> closed;
    final Set<Var> recursivelyClosed;
  }

  private static void cleanupAfterInline(Block block,
      HierarchicalMap<Var, Arg> replaceInputs,
      HierarchicalMap<Var, Arg> replaceAll) {
    // Redo replacements for newly inserted instructions/continuations
    for (Statement stmt: block.getStatements()) {
      stmt.renameVars(replaceInputs, RenameMode.VALUE);
      stmt.renameVars(replaceAll, RenameMode.REFERENCE);
    }
    for (Continuation c: block.getContinuations()) {
      c.renameVars(replaceInputs, RenameMode.VALUE, false);
      c.renameVars(replaceAll, RenameMode.REFERENCE, false);
    }
  }

  
  /**
   * 
   * @param logger
   * @param f
   * @param execCx
   * @param block
   * @param stmts
   * @param cv
   * @param replaceInputs
   * @param replaceAll
   * @return true if inlined.  Returns immediately if inlining happens
   * @throws InvalidWriteException
   * @throws InvalidOptionException 
   */
  private boolean handleStatements(Logger logger,
      Program program, Function f, ExecContext execCx, Block block,
      ListIterator<Statement> stmts, State cv,
      HierarchicalMap<Var, Arg> replaceInputs,
      HierarchicalMap<Var, Arg> replaceAll) throws InvalidWriteException,
                                                   InvalidOptionException {
    while(stmts.hasNext()) {
      Statement stmt = stmts.next();
      
      if (stmt.type() == StatementType.INSTRUCTION) {
        handleInstruction(logger, f, execCx, block, stmts, stmt.instruction(),
                          cv, replaceInputs, replaceAll);
      } else {
        assert(stmt.type() == StatementType.CONDITIONAL);
        // TODO: handle situation when:
        //        all branches assign future X a local values v1,v2,v3,etc.
        //        in this case should try to create another local value outside of
        //        conditional z which has the value from all branches stored
        UnifiedState condClosed = recurseOnContinuation(logger, program, f, execCx, 
                                          stmt.conditional(), cv, replaceInputs, replaceAll);
        cv.addClosed(condClosed);
      }
    }
    return false;
  }

  private static void handleInstruction(Logger logger, Function f,
      ExecContext execCx, Block block, ListIterator<Statement> stmts,
      Instruction inst, State cv, HierarchicalMap<Var, Arg> replaceInputs,
      HierarchicalMap<Var, Arg> replaceAll) {
    if (logger.isTraceEnabled()) {
      logger.trace("Value renames in effect: " + replaceInputs);
      logger.trace("Reference renames in effect: " + replaceAll);
      logger.trace("Available values this block: " + cv.availableVals);
      State ancestor = cv.parent;
      int up = 1;
      while (ancestor != null) {
        logger.trace("Available ancestor " + up + ": " +
                     ancestor.availableVals);
        up++;
        ancestor = ancestor.parent;
      }
      logger.trace("Closed variables: " + cv.closed);
      logger.trace("-----------------------------");
      logger.trace("At instruction: " + inst);
    }
    
    // Immediately apply the variable renames
    inst.renameVars(replaceInputs, RenameMode.VALUE);
    inst.renameVars(replaceAll, RenameMode.REFERENCE);
  
    List<ResultVal> icvs = inst.getResults(cv);
    
    /*
     * See if value is already computed somewhere and see if we should
     * replace variables going forward NOTE: we don't delete any instructions
     * on this pass, but rather rely on dead code elim to later clean up
     * unneeded instructions instead
     */
    updateReplacements(logger, f, inst, cv, icvs, replaceInputs, replaceAll);
    
    if (logger.isTraceEnabled()) {
      logger.trace("Instruction after updates: " + inst);
    }
    // now try to see if we can change to the immediate version
    if (switchToImmediate(logger, f, execCx, block, cv, inst, stmts)) {
      // Continue pass at the start of the newly inserted sequence
      // as if it was always there
      return;
    }
  
    // Add dependencies
    List<Var> in = inst.getBlockingInputs();
    if (in != null) {
      for (Var ov: inst.getOutputs()) {
        if (!Types.isScalarValue(ov.type())) {
          cv.setDependencies(ov, in);
        }
      }
    }
    
    for (Var out: inst.getClosedOutputs()) {
      cv.close(out, false);
    }
  }

  /**
   * 
   * @param logger
   * @param fn
   * @param block
   * @param cv
   * @param inst
   * @param insts if instructions inserted, leaves iterator pointing at previous instruction
   * @return
   */
  private static boolean switchToImmediate(Logger logger,
      Function fn, ExecContext execCx, Block block, State cv,
      Instruction inst, ListIterator<Statement> stmts) {
    // First see if we can replace some futures with values
    MakeImmRequest req = inst.canMakeImmediate(
            cv.getClosed(), cv.getUnmapped(), false);

    if (req == null) {
      return false;
    }
    
    // Create replacement sequence
    Block insertContext;
    ListIterator<Statement> insertPoint;
    boolean noWaitRequired = req.mode == TaskMode.LOCAL ||
                             req.mode == TaskMode.SYNC ||
                             (req.mode == TaskMode.LOCAL_CONTROL
                                 && execCx == ExecContext.CONTROL);
    if (noWaitRequired) {
      insertContext = block;
      insertPoint = stmts;
    } else {
      WaitStatement wait = new WaitStatement(
          fn.getName() + "-" + inst.shortOpName(),
          WaitVar.NONE, PassedVar.NONE, Var.NONE,
          WaitMode.TASK_DISPATCH, false, req.mode,
          inst.getTaskProps());
      insertContext = wait.getBlock();
      block.addContinuation(wait);
      // Insert at start of block
      insertPoint = insertContext.statementIterator();
    }
    
    // Now load the values
    List<Instruction> alt = new ArrayList<Instruction>();
    List<Arg> inVals = new ArrayList<Arg>(req.in.size());
    
    // same var might appear multiple times
    HashMap<Var, Arg> alreadyFetched = new HashMap<Var, Arg>();  
    for (Var v : req.in) {
      Arg maybeVal;
      if (alreadyFetched.containsKey(v)) {
        maybeVal = alreadyFetched.get(v);
      } else {
        maybeVal = cv.findRetrieveResult(v);
      }
      // Can only retrieve value of future or reference
      // If we inserted a wait, need to consider if local value can
      // be passed into new scope
      if (maybeVal != null &&
            (noWaitRequired || Semantics.canPassToChildTask(maybeVal.type()))) {
        /*
         * this variable might not actually be passed through continuations to
         * the current scope, so we might have temporarily made the IC
         * invalid, but we rely on fixupVariablePassing to fix this later
         */
        inVals.add(maybeVal);
        alreadyFetched.put(v, maybeVal);
      } else {
        // Generate instruction to fetch val, append to alt
        Var fetchedV = OptUtil.fetchForLocalOp(insertContext, alt, v);
        Arg fetched = Arg.createVar(fetchedV);
        inVals.add(fetched);
        alreadyFetched.put(v, fetched);
      }
    }
    List<Var> outValVars = OptUtil.declareLocalOpOutputVars(insertContext,
                                                            req.out);
    MakeImmChange change = inst.makeImmediate(outValVars, inVals);
    OptUtil.fixupImmChange(block, insertContext, change, alt,
                           outValVars, req.out);
    

    if (logger.isTraceEnabled()) {
      logger.trace("Replacing instruction <" + inst + "> with sequence "
          + alt.toString());
    }
    

    // Remove existing instruction
    stmts.remove();
    
    // Add new instructions at insert point
    for (Instruction newInst: alt) {
      insertPoint.add(newInst);
    }
    
    // Rewind argument iterator to instruction before replaced one
    if (stmts == insertPoint) {
      ICUtil.rewindIterator(stmts, alt.size());
    }
    return true;
  }

  /**
   * Remove unpassable vars from map
   * @param replaceInputs
   */
  private static void purgeUnpassableVars(HierarchicalMap<Var, Arg> replacements) {
    ArrayList<Var> toPurge = new ArrayList<Var>();
    for (Entry<Var, Arg> e: replacements.entrySet()) {
      Arg val = e.getValue();
      if (val.isVar() && !Semantics.canPassToChildTask(val.getVar().type())) {
        toPurge.add(e.getKey());
      }
    }
    for (Var key: toPurge) {
      replacements.remove(key);
    }
  }

}
