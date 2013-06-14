package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.util.CopyOnWriteSmallSet;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.MultiMap;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Instruction.CVMap;

/**
 * ValueTracker keep tracks of which variables are closed and which computed
 * expressions are available at different points in the IC
 */
class ValueTracker implements CVMap {
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

  /** variables which are closed at this point in program */
  final HierarchicalSet<Var> closed;

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

  ValueTracker(Logger logger, boolean reorderingAllowed) {
    this.logger = logger;
    this.reorderingAllowed = reorderingAllowed;
    this.parent = null;
    this.varsPassedFromParent = false;
    this.availableVals = new HashMap<ComputedValue, Arg>();
    this.blackList = new HashSet<ComputedValue>();
    this.varContents = new MultiMap<Var, ComputedValue>();
    this.closed = new HierarchicalSet<Var>();
    this.unmapped = new HierarchicalSet<Var>();
    this.recursivelyClosed = new HierarchicalSet<Var>();
    this.dependsOn = new HashMap<Var, CopyOnWriteSmallSet<Var>>();
  }

  private ValueTracker(Logger logger, boolean reorderingAllowed,
      ValueTracker parent, boolean varsPassedFromParent,
      HashMap<ComputedValue, Arg> availableVals,
      HashSet<ComputedValue> blackList,
      MultiMap<Var, ComputedValue> varContents, HierarchicalSet<Var> closed,
      HierarchicalSet<Var> unmapped, HierarchicalSet<Var> recursivelyClosed,
      HashMap<Var, CopyOnWriteSmallSet<Var>> dependsOn) {
    this.logger = logger;
    this.reorderingAllowed = reorderingAllowed;
    this.parent = parent;
    this.varsPassedFromParent = varsPassedFromParent;
    this.availableVals = availableVals;
    this.blackList = blackList;
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
   * @param replace
   *          for debugging purposes, set to true if we intend to replace
   */
  public void addComputedValue(ResultVal res, boolean replace) {
    boolean outClosed = res.outClosed();
    if (isAvailable(res.value())) {
      if (!replace) {
        throw new STCRuntimeError("Unintended overwrite of "
            + getLocation(res.value()) + " with " + res);
      }
    } else if (replace) {
      throw new STCRuntimeError("Expected overwrite of " + " with " + res
          + " but no existing value");
    }

    Arg valLoc = res.location();
    availableVals.put(res.value(), valLoc);
    if (valLoc.isVar()) {
      varContents.put(valLoc.getVar(), res.value());
      if (outClosed) {
        if (logger.isTraceEnabled()) {
          logger.trace("Output " + valLoc + " was closed");
        }
        close(valLoc.getVar(), false);
      }
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
    return res == null ? Collections.<ComputedValue> emptyList() : res;
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

  public void setUnmapped(Var var) {
    unmapped.add(var);
  }

  public Set<Var> getUnmapped() {
    return Collections.unmodifiableSet(unmapped);
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
        new MultiMap<Var, ComputedValue>(), closed.makeChild(),
        unmapped.makeChild(), recursivelyClosed.makeChild(), newDO);
  }
  
  static class UnifiedState {
    private UnifiedState(Set<Var> closed, Set<Var> recursivelyClosed) {
      super();
      this.closed = closed;
      this.recursivelyClosed = recursivelyClosed;
    }

    static final UnifiedState EMPTY = new UnifiedState(
        Collections.<Var> emptySet(), Collections.<Var> emptySet());

    /**
     * Assuming that branches are exhaustive, work out the set of variables
     * closed after the conditional has executed. TODO: unify available values?
     * 
     * @param parentState
     * @param branchStates
     * @return
     */
    static UnifiedState unify(ValueTracker parentState, List<ValueTracker> branchStates) {
      if (branchStates.isEmpty()) {
        return EMPTY;
      } else {
        ValueTracker firstState = branchStates.get(0);
        Set<Var> closed = new HashSet<Var>();
        Set<Var> recClosed = new HashSet<Var>();
        // Start off with variables closed in first branch that aren't
        // closed in parent
        for (Var v : firstState.getClosed()) {
          if (!parentState.isClosed(v)) {
            closed.add(v);
          }
        }
        for (Var v : firstState.getRecursivelyClosed()) {
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
}