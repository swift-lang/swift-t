package exm.stc.ic.opt.valuenumber;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;

import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.util.CopyOnWriteSmallSet;
import exm.stc.common.util.HierarchicalSet;

/**
 * ValueTracker keep tracks of which variables are closed and which computed
 * expressions are available at different points in the IC
 */
public class ValueTracker {
  private final boolean reorderingAllowed;
  
  private final Logger logger;

  /** variables which are closed at this point in program */
  final HierarchicalSet<Var> closed;

  /** variables which are recursively closed at this point in program */
  final HierarchicalSet<Var> recursivelyClosed;

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

  public ValueTracker(Logger logger, boolean reorderingAllowed) {
    this.logger = logger;
    this.reorderingAllowed = reorderingAllowed;
    this.closed = new HierarchicalSet<Var>();
    this.recursivelyClosed = new HierarchicalSet<Var>();
    this.dependsOn = new HashMap<Var, CopyOnWriteSmallSet<Var>>();
  }

  private ValueTracker(Logger logger, boolean reorderingAllowed,
      HierarchicalSet<Var> closed, HierarchicalSet<Var> recursivelyClosed,
      HashMap<Var, CopyOnWriteSmallSet<Var>> dependsOn) {
    this.logger = logger;
    this.reorderingAllowed = reorderingAllowed;
    this.closed = closed;
    this.recursivelyClosed = recursivelyClosed;
    this.dependsOn = dependsOn;
  }

  /**
   * Make an exact copy for a nested scope, such that any changes to the new
   * copy aren't reflected in this one
   */
  public ValueTracker makeChild() {
    HashMap<Var, CopyOnWriteSmallSet<Var>> newDO = new HashMap<Var, CopyOnWriteSmallSet<Var>>();
  
    for (Entry<Var, CopyOnWriteSmallSet<Var>> e : dependsOn.entrySet()) {
      newDO.put(e.getKey(), new CopyOnWriteSmallSet<Var>(e.getValue()));
    }
    return new ValueTracker(logger, reorderingAllowed,
        closed.makeChild(), recursivelyClosed.makeChild(), newDO);
  }

  Set<Var> getClosed() {
    return Collections.unmodifiableSet(closed);
  }

  Set<Var> getRecursivelyClosed() {
    return Collections.unmodifiableSet(recursivelyClosed);
  }

  boolean isClosed(Var var) {
    return closed.contains(var) || recursivelyClosed.contains(var);
  }

  boolean isRecursivelyClosed(Var var) {
    return recursivelyClosed.contains(var);
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
    assert (!Types.isPrimValue(future.type()));
    assert (!reorderingAllowed) : "Tracking transitive dependencies "
        + "unsafe until reordering disabled";
    CopyOnWriteSmallSet<Var> depset = dependsOn.get(future);
    if (depset == null) {
      depset = new CopyOnWriteSmallSet<Var>();
      dependsOn.put(future, depset);
    }
    for (Var v : depend) {
      assert (!Types.isPrimValue(v.type()));
      depset.add(v);
    }
  }

  public void printTraceInfo(Logger logger) {
    logger.trace("Closed variables: " + this.closed);
    logger.trace("Recursively closed variables: " + this.recursivelyClosed);
  }
}