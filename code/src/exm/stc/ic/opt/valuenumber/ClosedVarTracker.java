package exm.stc.ic.opt.valuenumber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.StackLite;

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
   * means that var2 or var3 being closed implies that var1 is closed
   * 
   * We maintain this data structure because it lets us infer which variables
   * will be closed if we block on a given variable
   * TODO: recursive dependencies?
   */
  private final MultiMap<Var, Var> dependsOn;

  private ClosedVarTracker(Logger logger, boolean useTransitiveDeps,
      ClosedVarTracker parent, int parentStmtIndex) {
    assert(parentStmtIndex >= 0);
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
    
  
  /**
   * Check if variable was closed before a given statement index
   * Note that this is not inclusive: if the variable is closed by
   * the statement, this isn't a match.
   * @param var
   * @param recursive
   * @param stmtIndex
   * @param aliases
   * @return
   */
  public ClosedEntry getClosedEntry(Var var, boolean recursive, int stmtIndex,
      AliasFinder aliases) {
    if (logger.isTraceEnabled()) {
      logger.trace("getClosedEntry(" + var.name() + ")@" + stmtIndex +
                  " rec: " + recursive); 
    }
    
    List<Var> varAliases = aliases.getAliasesOf(var);
    ClosedEntry ce = getDirectClosedEntry(varAliases, recursive, stmtIndex);
    if (ce != null) {
      if (ce.matches(recursive, stmtIndex)) {
        // Only stop search here if we didn't find anything
        return ce;
      }
    }
    
    // Didn't find anything about this variable directly
    // Check if closed via dependency.
    if (recursive || useTransitiveDeps) {
      // Don't track dependencies for recursion, and don't do
      // inference if not allowed
      return null;
    }
    
    StackLite<Var> depStack = new StackLite<Var>();
    for (Var varAlias: varAliases) {
      depStack.addAll(directDeps(varAlias));
    }
    
    // Avoid visiting same variables multiple times
    Set<Var> visited = new HashSet<Var>();
    visited.add(var);
    while (!depStack.isEmpty()) {
      Var predecessor = depStack.pop();
      if (logger.isTraceEnabled()) {
        logger.trace("Checking predecessor: " + predecessor);
      }
      if (!visited.contains(predecessor)) {
        List<Var> predAliases = aliases.getAliasesOf(predecessor);
        ClosedEntry predCE = getDirectClosedEntry(predAliases, false,
                                                  stmtIndex);
        if (predCE != null && predCE.matches(recursive, stmtIndex)) {
          // Copy over entry to this variable
          if (logger.isTraceEnabled()) {
            logger.trace("Inferred: " + predecessor + " closed => "
                         + var + " closed"); 
          }
          closed.put(var, predCE);
          return predCE;
        }
        for (Var predAlias: predAliases) { 
          depStack.addAll(directDeps(predAlias));
        }
      }
    }
    return null;
  }

  /**
   * Return direct dependencies from var
   * @param var
   * @return
   */
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
   * Check if one of a set of variables is directly closed.
   * Returns a matching entry 
   * @param vars
   * @param recursive
   * @param stmtIndex
   * @return
   */
  private ClosedEntry getDirectClosedEntry(Collection<Var> vars,
      boolean recursive, int stmtIndex) {
    // Walk up to root, checking if this var is closed.
    ClosedVarTracker curr = this;
    int currStmtIndex = stmtIndex;
    while (curr != null) {
      for (Var var: vars) {
        for (ClosedEntry ce: curr.closed.get(var)) {
          logger.trace(var + " " + ce + " vs " + recursive + ", " + currStmtIndex);
          // Check that statement index and recursiveness is right
          if (ce.matches(recursive, currStmtIndex)) {
            logger.trace("Matches!");
            if (curr == this) {
              return ce;
            } else {
              // Record in this scope for future lookups
              ClosedEntry origScopeEntry = ClosedEntry.blockStart(ce.recursive);
              close(var, origScopeEntry);
              return origScopeEntry;
            }
          }
        }
      }
      
      currStmtIndex = curr.parentStmtIndex;
      curr = curr.parent;
    }
    return null;
  }

  /**
   * Called to mark that var is closed
   * 
   * @param var
   */
  public void close(Var var, int stmtIndex, boolean recursive) {
    close(var, new ClosedEntry(stmtIndex, recursive));
  }
  
  /**
   * If closed from before the first statement of block executes
   * @param var
   * @param recursive
   */
  public void closeBlockStart(Var var, boolean recursive) {
    close(var, ClosedEntry.blockStart(recursive));
  }

  public void close(Var var, ClosedEntry ce) {
    if (logger.isTraceEnabled())
      logger.trace(var + " is closed: " + ce);
    
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
   * Register that variable future depends on another variable.
   * I.e. we can infer that if to is closed, then from is closed
   * 
   * @param to a future
   * @param from another future.
   */
  public void setDependency(Var to, Var from) {
    assert (!Types.isPrimValue(to.type()));
    assert (!Types.isPrimValue(from.type()));
    assert (!useTransitiveDeps) : "Tracking transitive dependencies "
        + "unsafe until reordering disabled";
    if (logger.isTraceEnabled()) {
      logger.trace("Set dependency: " + from + " => " + to);
    }
    dependsOn.put(from, to);
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
    
    /** stmtIndex: the variable is closed atall statements after this index */
    final int stmtIndex;
    final boolean recursive;
    
    /**
     * Create one showing that it is closed at the block start
     * @param recursive
     * @return
     */
    private static ClosedEntry blockStart(boolean recursive) {
      return new ClosedEntry(-1, recursive);
    }

    public boolean matches(boolean recursive, int stmtIndex) {
      // Using -1 for block start guarantees that it's initialized
      // for all statements in block
      return this.stmtIndex < stmtIndex &&
                  (!recursive || this.recursive);
    }
    /**
     * For debug prints 
     */
    public String toString() {
      return (recursive ? "REC_CLOSED" : "CLOSED") + "@" + stmtIndex;
    }
  }
  
  public static interface AliasFinder {
    /**
     * Return list of aliases of var, including var itself
     * @param var
     * @return
     */
    public List<Var> getAliasesOf(Var var);
  }
}