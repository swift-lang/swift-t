package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Arrays;
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

import exm.stc.ast.Types;
import exm.stc.ast.Variable;
import exm.stc.ast.Variable.VariableStorage;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.InvalidWriteException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.ic.ICUtil;
import exm.stc.ic.opt.ComputedValue.EquivalenceType;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICContinuations.Loop;
import exm.stc.ic.tree.ICContinuations.WaitStatement;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Oparg;
import exm.stc.ic.tree.ICInstructions.OpargType;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmChange;
import exm.stc.ic.tree.ICInstructions.Instruction.MakeImmRequest;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CompFunction;
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
public class ForwardDataflow {
  
  /**
   * State keep tracks of which variables are closed and which computed
   * expressions are available at different points in the IC
   */
  private static class State {
    private final Logger logger;
    /**
     * Map of variable names to value variables or literals which have been
     * created and set at this point in the code
     */
    private final HierarchicalMap<ComputedValue, Oparg> availableVals;

    /** variables which are closed at this point in time */
    private final HierarchicalSet<String> closed;

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
    private final HashMap<String, HashSet<String>> dependsOn;

    State(Logger logger) {
      this.logger = logger;
      this.availableVals = new HierarchicalMap<ComputedValue, Oparg>();
      this.closed = new HierarchicalSet<String>();
      this.dependsOn = new HashMap<String, HashSet<String>>();
    }

    private State(Logger logger,
        HierarchicalMap<ComputedValue, Oparg> availableVals,
        HierarchicalSet<String> closed,
        HashMap<String, HashSet<String>> dependsOn) {
      this.logger = logger;
      this.availableVals = availableVals;
      this.closed = closed;
      this.dependsOn = dependsOn;
    }

    public Set<String> getClosed() {
      return Collections.unmodifiableSet(closed);
    }

    public boolean isClosed(String name) {
      return closed.contains(name);
    }

    public boolean isAvailable(ComputedValue val) {
      return availableVals.containsKey(val);
    }

    /**
     * See if the result of a value retrieval is already in scope
     * 
     * @param v
     * @return
     */
    public Oparg findRetrieveResult(Variable v) {
      ComputedValue cvRetrieve = ICInstructions.retrieveCompVal(v);
      if (cvRetrieve == null) {
        return null;
      } else {
        return this.availableVals.get(cvRetrieve);
      }
    }

    /**
     * 
     * @param newCV
     * @param replace for debugging purposes, set to true if we intend to 
     *        replace
     */
    public void addComputedValue(ComputedValue newCV, boolean replace) {
      boolean outClosed = newCV.isOutClosed();
      if (availableVals.containsKey(newCV)) {
        if (!replace) {
          throw new STCRuntimeError("Unintended overwrite of "
              + availableVals.get(newCV) + " with " + newCV);
        }
      } else if (replace) {
        throw new STCRuntimeError("Expected overwrite of " + " with "
            + newCV + " but no existing value");
      }

      Oparg valLoc = newCV.getValLocation();
      Opcode op = newCV.getOp();
      availableVals.put(newCV, valLoc);
      if (valLoc.getType() == OpargType.VAR && outClosed) {
        this.closed.add(valLoc.getVar().getName());
      }
      if (op == Opcode.LOAD_BOOL || op == Opcode.LOAD_FLOAT
          || op == Opcode.LOAD_INT || op == Opcode.LOAD_REF 
          || op == Opcode.LOAD_STRING) {
        // If the value is available, it is effectively closed even if
        // the future isn't closed
        this.closed.add(newCV.getInput(0).getVar().getName());
      }
    }

    public boolean hasComputedValue(ComputedValue compVal) {
      return availableVals.containsKey(compVal);
    }

    /**
     * Return an oparg with the variable or constant for the computed value
     * 
     * @param val
     * @return
     */
    public Oparg getLocation(ComputedValue val) {
      return availableVals.get(val);
    }

    /**
     * Called when we enter a construct that blocked on v
     * 
     * @param varName
     */
    public void close(String varName) {
      // Do DFS on the dependency graph to find all dependencies
      // that are now enabled
      Stack<String> work = new Stack<String>();
      work.add(varName);
      while (!work.empty()) {
        String v = work.pop();
        // they might already be in closed, but add anyway
        closed.add(v);
        HashSet<String> deps = dependsOn.remove(v);
        if (deps != null) {
          work.addAll(deps);
        }
      }
    }

    /**
     * Register that variable future depends on all of the variables in the
     * collection, so that if future is closed, then the other variables must be
     * closed
     * 
     * @param future
     *          a scalar future
     * @param depend
     *          more scalar futures
     */
    public void setDependencies(Variable future, Collection<Variable> depend) {
      assert (!Types.isScalarValue(future.getType()));
      HashSet<String> depset = dependsOn.get(future.getName());
      if (depset == null) {
        depset = new HashSet<String>();
        dependsOn.put(future.getName(), depset);
      }
      for (Variable v : depend) {
        assert (!Types.isScalarValue(v.getType()));
        depset.add(v.getName());
      }
    }

    /**
     * Make an exact copy for a nested scope, such that any changes to the new
     * copy aren't reflected in this one
     */
    State makeCopy() {
      HashMap<String, HashSet<String>> newDO = new HashMap<String, HashSet<String>>();

      for (Entry<String, HashSet<String>> e : dependsOn.entrySet()) {
        newDO.put(e.getKey(), new HashSet<String>(e.getValue()));
      }
      return new State(logger, availableVals.makeChildMap(),
          closed.makeChild(), newDO);
    }
  }

  private static List<ComputedValue> updateReplacements(
      Logger logger, Instruction inst,
      State av, HierarchicalMap<String, Oparg> replaceInputs, 
      HierarchicalMap<String, Oparg> replaceAll) {
    List<ComputedValue> icvs = inst.getComputedValues(av.availableVals);
    logger.trace("no icvs");
    if (icvs != null) {
      logger.trace("icvs: " + icvs.toString());
      for (ComputedValue currCV : icvs) {
        if (ComputedValue.isCopy(currCV)) {
          // Copies are easy to handle: replace output of inst with input 
          // going forward
          replaceInputs.put(currCV.getValLocation().getVar().getName(),
                                              currCV.getInput(0));
          continue;
        }
        Oparg currLoc = currCV.getValLocation();
        if (!av.isAvailable(currCV)) {
          // Can't replace, track this value
          av.addComputedValue(currCV, false);
        } else if (currLoc.isConstant()) {
          Oparg prevLoc = av.getLocation(currCV);
          if (prevLoc.getType() == OpargType.VAR) {
            assert (Types.isScalarValue(prevLoc.getVar().getType()));
            // Constants are the best... might as well replace
            av.addComputedValue(currCV, true);
            // System.err.println("replace " + prevLoc + " with " + currLoc);
            replaceInputs.put(prevLoc.getVar().getName(), currLoc);
          } else {
            // Should be same, otherwise bug
            assert (currLoc.equals(prevLoc));
          }
        } else {
          final boolean usePrev;
          assert (currLoc.getType() == OpargType.VAR);
          // See if we should replace
          Oparg prevLoc = av.getLocation(currCV);
          if (prevLoc.isConstant()) {
            usePrev = true;
          } else {
            assert (prevLoc.getType() == OpargType.VAR);
            boolean currClosed = av.isClosed(currLoc.getVar().getName());
            boolean prevClosed = av.isClosed(prevLoc.getVar().getName());
            if (currCV.equivType == EquivalenceType.REFERENCE) {
              // The two locations are both references to same thing, so can 
              // replace all references, including writes to currLoc
              replaceAll.put(currLoc.getVar().getName(), prevLoc);
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
            replaceInputs.put(currLoc.getVar().getName(), prevLoc);
            // System.err.println("replace " + currLoc + " with " + prevLoc);
          } else {
            replaceInputs.put(prevLoc.getVar().getName(), currLoc);
            // System.err.println("replace " + prevLoc + " with " + currLoc);
          }
        }
      }
    }
    return icvs;
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
  public static void forwardDataflow(Logger logger, Program program)
      throws InvalidOptionException, InvalidWriteException {
    for (CompFunction f : program.getComposites()) {
      // Do repeated passes until converged
      boolean changes;
      int pass = 1;
      do {
        logger.trace("closed variable analysis on function " + f.getName()
            + " pass " + pass);
        changes = forwardDataflow(logger, program, f, f.getMainblock(), null,
            null, null);
        
        liftWait(logger, program, f);
        pass++;
      } while (changes);

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
  private static void liftWait(Logger logger, Program program, CompFunction f) {
    if (!f.isAsync()) {
      // Can only do this optimization if the function runs asynchronously
      return;
    }
    Block main = f.getMainblock();
    Set<String> blockingVariables = findBlockingVariables(main);
    if (blockingVariables != null) {
      Set<String> localNames = Variable.nameSet(main.getVariables());
      logger.trace("Blocking " + f.getName() + ": " + blockingVariables);
      for (String vName: blockingVariables) {
        boolean isConst = program.lookupGlobalConst(vName) != null;
        // Global constants are already set
        if (!isConst && !localNames.contains(vName)) {
          // Check if a non-arg
          f.addBlockingInput(vName);
        }
      }
    }
  }

  
  /**
   * Opcodes which we don't consider as "significant work" for 
   * e.g. administrative opcodes or opcodes which don't require much
   * work. 
   */
  static HashSet<Opcode> nonProgressOpcodes = new 
                            HashSet<ICInstructions.Opcode>();
  static {
    nonProgressOpcodes.add(Opcode.ARRAY_DECR_WRITERS);
    nonProgressOpcodes.add(Opcode.LOCAL_OP);
    nonProgressOpcodes.add(Opcode.STORE_BOOL);
    nonProgressOpcodes.add(Opcode.STORE_INT);
    nonProgressOpcodes.add(Opcode.STORE_FLOAT);
    nonProgressOpcodes.add(Opcode.STORE_STRING);
    nonProgressOpcodes.add(Opcode.COPY_REF);
    nonProgressOpcodes.add(Opcode.ADDRESS_OF);
    nonProgressOpcodes.add(Opcode.LOAD_BOOL);
    nonProgressOpcodes.add(Opcode.LOAD_FLOAT);
    nonProgressOpcodes.add(Opcode.LOAD_INT);
    nonProgressOpcodes.add(Opcode.LOAD_REF);
    nonProgressOpcodes.add(Opcode.LOAD_STRING);
  }
  /**
   * Find the set of variables required to make progress in block
   * @param block
   * @return
   */
  private static Set<String> findBlockingVariables(Block block) {
    HashSet<String> blockingVariables = null;
    /*TODO: could exploit the information we have in getBlockingInputs() 
     *      to explore dependencies between variables and work out 
     *      which variables are needed to make progress */
    for (Instruction inst: block.getInstructions()) {
      if (!nonProgressOpcodes.contains(inst.op)) {
        return null;
      }
    }
    
    for (Continuation c: block.getContinuations()) {
      List<String> waitOn = null;
      if (c.getType() == ContinuationType.WAIT_STATEMENT) {
        waitOn = Variable.nameList(
            ((WaitStatement) c).getWaitVars());
      } else if (c.getType() == ContinuationType.LOOP) {
        waitOn = Arrays.asList(((Loop)c).getInitCond().getName());
      } else {
        // can't handle other continuations
        return null;
      }
      assert(waitOn != null);
      //System.err.println("waitOn: " + waitOn);
      if (blockingVariables == null) {
        blockingVariables = new HashSet<String>(waitOn);
      } else {
        // Keep only those variables which block all wait statements
        blockingVariables.retainAll(waitOn);
      }
      //System.err.println("Blocking so far:" + blockingVariables);
    }
    return blockingVariables;
  }

  /**
   * 
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
      CompFunction f, Block block, State cv,
      HierarchicalMap<String, Oparg> replaceInputs,
      HierarchicalMap<String, Oparg> replaceAll) throws InvalidOptionException,
      InvalidWriteException {
    boolean anotherPassNeeded = false;
    if (cv == null) {
      cv = new State(logger);
      for (Variable v: f.getBlockingInputs()) {
        cv.close(v.getName());
      }
      for (Variable v: f.getInputList()) {
        if (Types.isScalarUpdateable(v.getType())) {
          // Updateables always have a value
          cv.close(v.getName());
        }
      }
    }
    if (replaceInputs == null) {
      replaceInputs = new HierarchicalMap<String, Oparg>();
    }
    if (replaceAll == null) {
      replaceAll = new HierarchicalMap<String, Oparg>();
    }
    for (Variable v : block.getVariables()) {
      // First, all constants can be treated as being set
      if (v.getStorage() == VariableStorage.GLOBAL_CONST) {
        Oparg val = program.lookupGlobalConst(v.getName());
        assert (val != null): v.getName();
        ComputedValue compVal = ICInstructions.assignComputedVal(v, val);
        cv.addComputedValue(compVal, cv.hasComputedValue(compVal));
      }
      if (v.isMapped() && Types.isFile(v.getType())) {
        // filename will return the mapping
        ComputedValue filenameVal = new ComputedValue(Opcode.CALL_BUILTIN,
            "filename", Arrays.asList(Oparg.createVar(v)),
            Oparg.createVar(v.getMapping()), false, EquivalenceType.VALUE);
        cv.addComputedValue(filenameVal, false);
      }
    }

    boolean anotherPass2 =  forwardDataflow(logger, f, block, 
            block.instructionIterator(), cv, replaceInputs, replaceAll);
    anotherPassNeeded = anotherPassNeeded || anotherPass2;

    block.renameArraysToClose(replaceInputs);
    block.renameArraysToClose(replaceAll);
    
    // might be able to eliminate wait statements or reduce the number
    // of vars they are blocking on
    for (int i = 0; i < block.getContinuations().size(); i++) {
      Continuation c = block.getContinuation(i);
      
      // First make sure all variable replacements are done  
      c.replaceInputs(replaceInputs);
      c.replaceVars(replaceAll);
      Block toInline = c.tryInline(cv.closed);
      if (toInline != null) {
        anotherPassNeeded = true;
        c.inlineInto(block, toInline);
        i--; // compensate for removal of continuation
      }
    }

    // Note: assume that continuations aren't added to rule engine until after
    // all code in block has run
    for (Continuation cont : block.getContinuations()) {
      State contCV = cv.makeCopy();
      // additional variables may be close once we're inside continuation
      List<Variable> contClosedVars = cont.blockingVars();
      if (contClosedVars != null) {
        for (Variable v : contClosedVars) {
          contCV.close(v.getName());
        }
      }
      
      List<Block> contBlocks = cont.getBlocks();
      for (int i = 0; i < contBlocks.size(); i++) {
        boolean again;
        int pass = 1;
        do {
          logger.debug("closed variable analysis on nested block pass " + pass);
          again = forwardDataflow(logger, program, f, contBlocks.get(i),
              contCV.makeCopy(), replaceInputs.makeChildMap(),
                                 replaceAll.makeChildMap());
          // changes within nested scope don't require another pass
          // over this scope
          pass++;
        } while (again);
      }
    }

    // We might have created some dead code to clean up
    if (Settings.getBoolean(Settings.OPT_DEAD_CODE_ELIM)) {
      DeadCodeEliminator.eliminate(logger, block);
    }
    return anotherPassNeeded;
  }

  private static boolean forwardDataflow(Logger logger,
      CompFunction f, Block block,
      ListIterator<Instruction> insts, State cv,
      HierarchicalMap<String, Oparg> replaceInputs,
      HierarchicalMap<String, Oparg> replaceAll) throws InvalidWriteException {
    boolean anotherPassNeeded = false;
    while(insts.hasNext()) {
      Instruction inst = insts.next();
      
      logger.trace("Input renames in effect: " + replaceInputs);
      logger.trace("Output renames in effect: " + replaceAll);
      logger.trace("Available values: " + cv.availableVals);
      logger.trace("Closed variables: " + cv.closed);
      logger.trace("-----------------------------");
      logger.trace("At instruction: " + inst);
      // Immediately apply the variable renames
      inst.renameInputs(replaceInputs);
      inst.renameVars(replaceAll);
  
      // now try to see if we can change to the immediate version
      List<Instruction> alt = switchToImmediateVersion(logger, block, cv,
                                                        inst);
      if (alt != null) {
        logger.trace("Replacing instruction <" + inst + "> with sequence "
            + alt.toString());
        ICUtil.replaceInsts(insts, alt);
        
        // Continue pass at the start of the newly inserted sequence
        // as if it was always there
        ICUtil.rewindIterator(insts, alt.size());
        continue;
      }
  
      /*
       * Next: see if value is already computed somewhere and see if we should
       * replace variables going forwardNOTE: we don't delete any instructions
       * on this pass, but rather rely on dead code elim to later clean up
       * unneeded instructions instead
       */
      updateReplacements(logger, inst, cv, replaceInputs, replaceAll);
  
      // Add dependencies
      List<Variable> in = inst.getBlockingInputs();
      if (in != null) {
        for (Oparg o : inst.getOutputs()) {
          Variable ov = o.getVar();
          if (!Types.isScalarValue(ov.getType())) {
            cv.setDependencies(ov, in);
          }
        }
      }
    }
    return anotherPassNeeded;
  }


  private static List<Instruction> switchToImmediateVersion(Logger logger,
      Block block, State cv, Instruction inst) {
    // First see if we can replace some futures with values
    MakeImmRequest varsNeeded = inst.canMakeImmediate(cv.getClosed(), false);

    if (varsNeeded != null) {
      // Now load the values
      List<Instruction> alt = new ArrayList<Instruction>();
      List<Oparg> inVals = new ArrayList<Oparg>(varsNeeded.in.size());
      
      // same var might appear multiple times
      HashMap<String, Oparg> alreadyFetched = new HashMap<String, Oparg>();  
      for (Variable v : varsNeeded.in) {
        Oparg maybeVal;
        if (alreadyFetched.containsKey(v.getName())) {
          maybeVal = alreadyFetched.get(v.getName());
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
          alreadyFetched.put(v.getName(), maybeVal);
        } else {
          // Generate instruction to fetch val, append to alt
          Variable fetchedV = OptUtil.fetchValueOf(block, alt, v);
          Oparg fetched = Oparg.createVar(fetchedV);
          inVals.add(fetched);
          alreadyFetched.put(v.getName(), fetched);
        }
      }
      List<Variable> outValVars = OptUtil.declareLocalOpOutputVars(block,
                                                          varsNeeded.out);
      MakeImmChange change = inst.makeImmediate(outValVars, inVals);
      OptUtil.fixupImmChange(block, change, alt, outValVars, varsNeeded.out);
      return alt;
    } else {
      return null;
    }
  }

}
