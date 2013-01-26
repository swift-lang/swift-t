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
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.CopyOnWriteSmallSet;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.ic.ICUtil;
import exm.stc.ic.opt.ComputedValue.EquivalenceType;
import exm.stc.ic.tree.ICContinuations.BlockingVar;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.CVMap;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmRequest;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;

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

    /** variables which are closed at this point in program */
    private final HierarchicalSet<String> closed;
    
    /** mappable variables which are unmapped */
    private final HierarchicalSet<String> unmapped;

    /** variables which are recursively closed at this point in program */
    private final HierarchicalSet<String> recursivelyClosed;
    
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
    private final HashMap<String, CopyOnWriteSmallSet<String>> dependsOn;

    State(Logger logger) {
      this.logger = logger;
      this.parent = null;
      this.varsPassedFromParent = false;
      this.availableVals = new HashMap<ComputedValue, Arg>();
      this.closed = new HierarchicalSet<String>();
      this.unmapped = new HierarchicalSet<String>();
      this.recursivelyClosed = new HierarchicalSet<String>();
      this.dependsOn = new HashMap<String, CopyOnWriteSmallSet<String>>();
    }

    private State(Logger logger, State parent,
        boolean varsPassedFromParent,
        HashMap<ComputedValue, Arg> availableVals,
        HierarchicalSet<String> closed,
        HierarchicalSet<String> unmapped,
        HierarchicalSet<String> recursivelyClosed,
        HashMap<String, CopyOnWriteSmallSet<String>> dependsOn) {
      this.logger = logger;
      this.parent = parent;
      this.varsPassedFromParent = varsPassedFromParent;
      this.availableVals = availableVals;
      this.closed = closed;
      this.unmapped = unmapped;
      this.recursivelyClosed = recursivelyClosed;
      this.dependsOn = dependsOn;
    }

    public Set<String> getClosed() {
      return Collections.unmodifiableSet(closed);
    }

    public boolean isClosed(String name) {
      return closed.contains(name) || recursivelyClosed.contains(name);
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
     * @param newCV
     * @param replace for debugging purposes, set to true if we intend to 
     *        replace
     */
    public void addComputedValue(ComputedValue newCV, boolean replace) {
      if (newCV.getValLocation() != null && newCV.getValLocation().isVar()
          && newCV.getValLocation().getVar().name().equals("__ov___t25")) {
        System.err.println("Computed val: " + newCV);
        System.err.println(newCV.isOutClosed());
      }
      boolean outClosed = newCV.isOutClosed();
      if (isAvailable(newCV)) {
        if (!replace) {
          throw new STCRuntimeError("Unintended overwrite of "
              + getLocation(newCV) + " with " + newCV);
        }
      } else if (replace) {
        throw new STCRuntimeError("Expected overwrite of " + " with "
            + newCV + " but no existing value");
      }

      Arg valLoc = newCV.getValLocation();
      Opcode op = newCV.getOp();
      availableVals.put(newCV, valLoc);
      if (valLoc.isVar() && outClosed) {
        close(valLoc.getVar().name(), false);
      }
      if (op == Opcode.LOAD_BOOL || op == Opcode.LOAD_FLOAT
          || op == Opcode.LOAD_INT || op == Opcode.LOAD_STRING
          || op == Opcode.LOAD_VOID || op == Opcode.LOAD_FILE) {
        // If the value is available, it is effectively closed even if
        // the future isn't closed
        close(newCV.getInput(0).getVar().name(), true);
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
          } else if (cantPass(loc.getType())) {
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

    /**
     * Called when we enter a construct that blocked on v
     * 
     * @param varName
     */
    public void close(String varName, boolean recursive) {
      if (varName.equals("__ov___t25")) {
        System.err.println(varName + " is closed! ");
        new Exception().printStackTrace();
      }
      // Do DFS on the dependency graph to find all dependencies
      // that are now enabled
      Stack<String> work = new Stack<String>();
      work.add(varName);
      while (!work.empty()) {
        String v = work.pop();
        // they might already be in closed, but add anyway
        closed.add(v);
        CopyOnWriteSmallSet<String> deps = dependsOn.remove(v);
        if (deps != null) {
          work.addAll(deps);
        }
      }
      if (recursive) {
        recursivelyClosed.add(varName);
      }
    }
    
    public void setUnmapped(String varName) {
      unmapped.add(varName);
    }
    
    public Set<String> getUnmapped() {
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
      CopyOnWriteSmallSet<String> depset = dependsOn.get(future.name());
      if (depset == null) {
        depset = new CopyOnWriteSmallSet<String>();
        dependsOn.put(future.name(), depset);
      }
      for (Var v : depend) {
        assert (!Types.isScalarValue(v.type()));
        depset.add(v.name());
      }
    }

    /**
     * Make an exact copy for a nested scope, such that any changes to the new
     * copy aren't reflected in this one
     */
    State makeChild(boolean varsPassedFromParent) {
      HashMap<String, CopyOnWriteSmallSet<String>> newDO = 
              new HashMap<String, CopyOnWriteSmallSet<String>>();

      for (Entry<String, CopyOnWriteSmallSet<String>> e : dependsOn.entrySet()) {
        newDO.put(e.getKey(), new CopyOnWriteSmallSet<String>(e.getValue()));
      }
      return new State(logger, this, varsPassedFromParent, 
          new HashMap<ComputedValue, Arg>(), closed.makeChild(),
          unmapped.makeChild(), recursivelyClosed.makeChild(), newDO);
    }
  }

  private static List<ComputedValue> updateReplacements(
      Logger logger, Function function, Instruction inst,
      State av, HierarchicalMap<String, Arg> replaceInputs, 
      HierarchicalMap<String, Arg> replaceAll) {
    List<ComputedValue> icvs = inst.getComputedValues(av);
    if (icvs != null) {
      if (logger.isTraceEnabled()) {
        logger.trace("icvs: " + icvs.toString());
      }
      for (ComputedValue currCV : icvs) {
        if (ComputedValue.isCopy(currCV)) {
          // Copies are easy to handle: replace output of inst with input 
          // going forward
          replaceInputs.put(currCV.getValLocation().getVar().name(),
                                              currCV.getInput(0));
          continue;
        }
        Arg currLoc = currCV.getValLocation();
        if (!av.isAvailable(currCV)) {
          // Can't replace, track this value
          av.addComputedValue(currCV, false);
        } else if (currLoc.isConstant()) {
          Arg prevLoc = av.getLocation(currCV);
          if (prevLoc.isVar()) {
            assert (Types.isScalarValue(prevLoc.getVar().type()));
            // Constants are the best... might as well replace
            av.addComputedValue(currCV, true);
            // System.err.println("replace " + prevLoc + " with " + currLoc);
            replaceInputs.put(prevLoc.getVar().name(), currLoc);
          } else {
            // Should be same, otherwise bug
            assert (currLoc.equals(prevLoc)) : currCV + " = " + prevLoc +
                    " != " + currLoc + " in " + function.getName();
          }
        } else {
          final boolean usePrev;
          assert (currLoc.isVar());
          // See if we should replace
          Arg prevLoc = av.getLocation(currCV);
          if (prevLoc.isConstant()) {
            usePrev = true;
          } else {
            assert (prevLoc.isVar());
            boolean currClosed = av.isClosed(currLoc.getVar().name());
            boolean prevClosed = av.isClosed(prevLoc.getVar().name());
            if (currCV.equivType == EquivalenceType.REFERENCE) {
              // The two locations are both references to same thing, so can 
              // replace all references, including writes to currLoc
              replaceAll.put(currLoc.getVar().name(), prevLoc);
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
          if (usePrev) {
            // Do it
            replaceInputs.put(currLoc.getVar().name(), prevLoc);
            // System.err.println("replace " + currLoc + " with " + prevLoc);
          } else {
            replaceInputs.put(prevLoc.getVar().name(), currLoc);
            // System.err.println("replace " + prevLoc + " with " + currLoc);
          }
        }
      }
    } else {
      logger.trace("no icvs");
    }
    return icvs;
  }

  public static boolean cantPass(Type t) {
    return t.assignableTo(Types.V_BLOB) || t.assignableTo(Types.V_FILE);
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
    for (Function f : program.getFunctions()) {
      // Do repeated passes until converged
      boolean changes;
      int pass = 1;
      do {
        logger.trace("closed variable analysis on function " + f.getName()
            + " pass " + pass);
        changes = forwardDataflow(logger, program, f, ExecContext.CONTROL,
            f.getMainblock(), null, null, null);
        
        liftWait(logger, program, f);
        pass++;
      } while (changes);


      // We might have created some dead code to clean up
      if (Settings.getBoolean(Settings.OPT_DEAD_CODE_ELIM)) {
        DeadCodeEliminator.eliminate(logger, f);
      }
    }
    /*
     * The previous optimisation sometimes results in variables not being passed
     * into inner blocks properly. We have enough information to reconstruct
     * what should be happening, so its easier just to fix up broken things as a
     * post-optimization step
     */
    FixupVariables.fixupVariablePassing(logger, program);
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
    Block main = f.getMainblock();
    Set<String> blockingVariables = findBlockingVariables(main);
    if (blockingVariables != null) {
      Set<String> localNames = Var.nameSet(f.getInputList());
      if (logger.isTraceEnabled()) {
        logger.trace("Blocking " + f.getName() + ": " + blockingVariables);
      }
      for (String vName: blockingVariables) {
        boolean isConst = program.lookupGlobalConst(vName) != null;
        // Global constants are already set
        if (!isConst && localNames.contains(vName)) {
          // Check if a non-arg
          f.addBlockingInput(vName);
        }
      }
    }
  }
  
  /**
   * Find the set of variables required to be closed (recursively or not)
   * to make progress in block.  
   * If function inlining is enabled, exclude explicit waits since blocking
   * inputs to a function are treated as data_only upon inlining and this
   * can cause problems if they were previously explicit waits.
   * @param block
   * @return
   */
  private static Set<String> findBlockingVariables(Block block) {
    HashSet<String> blockingVariables = null;
    /*TODO: could exploit the information we have in getBlockingInputs() 
     *      to explore dependencies between variables and work out 
     *      which variables are needed to make progress */
    for (Instruction inst: block.getInstructions()) {
      if (!ProgressOpcodes.isNonProgressOpcode(inst.op)) {
        return null;
      }
    }
    
    for (Continuation c: block.getContinuations()) {
      List<BlockingVar> waitOnVars = c.blockingVars();
      List<String> waitOn;
      if (waitOnVars == null) {
        waitOn = Collections.<String>emptyList(); 
      } else {
        waitOn = new ArrayList<String>(waitOnVars.size());
        for (BlockingVar bv: waitOnVars) {
          waitOn.add(bv.var.name());
        }
      }

      assert(waitOn != null);
      //System.err.println("waitOn: " + waitOn);
      if (blockingVariables == null) {
        blockingVariables = new HashSet<String>(waitOn);
      } else {
        // Keep only those variables which block all wait statements
        blockingVariables.retainAll(waitOn);
      }
      
      try {
        // Don't pass back if inlining turned on
        if (Settings.getBoolean(Settings.OPT_FUNCTION_INLINE)) {
          if (c.getType() == ContinuationType.WAIT_STATEMENT &&
                  ((WaitStatement)c).getMode() == WaitMode.EXPLICIT) {
            blockingVariables.removeAll(waitOn);
          }
        }
      } catch (InvalidOptionException e) {
        throw new STCRuntimeError(e.getMessage());
      }
      //System.err.println("Blocking so far:" + blockingVariables);
    }
    return blockingVariables;
  }

  /**
   * 
   * @param execCx 
   * @param block
   * @param cv
   *          copy of cv from outer scope, or null if it should be initialized
   * @param replaceInputs
   *          : a set of variable replaces to do from this point in IC onwards
   * @return true if another pass might change things
   * @throws InvalidOptionException
   * @throws InvalidWriteException
   */
  private static boolean forwardDataflow(Logger logger, Program program,
      Function f, ExecContext execCx, Block block, State cv,
      HierarchicalMap<String, Arg> replaceInputs,
      HierarchicalMap<String, Arg> replaceAll) throws InvalidOptionException,
      InvalidWriteException {
    boolean anotherPassNeeded = false;
    if (cv == null) {
      cv = new State(logger);
      for (Var v: f.getBlockingInputs()) {
        cv.close(v.name(), false);
      }
      for (Var v: f.getInputList()) {
        if (Types.isScalarUpdateable(v.type())) {
          // Updateables always have a value
          cv.close(v.name(), false);
        }
      }
    }
    if (replaceInputs == null) {
      replaceInputs = new HierarchicalMap<String, Arg>();
    }
    if (replaceAll == null) {
      replaceAll = new HierarchicalMap<String, Arg>();
    }
    for (Var v : block.getVariables()) {
      // First, all constants can be treated as being set
      if (v.storage() == VarStorage.GLOBAL_CONST) {
        Arg val = program.lookupGlobalConst(v.name());
        assert (val != null): v.name();
        ComputedValue compVal = ICInstructions.assignComputedVal(v, val);
        cv.addComputedValue(compVal, cv.isAvailable(compVal));
      }
      if (v.isMapped() && Types.isFile(v.type())) {
        ComputedValue filenameVal = ICInstructions.fileNameCV(
            Arg.createVar(v.mapping()), v);
        cv.addComputedValue(filenameVal, false);
      }
      if (Types.isMappable(v.type()) && !v.isMapped()
                        && v.storage() != VarStorage.ALIAS) {
        // Var is definitely unmapped
        cv.setUnmapped(v.name());
      }
    }

    boolean anotherPass2 = forwardDataflow(logger, f, execCx, block, 
            block.instructionIterator(), cv, replaceInputs, replaceAll);
    anotherPassNeeded = anotherPassNeeded || anotherPass2;

    block.renameCleanupActions(replaceInputs, true);
    block.renameCleanupActions(replaceAll, false);
    
    
    boolean inlined = false;
    // might be able to eliminate wait statements or reduce the number
    // of vars they are blocking on
    for (int i = 0; i < block.getContinuations().size(); i++) {
      Continuation c = block.getContinuation(i);
      
      // Replace all variables in the continuation construct
      c.replaceVars(replaceInputs, true, false);
      c.replaceVars(replaceAll, false, false);
      
      Block toInline = c.tryInline(cv.closed, cv.recursivelyClosed);
      if (toInline != null) {
        anotherPassNeeded = true;
        c.inlineInto(block, toInline);
        i--; // compensate for removal of continuation
        inlined = true;
      }
    }
    
    if (inlined) {
      // Redo replacements for newly inserted instructions/continuations
      for (Instruction i: block.getInstructions()) {
        i.renameInputs(replaceInputs);
        i.renameVars(replaceAll);
      }
      for (Continuation c: block.getContinuations()) {
        c.replaceVars(replaceInputs, true, false);
        c.replaceVars(replaceAll, false, false);
      }
    }

    // Note: assume that continuations aren't added to rule engine until after
    // all code in block has run
    for (Continuation cont : block.getContinuations()) {
      State contCV = cv.makeChild(cont.inheritsParentVars());
      // additional variables may be close once we're inside continuation
      List<BlockingVar> contClosedVars = cont.blockingVars();
      if (contClosedVars != null) {
        for (BlockingVar bv : contClosedVars) {
          contCV.close(bv.var.name(), bv.recursive);
        }
      }
      
      List<Block> contBlocks = cont.getBlocks();
      for (int i = 0; i < contBlocks.size(); i++) {
        // Update based on whether values available within continuation
        HierarchicalMap<String, Arg> contReplaceInputs;
        HierarchicalMap<String, Arg> contReplaceAll;
        if (cont.inheritsParentVars()) {
          contReplaceInputs = replaceInputs;
          contReplaceAll = replaceAll;
        } else {
          contReplaceInputs = replaceInputs.makeChildMap();
          contReplaceAll = replaceAll.makeChildMap();
          purgeUnpassableVars(contReplaceInputs);
          purgeUnpassableVars(contReplaceAll);
        }
        boolean again;
        int pass = 1;
        do {
          logger.debug("closed variable analysis on nested block pass " + pass);
          again = forwardDataflow(logger, program, f, cont.childContext(execCx),
              contBlocks.get(i), contCV.makeChild(true),
              contReplaceInputs.makeChildMap(), contReplaceAll.makeChildMap());
          // changes within nested scope don't require another pass
          // over this scope
          pass++;
        } while (again);
      }
    }

    return anotherPassNeeded;
  }

  
  private static boolean forwardDataflow(Logger logger,
      Function f, ExecContext execCx, Block block,
      ListIterator<Instruction> insts, State cv,
      HierarchicalMap<String, Arg> replaceInputs,
      HierarchicalMap<String, Arg> replaceAll) throws InvalidWriteException {
    boolean anotherPassNeeded = false;
    while(insts.hasNext()) {
      Instruction inst = insts.next();
      
      if (logger.isTraceEnabled()) {
        logger.trace("Input renames in effect: " + replaceInputs);
        logger.trace("Output renames in effect: " + replaceAll);
        // TODO: no longer prints all avail vals
        logger.trace("Available values in block: " + cv.availableVals);
        logger.trace("Closed variables: " + cv.closed);
        logger.trace("-----------------------------");
        logger.trace("At instruction: " + inst);
      }
      
      // Immediately apply the variable renames
      inst.renameInputs(replaceInputs);
      inst.renameVars(replaceAll);
  

      /*
       * See if value is already computed somewhere and see if we should
       * replace variables going forward NOTE: we don't delete any instructions
       * on this pass, but rather rely on dead code elim to later clean up
       * unneeded instructions instead
       */
      updateReplacements(logger, f, inst, cv, replaceInputs, replaceAll);
      
      if (logger.isTraceEnabled()) {
        logger.trace("Instruction after updates: " + inst);
      }
      // now try to see if we can change to the immediate version
      if (switchToImmediate(logger, f, execCx, block, cv, inst, insts)) {
        // Continue pass at the start of the newly inserted sequence
        // as if it was always there
        continue;
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
      List<Var> closedOutputs = inst.getClosedOutputs();
      if (closedOutputs != null) {
        for (Var out: closedOutputs) {
          cv.close(out.name(), false);
        }
      }
    }
    return anotherPassNeeded;
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
      Instruction inst, ListIterator<Instruction> insts) {
    // First see if we can replace some futures with values
    MakeImmRequest req = inst.canMakeImmediate(
            cv.getClosed(), cv.getUnmapped(), false);

    if (req == null) {
      return false;
    }
    
    // Create replacement sequence
    Block insertContext;
    ListIterator<Instruction> insertPoint;
    if (req.mode == TaskMode.LOCAL || req.mode == TaskMode.SYNC ||
      (req.mode == TaskMode.LOCAL_CONTROL && execCx == ExecContext.CONTROL)) {
      insertContext = block;
      insertPoint = insts;
    } else {
      List<Var> used = Var.varListUnion(req.in, req.out);
      WaitStatement wait = new WaitStatement(
          fn.getName() + "-" + inst.shortOpName(),
          Collections.<Var>emptyList(), used, Collections.<Var>emptyList(),
          inst.getPriority(),
          WaitMode.TASK_DISPATCH, false, req.mode);
      insertContext = wait.getBlock();
      block.addContinuation(wait);
      // Insert at start of block
      insertPoint = insertContext.instructionIterator();
    }
    
    // Now load the values
    List<Instruction> alt = new ArrayList<Instruction>();
    List<Arg> inVals = new ArrayList<Arg>(req.in.size());
    
    // same var might appear multiple times
    HashMap<String, Arg> alreadyFetched = new HashMap<String, Arg>();  
    for (Var v : req.in) {
      Arg maybeVal;
      if (alreadyFetched.containsKey(v.name())) {
        maybeVal = alreadyFetched.get(v.name());
      } else {
        maybeVal = cv.findRetrieveResult(v);
      }
      // Can only retrieve value of future or reference
       
      if (maybeVal != null) {
        /*
         * this variable might not actually be passed through continuations to
         * the current scope, so we might have temporarily made the IC
         * invalid, but we rely on fixupVariablePassing to fix this later
         */
        inVals.add(maybeVal);
        alreadyFetched.put(v.name(), maybeVal);
      } else {
        // Generate instruction to fetch val, append to alt
        Var fetchedV = OptUtil.fetchValueOf(insertContext, alt, v);
        Arg fetched = Arg.createVar(fetchedV);
        inVals.add(fetched);
        alreadyFetched.put(v.name(), fetched);
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
    insts.remove();
    
    // Add new instructions at insert point
    for (Instruction newInst: alt) {
      insertPoint.add(newInst);
    }
    
    // Rewind argument iterator to instruction before replaced one
    if (inst == insertPoint) {
      ICUtil.rewindIterator(insts, alt.size());
    } else {
      insts.previous();
    }
    return true;
  }

  /**
   * Remove unpassable vars from map
   * @param replaceInputs
   */
  private static void purgeUnpassableVars(HierarchicalMap<String, Arg> replacements) {
    ArrayList<String> toPurge = new ArrayList<String>();
    for (Entry<String, Arg> e: replacements.entrySet()) {
      Arg val = e.getValue();
      if (val.isVar() && cantPass(val.getVar().type())) {
        toPurge.add(e.getKey());
      }
    }
    for (String key: toPurge) {
      replacements.remove(key);
    }
  }

}