package exm.stc.ic.opt.valuenumber;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.CongruenceType;
import exm.stc.ic.opt.valuenumber.ComputedValue.RecCV;
import exm.stc.ic.opt.valuenumber.ValLoc.Closed;
import exm.stc.ic.tree.ICInstructions.Instruction.ValueState;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.Opcode;

/**
 * Track which variables/values are congruent with each other
 * In particular, track which is the "canonical" version to use.
 * Looking up a variable in the map will give you back the canonical
 * congruent variable.
 * 
 * We have two notions of congruence: by-value (reading results in same thing),
 * and by-alias (reading & writing results in same effect).  
 * 
 * This module uses a notion of "Congruence Set", which contains
 * all values known to be congruent to each other, to allow
 * accurate tracking of congruence relationship.  We retain
 * enough info to answer these kinds of questions:
 * 
 * - Given a ComputedValue or Arg, what congruence set is it in?
 *                                          (by value/by alias)?
 * - Given a (by alias or by value) congruence set, what is
 *                                            the canonical Arg?
 * - Given a Var in a REFERENCE/VALUE context, what should we
 *    replace it with, if anything?
 *    
 * The notion of by-value congruence can't totally disregard whether a
 * variable is mapped: we can only have one mapped variable in each
 * congruence set, and it must be the canonical member
 *   
 * TODO: need to fix inter-variable dependency tracking
 */
public class Congruences implements ValueState {

  /* 
   * Arithmetic, etc:
   * - TODO: move arithmetic logic into congruence: can chase args through
   *         canonicalization table.
   *         -> TODO when to try and do arithmetic?
   */
  final Logger logger;
  final Congruences parent;
  final ValueTracker track;
  final CongruentSets byValue;
  final CongruentSets byAlias;
  final boolean reorderingAllowed;
  final Set<Var> closedSet;
  final Set<Var> recClosedSet;
  
  private Congruences(Logger logger,
                        Congruences parent,
                        ValueTracker track,
                        CongruentSets byValue,
                        CongruentSets byAlias,
                        boolean reorderingAllowed) {
    this.logger = logger;
    this.parent = parent;
    this.track = track;
    this.byValue = byValue;
    this.byAlias = byAlias;
    this.reorderingAllowed = reorderingAllowed;
    this.closedSet = new ClosedSet(false);
    this.recClosedSet = new ClosedSet(true);
  }
  
  public Congruences(Logger logger, boolean reorderingAllowed) {
    this(logger, null, new ValueTracker(logger, reorderingAllowed),
        CongruentSets.makeRoot(CongruenceType.VALUE),
         CongruentSets.makeRoot(CongruenceType.ALIAS),
         reorderingAllowed);
  }
  
  public Congruences makeChild(boolean varsPassedFromParents) {
    Congruences child = new Congruences(logger, this,
             track.makeChild(),
             byValue.makeChild(), byAlias.makeChild(), reorderingAllowed);
    
    // If variables aren't visible in child scope, mark them as unpassable
    if (!varsPassedFromParents) {
      for (CongruentSets set: Arrays.asList(child.byValue, child.byAlias)) {
        set.purgeUnpassableVars();
      }
    }
    return child;
  }
  
  public void update(String errContext, ValLoc resVal) {
    if (resVal.congType() == CongruenceType.ALIAS) {
      // Update aliases only if congType matches
      update(errContext, resVal.location(), resVal.value(), byAlias, true);
    } else {
      assert(resVal.congType() == CongruenceType.VALUE);
    }

    /*
     * After alias updates, mark arg as closed, so that closedness
     * is propagated to all in set.  Do this before updating value
     * congruence so that we can pick a closed variable to represent
     * the value if possible.
     */
    markClosed(resVal.location(), resVal.locClosed());
    
    // Both alias and value links result in updates to value
    update(errContext, resVal.location(), resVal.value(),
                  byValue, true);
  }
  
  /**
   * Update a congruentSet with the information that value is stored
   * in location
   * @param errContext
   * @param location
   * @param value
   * @param congruent
   * @param addInverses
   * @return
   */
  public void update(String errContext,
            Arg location, ArgCV value, CongruentSets congruent,
            boolean addInverses) {
    // LocCV may already be in congruent set
    Arg canonLoc = congruent.findCanonical(new RecCV(location)); 
    // Canonicalize value based on existing congruences
    RecCV canonVal = congruent.canonicalize(value);
  
    // Check if value is already associated with a location
    Arg canonLocFromVal = congruent.findCanonical(canonVal);
    if (canonLocFromVal == null) {
      // Handle case where value not congruent to anything yet.
      // Just add val to arg's set
      congruent.addToSet(canonVal, canonLoc);
    } else {
      // Need to merge together two existing sets
      mergeSets(errContext, canonVal, congruent, canonLoc, canonLocFromVal);
    }
    
    if (addInverses) {
      addInverses(errContext, canonLoc, canonVal);
    }
  }

  private CongruentSets getCongruentSet(CongruenceType congType) {
    if (congType == CongruenceType.VALUE) {
      return byValue;
    } else {
      assert(congType == CongruenceType.ALIAS);
      return byAlias;
    }
  }

  /**
   * Add any inverse operations that can be directly inferred from
   * a value that was just added
   * @param errContext
   * @param canonLoc
   * @param canonVal
   */
  private void addInverses(String errContext, Arg canonLoc, RecCV canonVal) {
    if (canonVal.isCV() && canonVal.cv().inputs.size() == 1) {
      ComputedValue<RecCV> cv = canonVal.cv();
      RecCV input = cv.getInput(0);
      if (input.isArg()) {
        Arg invOutput = input.arg();
        // Only add value congruences to be safe.. may be able to
        // relax this later e.g. for STORE_REF/LOAD_REF pair (TODO)
        CongruentSets valueSet = getCongruentSet(CongruenceType.VALUE);
        if (cv.op().isAssign()) {
          ArgCV invVal = ComputedValue.retrieveCompVal(canonLoc.getVar());
          update(errContext, invOutput, invVal, valueSet, false);
        } else if (cv.op().isRetrieve()) {
          ArgCV invVal = new ArgCV(Opcode.assignOpcode(invOutput.getVar()),
                                   canonLoc.asList());
          update(errContext, invOutput, invVal, valueSet, false);
        }
      }
    }
  }
  /**
   * Merge two congruence sets that are newly connected via value
   * @param errContext
   * @param resVal
   * @param congruent
   * @param newLoc representative of set with location just assigned
   * @param oldLoc representative of existing set
   */
  private void mergeSets(String errContext, RecCV value,
      CongruentSets congruent, Arg newLoc, Arg oldLoc) {
    if (newLoc.equals(oldLoc)) {
      // Already merged
      return;
    }
    
    if (!checkNoContradiction(errContext, congruent.congType,
                              value, newLoc, oldLoc)) {
      congruent.markContradiction(newLoc);
      congruent.markContradiction(oldLoc);
    }
    
    // Must merge.  Select which is the preferred value
    // (for replacement purposes, etc.)
    Arg winner = preferred(congruent, oldLoc, newLoc);
    Arg loser = (winner == oldLoc ? newLoc : oldLoc);
    changeCanonical(congruent, loser, winner);
  }

  /**
   * Helper function to update CongruentSets plus any other info
   * @param congruent
   * @param locCV
   * @param canonicalFromVal
   */
  private void changeCanonical(CongruentSets congruent, Arg oldVal, Arg newVal) {
    assert(!oldVal.equals(newVal));
    
    if (congruent.congType == CongruenceType.VALUE) {
      // Two mapped variables with same value aren't interchangeable: abort!
      if (oldVal.isMapped() != Ternary.FALSE) {
        return;
      }
    }
    
    if (congruent.congType == CongruenceType.ALIAS) {
      // Might need to mark new one as closed.  We don't need to do the
      // reverse, since if the set we're adding to was marked as closed,
      // we're done.
      if (track.isRecursivelyClosed(oldVal.getVar())) {
        track.close(newVal.getVar(), true);
      } else if (track.isClosed(oldVal.getVar())) {
        track.close(newVal.getVar(), false);
      }
    }
    
    congruent.changeCanonical(oldVal, newVal);
  }

  private boolean checkNoContradiction(String errContext,
    CongruenceType congType, RecCV value, Arg val1, Arg val2) {
    boolean contradiction = false;
    if (congType == CongruenceType.VALUE) {
      if (val1.isConstant() && val2.isConstant() && !val1.equals(val2)) {
        contradiction = true;
      }
    } else {
      assert(congType == CongruenceType.ALIAS);
      assert(val1.isVar() && val2.isVar());
      if (val1.getVar().storage() != Alloc.ALIAS &&
          val2.getVar().storage() != Alloc.ALIAS &&
          !val1.getVar().equals(val2.getVar())) {
        contradiction = true;
      }
    }
    if (contradiction) {
      Logging.uniqueWarn("Invalid code detected during optimization. "
          + "Conflicting values for " + value + ": " + val1 +
          " != " + val2 + " in " + errContext + ".\n"
          + "This may have been caused by a double-write to a variable. "
          + "Please look at any previous warnings emitted by compiler. "
          + "Otherwise this could indicate a stc bug");
      return false;
    } else {
      return true;
    }
  }

  /**
   * Check which arg is preferred as 
   * @param track
   * @param congType 
   * @param oldArg current one
   * @param newArg oldOne
   * @return the preferred of the two args
   */
  private Arg preferred(CongruentSets congruent,
                            Arg oldArg, Arg newArg) {
  
    if (congruent.congType == CongruenceType.VALUE) {
      // Constants trump all
      if (oldArg.isConstant()) {
        return oldArg;
      } else if (newArg.isConstant()) {
        return oldArg;
      }
    } else {
      assert(congruent.congType == CongruenceType.ALIAS);
      assert(oldArg.isVar() && newArg.isVar());
      // Shouldn't have alias equivalence on values
      // Prefer non-alias (i.e. direct handle)
      if (oldArg.getVar().storage() != Alloc.ALIAS) {
        return oldArg;
      } else if (newArg.getVar().storage() != Alloc.ALIAS){
        return newArg;
      }
    }
    
    if (congruent.congType == CongruenceType.VALUE) {
      // Mapped var must be canonical member of congruence set.
      // If both are mapped, keep old and caller will abort merge
      if (oldArg.isMapped() != Ternary.FALSE) {
        return oldArg;
      } else if (newArg.isMapped() != Ternary.FALSE) {
        return newArg;
      }
    }
     
    // Check if accessible (based on passability).
    // Assume new one is accessible
    if (congruent.inaccessible(oldArg.getVar())) {
       return newArg;
    }
    
    // otherwise keep old arg
    return oldArg;
  }

  private Var getCanonicalAlias(Arg varArg) {
    assert(varArg.isVar()) : varArg;
    Arg canonical = byAlias.findCanonical(varArg);
    assert(canonical.isVar()) : "Should only have a variable as" +
    		    " canonical member in ALIAS congruence relationship";
    return canonical.getVar();
  }

  public void markClosed(Var var, boolean recursive) {
    if (var.storage() == Alloc.LOCAL) {
      // Don't bother tracking this info: not actually closed
    }
    track.close(getCanonicalAlias(var.asArg()), recursive);
  }
  
  public boolean isClosed(Var var) {
    return isClosed(var.asArg());
  }
  
  public boolean isClosed(Arg varArg) {
    // Find canonical var for alias, and check if that is closed.
    if (varArg.isConstant()) {
      return true;
    }
    return track.isClosed(getCanonicalAlias(varArg));
  }
  
  public boolean isRecClosed(Var var) {
    return isRecClosed(var.asArg());
  }
  
  public boolean isRecClosed(Arg varArg) {
    if (varArg.isConstant()) {
      return true;
    }
    // Find canonical var for alias, and check if that is closed.
    return track.isRecursivelyClosed(getCanonicalAlias(varArg));
  }

  public Set<Var> getClosed() {
    return closedSet;
  }

  public Set<Var> getRecursivelyClosed() {
    return recClosedSet;
  }
  
  /**
   * Update data structures to reflect that location is closed
   * @param track
   * @param closed 
   * @param resVal
   */
  private void markClosed(Arg location, Closed closed) {
    if (closed != Closed.MAYBE_NOT && location.isVar()) {
      // Mark the canonical version of the variable closed
      Var canonAlias = getCanonicalAlias(location);
      if (closed == Closed.YES_NOT_RECURSIVE) {
        track.close(canonAlias, false);
      } else {
        assert(closed == Closed.YES_RECURSIVE);
        track.close(canonAlias, true);
      }
    }
  }
 
  /**  
   * @param mode
   * @return replacements in effect for given rename mode
   */
  public Map<Var, Arg> replacements(RenameMode mode) {
    if (mode == RenameMode.VALUE) {
      return byValue.getReplacementMap();
    } else { 
      assert(mode == RenameMode.REFERENCE);
      return byAlias.getReplacementMap();
    }
  }

  public void printTraceInfo(Logger logger) {
    byAlias.printTraceInfo(logger);
    byValue.printTraceInfo(logger);
    track.printTraceInfo(logger);
  }
  
  /**
   * Do any internal validations
   */
  public void validate() {
    byAlias.validate();
    byValue.validate();
  }
  
  /**
   * See if the result of a value retrieval is already in scope
   * 
   * @param v
   * @return
   */
  public Arg findRetrieveResult(Var v) {
    ArgCV cvRetrieve = ComputedValue.retrieveCompVal(v);
    if (cvRetrieve == null) {
      return null;
    }
    Arg val = byValue.findCanonical(cvRetrieve);
    if (val != null && !byValue.hasContradiction(val)) {
      return val;
    } else {
      return null;
    }
  }
  
  /**
   * @return canonical location for given value, null if not stored anywhere
   */
  @Override
  public Arg findCanonical(ArgCV val, CongruenceType congType) {
    return getCongruentSet(congType).findCanonical(val);
  }

  public boolean isAvailable(ArgCV val, CongruenceType congType) {
    return findCanonical(val, congType) != null;
  }
  
  private Arg findCanonical(RecCV val, CongruenceType congType) {
    return getCongruentSet(congType).findCanonical(val);
  }

  @Override
  public List<RecCV> findCongruent(Arg arg, CongruenceType congType) {
    return getCongruentSet(congType).findCongruentValues(arg);
  }
  
  public void addUnifiedValues(String errContext,
                               UnifiedValues unified) {
    // TODO: need to refine this merge to compensate for sets being
    //      named differently in child
    for (Var closed: unified.closed) {
      markClosed(closed, false);
    }
    for (Var closed: unified.recursivelyClosed) {
      markClosed(closed, true);
    }
    for (ValLoc loc: unified.availableVals) {
      update(errContext, loc);
    }
  }

  /**
   * Return iterator over values that are defined only in the
   * current scope (ignore outer scopes)
   * @param congType
   * @return
   */
  public Iterable<RecCV> availableThisScope(CongruenceType congType) {
    return getCongruentSet(congType).availableThisScope();
  }

  /**
   * Convert from recursive value back into flat representation
   * @param val
   * @param congType
   * @return
   */
  public ArgCV convertToArgs(RecCV val, CongruenceType congType) {
    if (val.isArg()) {
      if (congType == CongruenceType.VALUE) {
        return ComputedValue.makeCopy(val.arg());
      } else {
        assert(congType == CongruenceType.ALIAS);
        return ComputedValue.makeAlias(val.arg());
      }
    } else {
      assert(val.isCV());
      List<RecCV> oldInputs = val.cv().getInputs();
      List<Arg> newInputs = new ArrayList<Arg>(oldInputs.size());
      for (RecCV input: oldInputs) {
        if (input.isArg()) {
          newInputs.add(input.arg());
        } else {
          Arg canonicalInput = findCanonical(input, congType);
          if (canonicalInput == null) {
            return null;
          }
          newInputs.add(canonicalInput);
        }
      }

      return new ArgCV(val.cv().op, val.cv().subop, newInputs);
    }
  }

  /**
   * Add in closedness dependency: if to is closed, implies
   * from is closed
   * @param to
   * @param from
   * TODO: need to update to use correct canonical alias as they change
   * TODO: or alternatively use history of changes to search on demand
   */
  public void setDependencies(Var to, List<Var> fromVars) {
    Var toCanon = getCanonicalAlias(to.asArg());
    for (Var fromVar: fromVars) {
      Var fromCanon = getCanonicalAlias(fromVar.asArg());
      track.setDependency(toCanon, fromCanon);
    }
  }

  /**
   * Implement set interface for checking if var is closed
   */
  private class ClosedSet extends AbstractSet<Var> {
    ClosedSet(boolean recursive) {
      super();
      this.recursive = recursive;
    }

    private final boolean recursive;
  
    @Override
    public boolean contains(Object o) {
      assert(o instanceof Var);
      Var v = (Var)o;
      if (recursive) {
        return isRecClosed(v);
      } else {
        return isClosed(v);
      }
    }

    /**
     * @return iterator over all aliases of all closed vars
     */
    @Override
    public Iterator<Var> iterator() {
      List<Var> allClosed = new ArrayList<Var>();
      Set<Var> closedSet = recursive ? track.getRecursivelyClosed()
                                     : track.getClosed();
      for (Var closed: closedSet) {
        for (RecCV cong: findCongruent(closed.asArg(), CongruenceType.ALIAS)) {
          if (cong.isArg() && cong.arg().isVar()) {
            allClosed.add(cong.arg().getVar());
          }
        }
      }
      return allClosed.iterator();
    }
    
    @Override
    public int size() {
      throw new STCRuntimeError("size() not supported");
    }
  }
}