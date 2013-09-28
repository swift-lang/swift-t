package exm.stc.ic.opt.valuenumber;

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
import exm.stc.common.lang.TaskMode;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.CopyOnWriteSmallSet;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.Semantics;
import exm.stc.ic.opt.valuenumber.ComputedValue.EquivalenceType;

/**
 * ValueTracker keep tracks of which variables are closed and which computed
 * expressions are available at different points in the IC
 */
public class ValueTracker {
  private final boolean reorderingAllowed;
  
  private final Logger logger;

  private final ValueTracker parent;
  private final boolean varsPassedFromParent;

  /**
   * Map of variable names to value variables or literals which have been
   * created and set in this scope
   */
  final HashMap<ComputedValue<Arg>, ValLoc> availableVals;
  
  /**
   * Blacklist of values that should not be used for substitution.
   * Shared globally within function.
   */
  final HashSet<ComputedValue<Arg>> blackList;

  /**
   * What computedValues are stored in each value (inverse of availableVals)
   */
  final MultiMap<Var, ValLoc> varContents;
  
  /**
   * What computedValues references this variable
   */
  final MultiMap<Var, ValLoc> varReferences;

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
    this.parent = null;
    this.varsPassedFromParent = false;
    this.availableVals = new HashMap<ComputedValue<Arg>, ValLoc>();
    this.blackList = new HashSet<ComputedValue<Arg>>();
    this.varContents = new MultiMap<Var, ValLoc>();
    this.varReferences = new MultiMap<Var, ValLoc>();
    this.closed = new HierarchicalSet<Var>();
    this.recursivelyClosed = new HierarchicalSet<Var>();
    this.dependsOn = new HashMap<Var, CopyOnWriteSmallSet<Var>>();
  }

  private ValueTracker(Logger logger, boolean reorderingAllowed,
      ValueTracker parent, boolean varsPassedFromParent,
      HashMap<ComputedValue<Arg>, ValLoc> availableVals,
      HashSet<ComputedValue<Arg>> blackList,
      MultiMap<Var, ValLoc> varContents,
      MultiMap<Var, ValLoc> varReferences,
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

  public boolean isAvailable(ComputedValue<Arg> val) {
    return lookupCV(val) != null;
  }

  /**
   * See if the result of a value retrieval is already in scope
   * 
   * @param v
   * @return
   */
  public Arg findRetrieveResult(Var v) {
    ComputedValue<Arg> cvRetrieve = ComputedValue.retrieveCompVal(v);
    if (cvRetrieve == null) {
      return null;
    } else {
      ValLoc rv = lookupCV(cvRetrieve);
      if (rv == null) {
        return null;
      } else {
        return rv.location();
      }
    }
  }

  /**
   * 
   * @param resVal
   * @param replace
   *          for debugging purposes, set to true if we intend to replace
   */
  public void addComputedValue(ValLoc resVal, Ternary replace) {
    boolean outClosed = resVal.locClosed();
    ComputedValue<Arg> val = resVal.value();
    if (isAvailable(val)) {
      if (replace == Ternary.FALSE) {
        throw new STCRuntimeError("Unintended overwrite of "
            + lookupCV(val) + " with " + resVal);
      }
    } else if (replace == Ternary.TRUE) {
      throw new STCRuntimeError("Expected overwrite of " + " with " + resVal
          + " but no existing value");
    }

    Arg valLoc = resVal.location();
    availableVals.put(val, resVal);
    if (valLoc.isVar()) {
      varContents.put(valLoc.getVar(), resVal);
      if (outClosed && valLoc.getVar().storage() != Alloc.LOCAL) {
        if (logger.isTraceEnabled()) {
          logger.trace("Output " + valLoc + " was closed");
        }
        close(valLoc.getVar(), false);
      }
    }
    for (Arg input: val.getInputs()) {
      if (input.isVar()) {
        varReferences.put(input.getVar(), resVal);
      }
    }
  }
  
  public void addComputedValues(List<ValLoc> vals, Ternary replace) {
    for (ValLoc val: vals) {
      addComputedValue(val, replace);
    }
  }
  
  public void addUnifiedValues(UnifiedValues condClosed) {
    addClosed(condClosed);
    addComputedValues(condClosed.availableVals, Ternary.FALSE);
  }

  /**
   * Invalidate all entries for computed value and ignore
   * future additions.
   * @param cv
   */
  public void invalidateComputedValue(ComputedValue<Arg> cv) {
    blackList.add(cv);
  }

  /**
   * Return an oparg with the variable or constant for the computed value
   * 
   * @param val
   * @return
   */
  public ValLoc lookupCV(ComputedValue<Arg> val) {
    
    if (blackList.contains(val)) {
      // Should not be available
      return null;
    }
    
    boolean passRequired = false;
    ValueTracker curr = this;

    while (curr != null) {
      ValLoc rv = curr.availableVals.get(val);
      if (rv != null) {
        // Found a value, now see if it is actually visible
        if (!passRequired) {
          return rv;
        } else if (!Semantics.canPassToChildTask(rv.location().type())) {
          return null;
        } else {
          return rv;
        }
      }

      passRequired = passRequired || (!curr.varsPassedFromParent);
      curr = curr.parent;
    }

    return null;
  }

  public List<ValLoc> getVarContents(Var v) {
    ValueTracker curr = this;
    List<ValLoc> res = new ArrayList<ValLoc>();

    while (curr != null) {
      for (ValLoc rv: curr.varContents.get(v)) {
        if (!blackList.contains(rv.value())) {
          res.add(rv);
        }
      }
      curr = curr.parent;
    }
    return res;
  }

  /**
   * Get computed values in which this variable is in input
   */
  public List<ValLoc> getReferencedCVs(Var input) {
    List<ValLoc> res = new ArrayList<ValLoc>();
    ValueTracker curr = this;
    while (curr != null) {
      for (ValLoc rv: curr.varReferences.get(input)) {
        if (!blackList.contains(rv.value())) {
          res.add(rv);
        }
      }
      curr = curr.parent;
    }
    return res;
  }
  
  public void addClosed(UnifiedValues closed) {
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

  /**
   * Make an exact copy for a nested scope, such that any changes to the new
   * copy aren't reflected in this one
   */
  public ValueTracker makeChild(boolean varsPassedFromParent) {
    HashMap<Var, CopyOnWriteSmallSet<Var>> newDO = new HashMap<Var, CopyOnWriteSmallSet<Var>>();

    for (Entry<Var, CopyOnWriteSmallSet<Var>> e : dependsOn.entrySet()) {
      newDO.put(e.getKey(), new CopyOnWriteSmallSet<Var>(e.getValue()));
    }
    return new ValueTracker(logger, reorderingAllowed, this, varsPassedFromParent,
        new HashMap<ComputedValue<Arg>, ValLoc>(),
        blackList, // blacklist shared globally
        new MultiMap<Var, ValLoc>(),
        new MultiMap<Var, ValLoc>(),
        closed.makeChild(), recursivelyClosed.makeChild(), newDO);
  }
  
  /**
   * Copy over computed values from src to dst
   * @param varContents
   * @return
   */
  public static List<ValLoc> makeCopiedRVs(ValueTracker existing, Var dst, Arg src,
                TaskMode copyMode, EquivalenceType equivType) {
    if (!src.isVar()) {
      return Collections.emptyList();
    }

    List<ValLoc> res = new ArrayList<ValLoc>();

    // We assigned dst <- src, so if src == CV_1, then dst == CV_1
    // at least as far as the value goes (alias is another story)
    Var srcVar = src.getVar();
    List<ValLoc> srcVLs = existing.getVarContents(srcVar);
    Logger logger = Logging.getSTCLogger();
    
    if (logger.isTraceEnabled()) {
      logger.trace("Copy " + src + " => " + dst + " srcRVs: " + srcVLs);
    }
    for (ValLoc srcVL: srcVLs) {
      // create new result value for copy
      ValLoc dstRV = srcVL.copyOf(dst, copyMode, equivType);
      if (logger.isTraceEnabled()) {
        logger.trace("Created ValLoc " + dstRV + " based on "
                      + dst + " <- " + src + " && " + src + " == " + srcVL.value());
      }
      res.add(dstRV);
    }
    
    List<ValLoc> inputRVs = existing.getReferencedCVs(srcVar);
    if (logger.isTraceEnabled()) {
      logger.trace("Copy " + src + " => " + dst + " inputCvs: " + inputRVs);
    }
    for (ValLoc inputRV: inputRVs) {
      // create new result value with replaced inputs
      res.add(inputRV.substituteInputs(srcVar, dst.asArg(), equivType));
    }
    
    return res;
  }

  
  public void printTraceInfo(Logger logger) {
    logger.trace("Blacklist values: " + this.blackList);
    logger.trace("Closed variables: " + this.closed);
    logger.trace("Available values this block: " + this.availableVals);
    ValueTracker ancestor = this.parent;
    int up = 1;
    while (ancestor != null) {
      logger.trace("Available ancestor " + up + ": " + ancestor.availableVals);
      up++;
      ancestor = ancestor.parent;
    } 
  }
}