package exm.stc.ic.opt.valuenumber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.util.HierarchicalMap;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.Semantics;
import exm.stc.ic.opt.valuenumber.ComputedValue.EquivalenceType;
import exm.stc.ic.tree.ICTree.RenameMode;

/**
 * Track which variables/values are congruent with each other
 * In particular, track which is the "canonical" version to use.
 * Looking up a variable in the map will give you back the canonical
 * congruent variable.
 * 
 * We have two notions of congruence: by-value (reading results in same thing),
 * and by-alias (reading & writing results in same effect)
 */
public class CongruentVars {

  /*
   * TODO: add an additional level of indirection so that we can
   * properly capture all congruence relations.  We want to be
   * able to answer the questions:
   * - TODO: convert ComputedValue<Arg> to ComputedValue<ComputedValue>.
   *      -> Keep index of where ComputedValue appears.  When merge
   *         happens, need to go through and replace old CV with new
   *         canonical CV
   *       -> TODO: what does this mean for arithmetic, etc?
   * - Given a computed value C, what congruence set is it in?
   *                                      (by value/by alias)?
   *   Congruence sets disregard mapping.
   * - Given a (by alias or by value) congruence set, what is
   *                               the canonical Arg?
   * - Given two Args, which is preferred as canonical?
   *    Const/val, mapped/unmapped, closed/unclosed, first/second.
   *    NOTE: if we find constant, should make COPY[x] the cv
   * - Given a variable, what should it be replaced with?
   *    I.e. given variable what congruence set is it in, and
   *      what is canonical Arg for congruence set?
   * 
   * Data structures:
   * Parallel data structures for by-value and by-alias: 
   * - All data structures should be scoped, and new information
   *    added only by put() to the child map.
   * - congSet: Union-find on computed value congruence sets
   *        Representative of set should always be COPY[x]
   *        or ALIAS[x] as appropriate
   *        Variable starts of as part of one-element set (itself)
   *    * When adding new info (var == CV):
   *      -> Check if congSet[ALIAS|COPY[var]] or CV exist
   *      -> If matches, choose canonical and update.  If
   *          replaced canonical, need to go through and update
   *          everywhere
   *      -> If no matches add CV -> ALIAS|COPY[var]  
   * - Replacement map interface implemented as:
   *       x <- ALIAS|COPY[oldVar]
   *       newArg <- congSet[x].getInput(0)
   *    -> Mapped var must be canonical (can't replace mapped w/ unmapped)
   *      for by-value replacements
   *    -> Return null for by-value if oldVar is mapped
   * 
   * Arithmetic, etc:
   * - TODO: Do constant folding as we go?
   *         Lookup args in table, see if constant 
   * - TODO: move arithmetic logic into congruence
   * - TODO: does storing ComputedValue<ComputedValue> allow us to
   *  reconstruct arithmetic expressions?  Can we canonicalize
   *  arithmetic expressions in this scheme somehow?  Would need to
   *  do search and replace every time new canon value created
   */
  final HierarchicalMap<Var, Arg> byValue;
  final HierarchicalMap<Var, Arg> byAlias;
  
  private CongruentVars(HierarchicalMap<Var, Arg> byValue,
      HierarchicalMap<Var, Arg> byAlias) {
    this.byValue = byValue;
    this.byAlias = byAlias;
  }
  
  public CongruentVars() {
    this(new HierarchicalMap<Var, Arg>(), new HierarchicalMap<Var, Arg>());
  }
  
  public CongruentVars makeChild() {
    return new CongruentVars(byValue.makeChildMap(), byAlias.makeChildMap());
  }
  
  /**
   * Update congruent vars with resVal
   * @param logger
   * @param function
   * @param inst
   * @param av
   * @param irs
   * @param resVal
   * @return false if we found a contradiction, true on success
   */
   public boolean update(String errContext, ValueTracker av, ValLoc resVal) {
    if (resVal.value().isAlias() || resVal.value().isCopy()) {
      // Copies are easy to handle: replace output of inst with input
      // going forward
      // Get equivalence type directly in case it is a copy
      EquivalenceType equivType = resVal.equivType();
      updateCongruence(resVal.location().getVar(), resVal.value().getInput(0),
          equivType);
      return true;
    }
    if (!av.isAvailable(resVal.value())) {
      // Can't replace, track this value
      av.addComputedValue(resVal, Ternary.FALSE);
      return true;
    } else if (resVal.location().isConstant()) {
      return updateWithConstant(errContext, av, resVal);
    } else {
      updateVarCongruence(av, resVal, resVal.location().getVar(),
                          av.lookupCV(resVal.value()).location());
      return true;
    }
  }
   
  /**  
   * @param mode
   * @return replacements in effect for given rename mode
   */
  public Map<Var, Arg> replacements(RenameMode mode) {
    if (mode == RenameMode.VALUE) {
      return byValue;
    } else {
      assert(mode == RenameMode.REFERENCE);
      return byAlias;
    }
  }

  /**
   * Remove unpassable vars from map
   * 
   * @param replaceInputs
   */
  public void purgeUnpassableVars() {
    for (HierarchicalMap<Var, Arg> map: Arrays.asList(byValue, byAlias)) {
      ArrayList<Var> toPurge = new ArrayList<Var>();
      for (Entry<Var, Arg> e : map.entrySet()) {
        Arg val = e.getValue();
        if (val.isVar() && !Semantics.canPassToChildTask(val.getVar())) {
          toPurge.add(e.getKey());
        }
      }
      for (Var key: toPurge) {
        // TODO: this does copy on write.
        // It could be more efficient to insert a null, but this
        // would require updating other code that looks up values
        map.remove(key);
      }
    }
  }

  public void printTraceInfo(Logger logger) {
    logger.trace("Value renames in effect: " + byValue);
    logger.trace("Alias renames in effect: " + byAlias);
  }

  /**
   * Update given that a var is congruent with one or more things
   * @param logger
   * @param av
   * @param resVal
   */
  private void updateVarCongruence(ValueTracker av,
            ValLoc resVal, Var currVar, Arg congruentWith) {
    Logger logger = Logging.getSTCLogger();
    final Arg preferred;
    
    boolean currUnmapped = (currVar.isMapped() == Ternary.FALSE);
    boolean prevUnmapped = (congruentWith.isMapped() == Ternary.FALSE);
    if (congruentWith.isConstant()) {
      // Only makes sense to use previous
      if (currUnmapped) {
        // Can replace
        preferred = congruentWith;
      } else {
        // Can't replace mapped var
        preferred = null;
      }
    } else {
      assert (congruentWith.isVar());
      Var congruentVar = congruentWith.getVar();
      boolean currClosed = av.isClosed(currVar);
      boolean prevClosed = av.isClosed(congruentVar);
      if (resVal.equivType() == EquivalenceType.ALIAS) {
        // The two locations are both references to same thing, so can
        // replace all references, including writes to currLoc
        updateCongruence(currVar, congruentVar.asArg(), EquivalenceType.ALIAS);
      }
      
      // Must be at least as closed as other and unmapped
      boolean canSubPrev = prevUnmapped && (!prevClosed || currClosed);
      boolean canSubCurr = currUnmapped && (!currClosed || prevClosed);
      
      if (!canSubPrev && !canSubCurr) {
        preferred = null;
      } else if (canSubPrev && prevClosed || !currClosed) {
        // Use the prev value
        preferred = congruentWith;
      } else {
        /*
         * The current variable is closed but the previous isn't. Its
         * probably better to use the closed one to enable further
         * optimisations
         */
        preferred = currVar.asArg();
      }
    }

    // Now we've decided whether to use the current or previous
    // variable for the computed expression
    if (preferred != null) {
      if (preferred == congruentWith) {
        if (logger.isTraceEnabled())
          logger.trace("replace " + currVar + " with " + congruentWith);
        updateCongruence(currVar, congruentWith, EquivalenceType.VALUE);
      } else {
        if (logger.isTraceEnabled())
          logger.trace("replace " + congruentWith + " with " + currVar);
        updateCongruence(congruentWith.getVar(), currVar.asArg(),
                                                 EquivalenceType.VALUE);
      }
    }
  }

  private void updateCongruence(Var var, Arg canonical, EquivalenceType equiv) {
    HierarchicalMap<Var, Arg> map;
    if (equiv == EquivalenceType.VALUE) {
      map = byValue;
    } else {
      assert(equiv == EquivalenceType.ALIAS);
      map = byAlias;
    }
    map.put(var, canonical);
    // TODO: logic to update things that point to var
  }

  private boolean updateWithConstant(String errContext, ValueTracker av,
                                     ValLoc resVal) {
    Arg constant = resVal.location();
    assert(constant.isConstant());
    Arg prevLoc = av.lookupCV(resVal.value()).location();
    if (prevLoc.isVar()) {
      assert(Types.isPrimValue(prevLoc.getVar().type()));
      // Constants are the best... might as well replace
      av.addComputedValue(resVal, Ternary.TRUE);
      if (prevLoc.isMapped() == Ternary.FALSE) {
        updateCongruence(prevLoc.getVar(), constant, EquivalenceType.VALUE);
      }
    } else {
      // Should be same, otherwise bug (in user script or compiler)
      if (!constant.equals(prevLoc)) {
        Logging.uniqueWarn("Invalid code detected during optimization. "
                + "Conflicting values for " + resVal + ": " + prevLoc + " != "
                + constant + " in " + errContext + ".\n"
                + "This may have been caused by a double-write to a variable. "
                + "Please look at any previous warnings emitted by compiler. "
                + "Otherwise this could indicate a stc bug");

        return false;
      }
    }
    return true;
  }
}