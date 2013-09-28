package exm.stc.ic.opt.valuenumber;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;

/**
 * ClosedVarTracker keep tracks of which variables are closed and which computed
 * expressions are available at different points in the IC
 */
public class ClosedVarTracker {
  private final boolean useTransitiveDeps;
  
  private final Logger logger;
  
  private final ClosedVarTracker parent;
  
  /**
   * Index of this within parent statements.
   */
  private final int parentStmtIndex;

  /** 
   * variables which are closed in this scope 
   * */
  private final MultiMap<Var, ClosedEntry> closed;


  /**
   * Multimap of var1 -> [ var2, var3]
   * 
   * There should only be an entry in here if var1 is not closed An entry here
   * means that var1 will be closed only after var2 and var3 are closed (e.g.
   * if var1 = var2 + var3)
   * 
   * We maintain this data structure because it lets us infer which variables
   * will be closed if we block on a given variable
   * TODO: recursive dependencies?
   */
  private final MultiMap<Var, Var> dependsOn;


  private ClosedVarTracker(Logger logger, boolean useTransitiveDeps,
      ClosedVarTracker parent, int parentStmtIndex) {
    this.logger = logger;
    this.useTransitiveDeps = useTransitiveDeps;
    this.parent = parent;
    this.parentStmtIndex = parentStmtIndex;
    this.closed = new MultiMap<Var, ClosedEntry>();
    this.dependsOn = new MultiMap<Var, Var>();
  }

  public static ClosedVarTracker makeRoot(Logger logger, boolean reorderingAllowed) {
    return new ClosedVarTracker(logger, reorderingAllowed, null, 0);
  }
  
  public ClosedVarTracker enterContinuation(int parentStmtIndex) {
    return makeChild(parentStmtIndex);
  }
  
  public ClosedVarTracker enterBlock() {
    // Don't have concept of statement index
    return makeChild(0);
  }

  /**
   * Make an exact copy for a nested scope, such that any changes to the new
   * copy aren't reflected in this one
   */
  private ClosedVarTracker makeChild(int parentStmtIndex) {
    return new ClosedVarTracker(logger, useTransitiveDeps, this, parentStmtIndex);
  }

  /**
   * Return set of variables closed in this scope that weren't closed
   * in parent scope
   * @param recursiveOnly
   * @return
   */
  public Set<Var> getScopeClosed(boolean recursiveOnly) {
    // TODO: Use transitive dependencies to expand set
    if (recursiveOnly) {
      // Extract only recursive
      Set<Var> recClosed = new HashSet<Var>();
      for (Entry<Var, List<ClosedEntry>> e: closed.entrySet()) {
        for (ClosedEntry ce: e.getValue()) {
          if (ce.recursive) {
            recClosed.add(e.getKey());
          }
        }
      }
      return recClosed;
    } else {
      // All closed vars count
      return Collections.unmodifiableSet(closed.keySet());
    }
  }

  public boolean isClosed(Var var, boolean recursive, int stmtIndex) {
    ClosedEntry ce = getClosedEntry(var, recursive, stmtIndex);
    if (ce != null && ce.matches(recursive, stmtIndex)) {
      return true;
    }
    return false;
  }

    
  public ClosedEntry getClosedEntry(Var var, boolean recursive, int stmtIndex) {
    ClosedEntry ce = getDirectClosedEntry(var, recursive, stmtIndex);
    if (ce != null && ce.matches(recursive, stmtIndex)) {
      // Only stop search here if we didn't find anything
      return ce;
    }
    
    // Didn't find anything about this variable directly
    // Check if closed via dependency.
    if (recursive || useTransitiveDeps) {
      // Don't track dependencies for recursion, and don't do
      // inference if not allowed
      return ce;
    }
    
    Deque<Var> depStack = new ArrayDeque<Var>();
    depStack.addAll(directDeps(var));
    
    // Avoid visiting same variables multiple times
    Set<Var> visited = new HashSet<Var>();
    visited.add(var);
    while (!depStack.isEmpty()) {
      Var predecessor = depStack.pop();
      if (!visited.contains(predecessor)) {
        ClosedEntry predCE = getDirectClosedEntry(predecessor, false,
                                                  stmtIndex);
        if (ce != null) {
          assert(predCE.matches(recursive, stmtIndex));
          // Copy over entry to this variable
          closed.put(var, predCE);
          return predCE;
        }
        depStack.addAll(directDeps(predecessor));
      }
    }
    return null;
  }

  private List<Var> directDeps(Var var) {
    ClosedVarTracker curr = this;
    List<Var> res = new ArrayList<Var>();
    while (curr != null) {
      res.addAll(curr.dependsOn.get(var));
      curr = curr.parent;
    }
    return res;
  }

  /**
   * Check if variable is directly closed.
   * Returns a matching entry 
   * @param var
   * @param recursive
   * @param stmtIndex
   * @return
   */
  private ClosedEntry getDirectClosedEntry(Var var, boolean recursive, int stmtIndex) {
    // Walk up to root, checking if this var is closed.
    ClosedVarTracker curr = this;
    int currStmtIndex = stmtIndex;
    while (curr != null) {
      for (ClosedEntry ce: curr.closed.get(var)) {
        // Check that statement index and recursiveness is right
        if (ce.matches(recursive, currStmtIndex)) {
          if (curr == this) {
            return ce;
          } else {
            // Record in this scope for future lookups
            ClosedEntry origScopeEntry = new ClosedEntry(0, ce.recursive);
            closed.put(var, origScopeEntry);
            return origScopeEntry;
          }
        }
      }
      
      currStmtIndex = curr.parentStmtIndex;
      curr = curr.parent;
    }
    return null;
  }

  /**
   * Called when we enter a construct that blocked on v
   * 
   * @param var
   */
  public void close(Var var, int stmtIndex, boolean recursive) {
    close(var, new ClosedEntry(stmtIndex, recursive));
  }

  public void close(Var var, ClosedEntry ce) {
    if (logger.isTraceEnabled())
      logger.trace(var + " is closed");
    
    closed.put(var, ce);
    
    // TODO: do this as a post-processing step?
    /*
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
        assert (!useTransitiveDeps) : "Tracking transitive dependencies "
            + "unsafe until reordering disabled";
        if (logger.isTraceEnabled())
          logger.trace(deps + " are closed because of " + v);
        work.addAll(deps);
      }
    }
    if (recursive) {
      recursivelyClosed.add(var);
    }*/
  }

  /**
   * Register that variable future depends on all of the variables in the
   * collection, so that if future is closed, then the other variables must be
   * closed.
   * 
   * @param to
   *          a scalar future
   * @param from
   *          more scalar futures
   */
  public void setDependency(Var to, Var from) {
    assert (!Types.isPrimValue(to.type()));
    assert (!Types.isPrimValue(from.type()));
    assert (!useTransitiveDeps) : "Tracking transitive dependencies "
        + "unsafe until reordering disabled";
    dependsOn.put(to, from);
  }

  public void printTraceInfo(Logger logger) {
    int height = 0;
    int parentIndex = -1;
    ClosedVarTracker curr = this;
    while (curr != null) {
      logger.trace("Closed vars @ ancestor " + height + 
          (curr == this ? "" : " Index " + parentIndex));
      logger.trace("closed:" + curr.closed);
      logger.trace("dependsOn: " + dependsOn);

      parentIndex = curr.parentStmtIndex;
      curr = curr.parent;
      height++;
    }
  }
  
  public static class ClosedEntry {
    private ClosedEntry(int stmtIndex, boolean recursive) {
      this.stmtIndex = stmtIndex;
      this.recursive = recursive;
    }
    final int stmtIndex;
    final boolean recursive;
    
    

    public boolean matches(boolean recursive, int stmtIndex) {
      return this.stmtIndex <= stmtIndex &&
                  (!recursive || this.recursive);
    }
    /**
     * For debug prints 
     */
    public String toString() {
      return (recursive ? "REC_CLOSED" : "CLOSED") + "@" + stmtIndex;
    }
  }
}