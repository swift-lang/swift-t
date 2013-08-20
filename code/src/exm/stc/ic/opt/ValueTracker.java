package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.CopyOnWriteSmallSet;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.common.util.Sets;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.ComputedValue.EquivalenceType;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.CVMap;
import exm.stc.ic.tree.ICInstructions.TurbineOp;
import exm.stc.ic.tree.ICTree.Block;

/**
 * ValueTracker keep tracks of which variables are closed and which computed
 * expressions are available at different points in the IC
 */
public class ValueTracker implements CVMap {
  private final boolean reorderingAllowed;
  
  private final Logger logger;

  final ValueTracker parent;
  private final boolean varsPassedFromParent;

  /**
   * Map of variable names to value variables or literals which have been
   * created and set in this scope
   */
  final HashMap<ComputedValue, Arg> availableVals;
  
  /**
   * Blacklist of values that should not be used for substitution.
   * Shared globally within function.
   */
  final HashSet<ComputedValue> blackList;

  /**
   * What computedValues are stored in each value (inverse of availableVals)
   */
  private final MultiMap<Var, ComputedValue> varContents;
  
  /**
   * What computedValues references this variable
   */
  private final MultiMap<Var, Pair<Arg, ComputedValue>> varReferences;

  /** variables which are closed at this point in program */
  final HierarchicalSet<Var> closed;

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

  ValueTracker(Logger logger, boolean reorderingAllowed) {
    this.logger = logger;
    this.reorderingAllowed = reorderingAllowed;
    this.parent = null;
    this.varsPassedFromParent = false;
    this.availableVals = new HashMap<ComputedValue, Arg>();
    this.blackList = new HashSet<ComputedValue>();
    this.varContents = new MultiMap<Var, ComputedValue>();
    this.varReferences = new MultiMap<Var, Pair<Arg,ComputedValue>>();
    this.closed = new HierarchicalSet<Var>();
    this.recursivelyClosed = new HierarchicalSet<Var>();
    this.dependsOn = new HashMap<Var, CopyOnWriteSmallSet<Var>>();
  }

  private ValueTracker(Logger logger, boolean reorderingAllowed,
      ValueTracker parent, boolean varsPassedFromParent,
      HashMap<ComputedValue, Arg> availableVals,
      HashSet<ComputedValue> blackList,
      MultiMap<Var, ComputedValue> varContents,
      MultiMap<Var, Pair<Arg, ComputedValue>> varReferences,
      HierarchicalSet<Var> closed, HierarchicalSet<Var> recursivelyClosed,
      HashMap<Var, CopyOnWriteSmallSet<Var>> dependsOn) {
    this.logger = logger;
    this.reorderingAllowed = reorderingAllowed;
    this.parent = parent;
    this.varsPassedFromParent = varsPassedFromParent;
    this.availableVals = availableVals;
    this.blackList = blackList;
    this.varContents = varContents;
    this.varReferences = varReferences;
    this.closed = closed;
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
   * @param replace
   *          for debugging purposes, set to true if we intend to replace
   */
  public void addComputedValue(ResultVal res, Ternary replace) {
    boolean outClosed = res.outClosed();
    ComputedValue val = res.value();
    if (isAvailable(val)) {
      if (replace == Ternary.FALSE) {
        throw new STCRuntimeError("Unintended overwrite of "
            + getLocation(val) + " with " + res);
      }
    } else if (replace == Ternary.TRUE) {
      throw new STCRuntimeError("Expected overwrite of " + " with " + res
          + " but no existing value");
    }

    Arg valLoc = res.location();
    availableVals.put(val, valLoc);
    if (valLoc.isVar()) {
      varContents.put(valLoc.getVar(), val);
      if (outClosed) {
        if (logger.isTraceEnabled()) {
          logger.trace("Output " + valLoc + " was closed");
        }
        close(valLoc.getVar(), false);
      }
    }
    for (Arg input: val.getInputs()) {
      if (input.isVar()) {
        varReferences.put(input.getVar(), Pair.create(valLoc, val));
      }
    }
  }
  
  public void addComputedValues(List<ResultVal> vals, Ternary replace) {
    for (ResultVal val: vals) {
      addComputedValue(val, replace);
    }
  }
  
  /**
   * Invalidate all entries for computed value and ignore
   * future additions.
   * @param cv
   */
  public void invalidateComputedValue(ComputedValue cv) {
    blackList.add(cv);
  }

  /**
   * Return an oparg with the variable or constant for the computed value
   * 
   * @param val
   * @return
   */
  public Arg getLocation(ComputedValue val) {
    
    if (blackList.contains(val)) {
      // Should not be available
      return null;
    }
    
    boolean passRequired = false;
    ValueTracker curr = this;

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
    ValueTracker curr = this;
    List<ComputedValue> res = new ArrayList<ComputedValue>();

    while (curr != null) {
      for (ComputedValue cv: curr.varContents.get(v)) {
        if (!blackList.contains(cv)) {
          res.add(cv);
        }
      }
      curr = curr.parent;
    }
    return res == null ? Collections.<ComputedValue> emptyList() : res;
  }

  /**
   * Get computed values in which this variable is in input
   */
  public List<Pair<Arg, ComputedValue>> getReferencedCVs(Var input) {
    List<Pair<Arg, ComputedValue>> res = 
        new ArrayList<Pair<Arg,ComputedValue>>();
    ValueTracker curr = this;
    while (curr != null) {
      for (Pair<Arg, ComputedValue> x: curr.varReferences.get(input)) {
        if (!blackList.contains(x.val2)) {
          res.add(x);
        }
      }
      curr = curr.parent;
    }
    return res;
  }
  
  public void addClosed(UnifiedState closed) {
    for (Var v : closed.closed) {
      close(v, false);
    }
    for (Var v : closed.recursivelyClosed) {
      close(v, true);
    }
  }

  /**
   * Called when we enter a construct that blocked on v
   * 
   * @param var
   */
  public void close(Var var, boolean recursive) {
    if (logger.isTraceEnabled())
      logger.trace(var + " is closed");
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
        assert (!reorderingAllowed) : "Tracking transitive dependencies "
            + "unsafe until reordering disabled";
        if (logger.isTraceEnabled())
          logger.trace(deps + " are closed because of " + v);
        work.addAll(deps);
      }
    }
    if (recursive) {
      recursivelyClosed.add(var);
    }
  }

  /**
   * Register that variable future depends on all of the variables in the
   * collection, so that if future is closed, then the other variables must be
   * closed TODO: later could allow specification that something is
   * recursively closed
   * 
   * @param future
   *          a scalar future
   * @param depend
   *          more scalar futures
   */
  public void setDependencies(Var future, Collection<Var> depend) {
    assert (!Types.isScalarValue(future.type()));
    assert (!reorderingAllowed) : "Tracking transitive dependencies "
        + "unsafe until reordering disabled";
    CopyOnWriteSmallSet<Var> depset = dependsOn.get(future);
    if (depset == null) {
      depset = new CopyOnWriteSmallSet<Var>();
      dependsOn.put(future, depset);
    }
    for (Var v : depend) {
      assert (!Types.isScalarValue(v.type()));
      depset.add(v);
    }
  }

  /**
   * Make an exact copy for a nested scope, such that any changes to the new
   * copy aren't reflected in this one
   */
  ValueTracker makeChild(boolean varsPassedFromParent) {
    HashMap<Var, CopyOnWriteSmallSet<Var>> newDO = new HashMap<Var, CopyOnWriteSmallSet<Var>>();

    for (Entry<Var, CopyOnWriteSmallSet<Var>> e : dependsOn.entrySet()) {
      newDO.put(e.getKey(), new CopyOnWriteSmallSet<Var>(e.getValue()));
    }
    return new ValueTracker(logger, reorderingAllowed, this, varsPassedFromParent,
        new HashMap<ComputedValue, Arg>(),
        blackList, // blacklist shared globally
        new MultiMap<Var, ComputedValue>(),
        new MultiMap<Var, Pair<Arg, ComputedValue>>(),
        closed.makeChild(), recursivelyClosed.makeChild(), newDO);
  }
  
  /**
   * Copy over computed values from src to dst
   * @param varContents
   * @return
   */
  public static List<ResultVal> makeCopiedRVs(CVMap existing, Var dst, Arg src) {
    if (!src.isVar()) {
      return Collections.emptyList();
    }
    
    List<ResultVal> res = new ArrayList<ResultVal>();
    Var srcVar = src.getVar();
    boolean closed = existing.isClosed(srcVar);
    List<ComputedValue> cvs = existing.getVarContents(srcVar);
    if (Logging.getSTCLogger().isTraceEnabled()) {
      Logging.getSTCLogger().trace("Copy " + src + " => " + dst + " cvs: " +
                                  cvs);
    }
    for (ComputedValue cv: cvs) {
      // create new result value with conservative parameters
      res.add(new ResultVal(cv, dst.asArg(), closed,
                            EquivalenceType.VALUE, false));
    }
    
    List<Pair<Arg, ComputedValue>> inputCVs = existing.getReferencedCVs(srcVar);
    if (Logging.getSTCLogger().isTraceEnabled()) {
      Logging.getSTCLogger().trace("Copy " + src + " => " + dst + " inputCvs: " +
                          inputCVs);
    }
    for (Pair<Arg, ComputedValue> pair: inputCVs) {
      ComputedValue origCV = pair.val2;
      List<Arg> newInputs = new ArrayList<Arg>(origCV.getInputs().size());
      for (Arg input: origCV.getInputs()) {
        if (input.isVar() && input.getVar().equals(srcVar)) {
          newInputs.add(dst.asArg());
        } else {
          newInputs.add(input);
        }
      }
      
      // create new result value with conservative parameters
      res.add(new ResultVal(origCV.substituteInputs(newInputs), pair.val1,
                            closed, EquivalenceType.VALUE, false));
    }
    
    return res;
  }

  
  static class UnifiedState {

    private UnifiedState(
        Set<Var> closed, Set<Var> recursivelyClosed,
        List<ResultVal> availableVals) {
      super();
      this.closed = closed;
      this.recursivelyClosed = recursivelyClosed;
      this.availableVals = availableVals;
    }

    static final UnifiedState EMPTY = new UnifiedState(
        Collections.<Var> emptySet(), Collections.<Var> emptySet(),
        Collections.<ResultVal>emptyList());

    /**
     * Assuming that branches are exhaustive, work out the set of variables
     * closed after the conditional has executed. TODO: unify available values?
     * 
     * @param parentState
     * @param branchStates
     * @return
     */
    static UnifiedState unify(boolean reorderingAllowed,
                               ValueTracker parentState, Block parent,
                               List<ValueTracker> branchStates, List<Block> branchBlocks) {
      if (branchStates.isEmpty()) {
        return EMPTY;
      } else {
        Set<Var> closed = new HashSet<Var>();
        Set<Var> recClosed = new HashSet<Var>();
        unifyClosed(parentState, branchStates, closed, recClosed);
        
        List<ResultVal> availVals = new ArrayList<ResultVal>();
        List<ComputedValue> allUnifiedCVs = new ArrayList<ComputedValue>();
        boolean newCVs;
        do {
          List<ComputedValue> newAllBranchCVs = findAllBranchCVs(
              parentState, branchStates, allUnifiedCVs);
          Pair<List<ResultVal>, Boolean> result = unifyCVs(reorderingAllowed,
                                      parent, branchStates,
                                      branchBlocks, newAllBranchCVs);
          availVals.addAll(result.val1);
          newCVs = result.val2;
          allUnifiedCVs.addAll(newAllBranchCVs);
        } while (newCVs);

        return new UnifiedState(closed, recClosed, availVals);
      }
    }


    /**
     * Merge computed values from different conditional branches
     * @param parent
     * @param branchStates
     * @param branchBlocks
     * @param allBranchCVs
     * @return
     */
    public static Pair<List<ResultVal>, Boolean> unifyCVs(boolean
        reorderingAllowed, Block parent,
        List<ValueTracker> branchStates, List<Block> branchBlocks,
        List<ComputedValue> allBranchCVs) {
      List<ResultVal> availVals = new ArrayList<ResultVal>();
      
      boolean createdNewBranchCVs = false;
      
      for (ComputedValue cv: allBranchCVs) {
        List<Arg> locs = new ArrayList<Arg>(branchStates.size());
        boolean allVals = true;
        boolean allSame = true;
            
        int br = 0;
        Arg firstLoc = branchStates.get(0).getLocation(cv);
        for (ValueTracker bs: branchStates) {
          Arg loc = bs.getLocation(cv);
          assert(loc != null);
          
          if (loc != firstLoc && !loc.equals(firstLoc)) {
            allSame = false;
          }
          
          if (!Types.isScalarValue(loc.type())) {
            allVals = false;
          }
          locs.add(loc);
          
          Logger logger = Logging.getSTCLogger();
          if (logger.isTraceEnabled()) {
            logger.trace("Branch " + br + ": " + bs.availableVals);
          }
          br++;
        }
        

        if (Logging.getSTCLogger().isTraceEnabled()) {
          Logging.getSTCLogger().trace(cv + " appears on all branches " +
                      "allSame: " + allSame + " allVals: " + allVals);
        }
        
        if (allSame) {
          // TODO: could maybe be more specific with parameters
          availVals.add(new ResultVal(cv, firstLoc, false, 
                                      EquivalenceType.VALUE, false));
        } else {
          // declare new temporary value in outer block
          Var unifiedLoc;
          if (allVals) {
            unifiedLoc = parent.declareVariable(firstLoc.type(), 
                OptUtil.optVPrefix(parent, "unified"), Alloc.LOCAL,
                DefType.LOCAL_COMPILER, null);
          } else {
            unifiedLoc = parent.declareVariable(firstLoc.type(), 
                OptUtil.optVPrefix(parent, "unified"), Alloc.ALIAS,
                DefType.LOCAL_COMPILER, null);
          }
          
          for (int i = 0; i < branchStates.size(); i++) {
            Arg loc = locs.get(i);
            Block branchBlock = branchBlocks.get(i);
            if (allVals) {
              branchBlock.addInstruction(ICInstructions.valueSet(unifiedLoc, loc));
            } else {
              assert (loc.isVar()) : loc + " " + loc.getKind();
              branchBlock.addInstruction(TurbineOp.copyRef(unifiedLoc, loc.getVar()));
            }
            
            // Add in additional computed values resulting from copy
            ValueTracker branchState = branchStates.get(i);
            branchState.addComputedValues(
                makeCopiedRVs(branchState, unifiedLoc, loc), Ternary.MAYBE);
            createdNewBranchCVs = true;
          }
          
          // Signal that value is stored in new var
          availVals.add(new ResultVal(cv, unifiedLoc.asArg(), false,
                                    EquivalenceType.VALUE, false));
        }
      }
      
      return Pair.create(availVals, createdNewBranchCVs);
    }
    
    
    /**
     * Find computed values that appear in all branches but not parent
     * @param parentState
     * @param branchStates
     * @param alreadyAdded ignore these
     * @return
     */
    public static List<ComputedValue> findAllBranchCVs(ValueTracker parentState,
        List<ValueTracker> branchStates, List<ComputedValue> alreadyAdded) {
      List<ComputedValue> allBranchCVs = new ArrayList<ComputedValue>();
      ValueTracker firstState = branchStates.get(0);
      for (ComputedValue val: firstState.availableVals.keySet()) {
        if (!alreadyAdded.contains(val) && !parentState.isAvailable(val)) {
          int nBranches = branchStates.size();
          boolean presentInAll = true;
          for (ValueTracker otherState: branchStates.subList(1, nBranches)) {
            if (!otherState.isAvailable(val)) {
              presentInAll = false;
              break;
            }
          }
          if (presentInAll) {
            allBranchCVs.add(val);
          }
        }
      }
      return allBranchCVs;
    }
    public static void unifyClosed(ValueTracker parentState,
        List<ValueTracker> branchStates,
        Set<Var> closed, Set<Var> recClosed) {
      List<Set<Var>> branchClosed = new ArrayList<Set<Var>>();
      List<Set<Var>> branchRecClosed = new ArrayList<Set<Var>>();
      for (ValueTracker branchState: branchStates) {
        branchClosed.add(branchState.closed);
        branchRecClosed.add(branchState.recursivelyClosed);
      }
      

      // Add variables closed in first branch that aren't closed in parent
      // to output sets
      for (Var closedVar: Sets.intersectionIter(branchClosed)) {
        if (!parentState.isClosed(closedVar)) {
          closed.add(closedVar);
        }
      }
      
      for (Var recClosedVar: Sets.intersectionIter(branchRecClosed)) {
        if (!parentState.isRecursivelyClosed(recClosedVar)) {
          recClosed.add(recClosedVar);
        }
      }
    }
    
    final Set<Var> closed;
    final Set<Var> recursivelyClosed;
    final List<ResultVal> availableVals;
  }
}