package exm.stc.ic.opt.valuenumber;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.OpEvaluator;
import exm.stc.common.lang.Operators.BuiltinOpcode;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.Semantics;
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
 * TODO: need to increment dependency management into this
 */
public class CongruentVars implements ValueState {

  /* 
   * Arithmetic, etc:
   * - TODO: Do constant folding as we go?
   *         Lookup args in table, see if constant 
   *         -> TODO: can we do this when we "convert"?
   * - TODO: move arithmetic logic into congruence: can chase args through
   *         canonicalization table.
   *         -> TODO when to try and do arithmetic?
   */
  final Logger logger;
  final CongruentVars parent;
  final ValueTracker track;
  final CongruentSet byValue;
  final CongruentSet byAlias;
  final boolean reorderingAllowed;
  final Set<Var> closedSet;
  final Set<Var> recClosedSet;
  
  private CongruentVars(Logger logger,
                        CongruentVars parent,
                        ValueTracker track,
                        CongruentSet byValue,
                        CongruentSet byAlias,
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
  
  public CongruentVars(Logger logger, boolean reorderingAllowed) {
    this(logger, null, new ValueTracker(logger, reorderingAllowed),
        CongruentSet.makeRoot(CongruenceType.VALUE),
         CongruentSet.makeRoot(CongruenceType.ALIAS),
         reorderingAllowed);
  }
  
  public CongruentVars makeChild(boolean varsPassedFromParents) {
    CongruentVars child = new CongruentVars(logger, this,
             track.makeChild(),
             byValue.makeChild(), byAlias.makeChild(), reorderingAllowed);
    
    // If variables aren't visible in child scope, mark them as unpassable
    if (!varsPassedFromParents) {
      for (CongruentSet set: Arrays.asList(child.byValue, child.byAlias)) {
        set.purgeUnpassableVars();
      }
    }
    return child;
  }
  
  public boolean update(String errContext, ValLoc resVal) {
    if (resVal.congType() == CongruenceType.ALIAS) {
      // Update aliases only if congType matches
      if (!update(errContext, resVal.location(), resVal.value(),
                  byAlias, true)) {
        return false;
      }
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
    return update(errContext, resVal.location(), resVal.value(),
                  byValue, true);
  }
  
  private CongruentSet getCongruentSet(CongruenceType congType) {
    if (congType == CongruenceType.VALUE) {
      return byValue;
    } else {
      assert(congType == CongruenceType.ALIAS);
      return byAlias;
    }
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
  public boolean update(String errContext,
            Arg location, ArgCV value, CongruentSet congruent,
            boolean addInverses) {
    // It's possible that locCV is already congruent with something:
    // find canonical location
    Arg canonLoc = congruent.findCanonical(new RecCV(location)); 
    // canonicalize based on existing congruences
    RecCV canonVal = canonicalize(congruent, value);

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
    
    return true;
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
        CongruentSet valueSet = getCongruentSet(CongruenceType.VALUE);
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
   * Do any canonicalization of result value here, e.g. to implement
   * constant folding, etc.
   * @param resVal
   * @return
   */
  private RecCV canonicalize(CongruentSet congruent, ArgCV origVal) {
    // First replace the args with whatever current canonical values
    RecCV result = congruent.canonicalizeInputs(origVal);
    
    if (result.isCV()) {
      // Then do additional transformations such as constant folding
      ComputedValue<RecCV> resultValue = result.cv();
      if (resultValue.isCopy() || resultValue.isAlias()) {
        // Strip out copy/alias operations, since we can use value directly
        result = result.cv().getInput(0);  
      } else if (congruent.congType == CongruenceType.VALUE) {
        RecCV constantFolded = tryConstantFold(congruent, resultValue);
        if (constantFolded != null) {
          result = constantFolded;
        }
      }      
    }
    
    return result;
  }

  private RecCV tryConstantFold(CongruentSet congruent,
                      ComputedValue<RecCV> val) {
    if (val.op == Opcode.ASYNC_OP || val.op == Opcode.LOCAL_OP) {
      List<Arg> inputs;
      if (val.op == Opcode.LOCAL_OP) {
        inputs = convertToArgs(val);
      } else {
        assert(val.op == Opcode.ASYNC_OP);
        inputs = findFutureValues(val, congruent);
      }

      if (logger.isTraceEnabled()) {
        logger.trace("Try constant fold: " + val + " " + inputs);
      }
      if (inputs != null) {
        // constant fold
        Arg res = OpEvaluator.eval((BuiltinOpcode)val.subop, inputs);
        if (res != null) {
          if (logger.isDebugEnabled()) {
            logger.debug("Constant fold: " + val + " => " + res);
          }
          boolean futureResult = val.op != Opcode.LOCAL_OP;
          return valFromArg(futureResult, res);
        }
      }
    } else if (val.op == Opcode.IS_MAPPED) {
      // TODO: merge over other constantFold() implementations once we can
      //       replace constant folding pass with this analysis
      // ARGV, etc too
    }
    return null;
  }

  /**
   * Convert arg representing result of computation (maybe constant)
   * into a computed value
   * @param futureResult
   * @param constant
   * @return
   */
  private RecCV valFromArg(boolean futureResult, Arg constant) {
    if (!futureResult) {
      // Can use directly
      return new RecCV(constant);
    } else {
      // Record stored future
      return new RecCV(Opcode.assignOpcode(constant.futureType()),
                                    new RecCV(constant).asList());
    }
  }

  private List<Arg> convertToArgs(ComputedValue<RecCV> val) {
    for (RecCV arg: val.inputs) {
      if (!arg.isArg()) {
        return null;
      }
    }
    
    List<Arg> inputs = new ArrayList<Arg>(val.inputs.size());
    for (RecCV arg: val.inputs) {
      inputs.add(arg.arg());
    }
    return inputs;
  }

  /**
   * Try to find constant values of futures  
   * @param val
   * @param congruent
   * @return a list with constants in places with constant values,
   *      or future values in places with future args.  Returns null
   *      if we couldn't resolve to args.
   */
  private List<Arg> findFutureValues(ComputedValue<RecCV> val,
                                     CongruentSet congruent) {
    List<Arg> inputs = new ArrayList<Arg>(val.inputs.size());
    for (RecCV arg: val.inputs) {
      if (!arg.isArg()) {
        return null;
      }
      Arg storedConst = findValueOf(congruent, arg);
      if (storedConst != null && storedConst.isConstant()) {
        inputs.add(storedConst);
      } else {
        inputs.add(arg.arg());
      }
    }
    return inputs;
  }

  /**
   * Find if a future has a constant value stored in it
   * @param congruent
   * @param arg
   * @return a value stored to the var, or null
   */
  private Arg findValueOf(CongruentSet congruent, RecCV arg) {
    assert(arg.arg().isVar());
    // Try to find constant load
    Opcode retrieveOp = Opcode.retrieveOpcode(arg.arg().getVar());
    assert(retrieveOp != null);
    RecCV retrieveVal = new RecCV(retrieveOp, arg.asList());
    
    return congruent.findCanonical(retrieveVal);
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
      CongruentSet congruent, Arg newLoc, Arg oldLoc) {
    if (newLoc.equals(oldLoc)) {
      // Already merged
      return;
    }
    
    // Found a match!
    if (!checkNoContradiction(errContext, congruent.congType,
                              value, newLoc, oldLoc)) {
      // Constants don't match, abort!
      congruent.markContradiction(newLoc);
      congruent.markContradiction(oldLoc);
      /*
       * TODO: is more aggressive strategy of aborting and poisoning all
       * values necessary? More surgical approach might work better since
       * we can make contradiction set as big as possible
       */
      //return false;
    }
    
    Arg winner = preferred(congruent, oldLoc, newLoc);
    Arg loser = (winner == oldLoc ? newLoc : oldLoc);
    changeCanonical(congruent, loser, winner);
  }

  /**
   * Helper function to update CongruentSet plus any other info
   * @param congruent
   * @param locCV
   * @param canonicalFromVal
   */
  private void changeCanonical(CongruentSet congruent, Arg oldVal, Arg newVal) {
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
  private Arg preferred(CongruentSet congruent,
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


  public void markContradiction(ValLoc val) {
    // TODO: shouldn't be called currently?
    //  remove later if we don't need infrastructure
    throw new STCRuntimeError("markContradiction shouldn't be called from" +
    		                      " outside CongruentVars");
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
    if (val != null && !byValue.contradictions.contains(val)) {
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
    for (Var closed: unified.closed) {
      markClosed(closed, false);
    }
    for (Var closed: unified.recursivelyClosed) {
      markClosed(closed, true);
    }
    // TODO: merge in computedvalues
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

  public void setDependencies(Var ov, List<Var> in) {
    // TODO Fill this in
  }

  /**
   * Represent a set for a particular form of congruence, 
   * e.g. locations with same value.  We use this indirection
   * so that we can accurately track all congruence relations
   * and use them to replace variables with other alternatives
   *  
   * - All data structures are scoped, and new information
   *    added only by put() to the child map.
   */
  private static class CongruentSet {
    private final Logger logger = Logging.getSTCLogger();
    
    private final CongruentSet parent;
    
    /**
     * This implements a "union-find" or "disjoint sets" data structure to
     * represent congruent sets of computed values.
     * This map links every member of the set to the canonical member of
     * the set.  It is incrementally updated so that, for every member X of
     * the set including the canonical member Y, X -> Y exists. 
     * Representative of set should always be an arg (variable or constant
     *  if appropriate)
     *  A variable starts off as part of one-element set (itself)
     * @return
     */
    // TODO: does the canonical need to be a RecCV?
    //       i.e. are we interested in congruence between abstract
    //       values that don't map to variables in program?
    private final Map<RecCV, Arg> canonical;
    
    /**
     * Store reverse direction links from canonical. Must manually traverse
     * parents.  Note that links aren't deleted, so the presence of a link
     * from X -> Y in here doesn't imply that X is still canonical.
     */
    private final MultiMap<Arg, RecCV> canonicalInv;
    
    /**
     * Track places where CV1 appears inside CV2, so that we can
     * go through and recanonicalize them if needed.
     * Must manually traverse parents to find all.
     */
    private final MultiMap<Arg, RecCV> componentIndex;

    /**
     * Track variables which are not accessible from parent
     */
    private final Set<Var> inaccessible;
    
    /**
     * Equivalence classes with contradictions in them: don't substitute!
     * We will keep growing the equivalence classes (keeping same
     * representative), but never use them to do substitutions.
     * This list is shared globally among all CongruentSets in tree. 
     * 
     * TODO: contradiction if constant values !=
     * TODO: contradiction if two non-alias vars have alias equiv
     */
    private final Set<Arg> contradictions;
    
    /**
     * Map interface to look up replacements
     */
    private final ReplacementMap replacementMap;
    
    /**
     * Record the equivalence type being represented
     */
    private final CongruenceType congType;

    private CongruentSet(CongruenceType congType,
        CongruentSet parent, Set<Arg> contradictions) {
      this.congType = congType;
      this.parent = parent;
      this.canonical = new HashMap<RecCV, Arg>();
      this.canonicalInv = new MultiMap<Arg, RecCV>();
      this.componentIndex = new MultiMap<Arg, RecCV>();
      this.inaccessible = new HashSet<Var>();
      this.contradictions = contradictions;
      this.replacementMap = new ReplacementMap();
    }
    
    /**
     * Dump data structures if tracing enabled
     */
    public void printTraceInfo(Logger logger) {
      if (logger.isTraceEnabled()) {
        // Print congruence sets nicely
        MultiMap<Arg, RecCV> sets = activeSets();
        for (Entry<Arg, List<RecCV>> e: sets.entrySet()) {
          assert(e.getValue().size() > 0);
          if (e.getValue().size() == 1) {
            // Should be self-reference, don't print
            assert(e.getValue().get(0).arg().equals(e.getKey()));
          } else {
            logger.trace(congType + " cong. class " + e.getKey() + 
                         " => " + e.getValue());
            if (contradictions.contains(e.getKey())) {
              logger.trace("CONTRADICTION IN " + e.getKey());
            }
          }
        }
        // print componentIndex and inaccessible directly
        int height = 0;
        CongruentSet curr = this;
        do {
          if (!curr.componentIndex.isEmpty()) {
            logger.trace("Components#" + height + ": " + curr.componentIndex);
          }
          if (!curr.inaccessible.isEmpty()) {
            logger.trace("Inaccessible#" + height + ": " + curr.inaccessible);
          }
          height++;
          curr = curr.parent;
        } while (curr != null);
      }
    }
    
    public void validate() {
      // Building active sets performs some validation
      activeSets();
    }

    /**
     * Reconstruct current sets and check consistency between
     * canonical and canonicalInvs.
     * Note that this also checks internal consistency between
     * canonical and canonicalInv  
     * @return
     */
    private MultiMap<Arg, RecCV> activeSets() {
      HashMap<RecCV, Arg> effective = new HashMap<RecCV, Arg>();
      MultiMap<Arg, RecCV> res = new MultiMap<Arg, RecCV>();
      CongruentSet curr = this;
      do {
        for (Entry<RecCV, Arg> e: curr.canonical.entrySet()) {
          // Seen in an inner scope, not active
          if (!effective.containsKey(e.getKey())) {
            res.put(e.getValue(), e.getKey());
            effective.put(e.getKey(), e.getValue());
          }
        }
        curr = curr.parent;
      } while (curr != null);
      
      checkCanonicalInv(effective, res);
      
      return res;
    }

    public static CongruentSet makeRoot(CongruenceType congType) {
      return new CongruentSet(congType, null, new HashSet<Arg>());
    }
    
    public CongruentSet makeChild() {
      return new CongruentSet(congType, this, contradictions);
    }

    /**
     * Called if variables need to be explicitly passed from parent
     * scope.  We can't do this with some variables.  We need to set
     * up a barrier to prevent them being substituted into inner scopes
     * into which they cannot be passed
     */
    public void purgeUnpassableVars() {
      // Look at canonical args in parent scope that might be substituted
      assert(parent != null);
      for (Arg canonical: parent.canonical.values()) {
        if (canonical.isVar() && 
            !Semantics.canPassToChildTask(canonical.getVar())) {
          inaccessible.add(canonical.getVar());
        }
      }
    }
    
    public boolean inaccessible(Var var) {
      CongruentSet curr = this;
      do {
        if (curr.inaccessible.contains(var)) {
          return true;
        }
        curr = curr.parent;
      } while (curr != null);
      return false;
    }
    
    /**
     * Find canonical representative of congruence set.
     * @param arg
     * @return not null
     */
    public Arg findCanonical(Arg arg) {
      assert(arg != null);
      Arg result = findCanonical(new RecCV(arg));
      assert(result != null);
      return result;
    }
    

    public Arg findCanonical(ArgCV val) {
      return findCanonical(canonicalizeInputs(val));
    }
    
    /**
     * If val.isArg() and there is not a canonical location, this
     * will update structures to reflect that it's a single-element set.
     * @return canonical representative of congruence set, maybe null 
     */
    public Arg findCanonical(RecCV val) {
      Arg canon = null;
      CongruentSet curr = this;

      do {
        canon = curr.canonical.get(val);
        if (canon != null) {
          break;
        }
        curr = curr.parent;
      } while (curr != null);

      if (canon != null) {
        return canon;
      } else if (val.isArg()) {
        // This is the representative of the set
        // (either this is a single-element set, or this is
        //   the canonical set member)
        // Mark as single-element set
        setCanonicalEntry(val, val.arg());
        return val.arg();
      } else {
        // Cannot canonicalize
        return null;
      }
    }
    
    public Map<Var, Arg> getReplacementMap() {
      return replacementMap;
    }

    public void markContradiction(Arg arg) {
      arg = findCanonical(arg);
      boolean newContradiction = contradictions.add(arg);
      if (!newContradiction) {
        // Don't need to do work: avoid infinite loop
        return;
      }
      
      // Mark all other possible canonicals in set as contradictions,
      // since we don't want them to be used in parent or sibling contexts
      CongruentSet curr = this;
      do {
        for (RecCV setMember: curr.canonicalInv.get(arg)) {
          if (setMember.isArg() && !setMember.arg().equals(arg)) {
            if (logger.isTraceEnabled()) {
              logger.trace("Contradiction implication " + arg + " => " +
                            setMember);
            }
            contradictions.add(setMember.arg());
          }
        }
        curr = curr.parent;
      } while (curr != null);
      // Propagate contradiction to components
      curr = this;
      do {
        for (RecCV cv: curr.componentIndex.get(arg)) {
          if (logger.isTraceEnabled()) {
            logger.trace("Contradiction implication " + arg + " => " +
                          cv);
          }
          Arg set = findCanonical(cv);
          if (set != null) {
            markContradiction(set);
          }
        }
        curr = curr.parent;
      } while (curr != null);
    }

    /**
     * True if some part of the value is marked as contradictory
     * @param val
     * @return
     */
    private boolean containsContradictoryArg(RecCV val) {
      if (val.isArg()) {
        // Check for 
        if (contradictions.contains(findCanonical(val.arg()))) {
          return true;
        }
      } else {
        assert(val.isCV());
        for (RecCV input: val.cv().getInputs()) {
          if (containsContradictoryArg(input)) {
            return true;
          }
        }
      }
      return false;
    }

    /**
     * Return full list of values congruend to arg
     * @param arg
     * @return
     */
    public List<RecCV> findCongruentValues(Arg arg) {
      CongruentSet curr = this;
      Arg canonical = findCanonical(arg);
      if (contradictions.contains(arg)) {
        return Collections.emptyList();
      }

      List<RecCV> res = null;
      
      if (!arg.equals(canonical)) {
        // Canonical is different to arg
        res = new ArrayList<RecCV>();
        res.add(new RecCV(canonical));
      }
      
      do {
        for (RecCV cv: curr.canonicalInv.get(canonical)) {
          if (res == null) {
            res = new ArrayList<RecCV>();
          }
          res.add(cv);
        }
        curr = curr.parent;
      } while (curr != null);
      if (res != null) {
        return res;
      } else {
        return Collections.emptyList();
      }
    }
    
    /**
     * Add val to set in which canonicalVal is and will remain canonical
     * member
     * @param val
     * @param canonicalVal
     */
    void addToSet(RecCV val, Arg canonicalVal) {
      assert(val != null);
      assert(canonicalVal != null);
      logger.trace("Add " + val + " to " + canonicalVal);
      setCanonicalEntry(val, canonicalVal);
      if (containsContradictoryArg(val)) {
        // See if anything contradictory contained in val, if so
        // conservatively mark this congruence set as containing
        // a contradiction
        markContradiction(canonicalVal);
      }
    }
    
    /**
     * Replace oldCanonical with newCanonical as canonical member of a set
     * @param oldCanonical
     * @param newCanonical
     */
    void changeCanonical(Arg oldCanonical, Arg newCanonical) {
      logger.trace("Merging " + oldCanonical + " into " + newCanonical);
      assert(!oldCanonical.equals(newCanonical));
      // Check that types are compatible in sets being merged
      assert(oldCanonical.type().getImplType().equals(
             newCanonical.type().getImplType())) : "Types don't match: " +
             oldCanonical + ":" + oldCanonical.type() +
              newCanonical + " " + newCanonical.type();  
      
      // Handle situation where oldCanonical is part of another RecCV 
      updateCanonicalComponents(oldCanonical, newCanonical);
      
      // Find all the references to old and update to point to new
      CongruentSet curr = this;
      do {
        for (RecCV val: curr.canonicalInv.get(oldCanonical)) {
          Arg canonicalCheck = findCanonical(val);
          // Confirm that oldCanonical was actually the canonical one
          // This should only be necessary on recursive calls when
          // updating components
          if (canonicalCheck.equals(oldCanonical)) {
            // val wasn't canonical before, so just need to redirect
            // it to new canonical
            setCanonicalEntry(val, newCanonical);
          }
        }
        curr = curr.parent;
      } while (curr != null);
      // If either set being merged has contradictions, mark both as
      // contradictions
      if (contradictions.contains(oldCanonical)) {
        markContradiction(newCanonical);
      } else if (contradictions.contains(newCanonical)) {
        markContradiction(oldCanonical);
      }
      
      // Clear out-of-date entries in current scope
      // (leave outer scopes untouched since they're not harmful)
      this.canonicalInv.remove(oldCanonical);
      
      logger.trace("Done merging " + oldCanonical + " into " + newCanonical);
    }

    public Iterable<RecCV> availableThisScope() {
      List<RecCV> avail = new ArrayList<RecCV>();
      for (List<RecCV> vals: canonicalInv.values()) {
        avail.addAll(vals);
      }
      return avail;
    }

    /**
     * Recanonicalize components.  If an old computed value had
     * a reference to oldCanonical in it, then it's no longer
     * canonical, so need to replace.
     * 
     * TODO: need to handle fact that canonicalizing components
     * might result in recursively merging classes!
     * @param oldComponent
     * @param newComponent
     */
    private void updateCanonicalComponents(Arg oldComponent,
                                           Arg newComponent) {
      CongruentSet curr = this;
      do {
        for (RecCV outerCV: curr.componentIndex.get(oldComponent)) {
          assert(outerCV.isCV());
          List<RecCV> newInputs = replaceInput(
              outerCV.cv().getInputs(), oldComponent, newComponent);
          RecCV newOuterCV = new RecCV(
              outerCV.cv().substituteInputs(newInputs));
          if (logger.isTraceEnabled()) {
            logger.trace("Sub " + oldComponent + " for "
                        + newComponent + " in " + outerCV); 
          }
          this.componentIndex.put(newComponent, newOuterCV);
          Arg canonical = findCanonical(outerCV);
          if (canonical != null) {
            // If this was already part of a set, should add updated one
            addToSet(newOuterCV, canonical);
            if (contradictions.contains(newComponent) &&
                !contradictions.contains(oldComponent)) {
              // TODO: concurrent modification problem here
              
              // Propagate contradiction to set
              markContradiction(canonical);
            }
          }
          // TODO: we don't have true recursive CVs now
          //changeCanonical(outerCV, newOuterCV);
        }
        curr = curr.parent;
      } while (curr != null);
      
      // Remove component map in this scope, since we won't use
      // oldCanonical any more (leave outer scopes untouched)
      this.componentIndex.remove(oldComponent);
    }

    private List<RecCV> replaceInput(List<RecCV> oldInputs,
        Arg inputToReplace, Arg newInput) {
      List<RecCV> newInputs = new ArrayList<RecCV>(oldInputs.size());
      for (RecCV oldInput: oldInputs) {
        if (oldInput.isArg() && oldInput.arg().equals(inputToReplace)) {
          newInputs.add(new RecCV(newInput));
        } else {
          newInputs.add(oldInput);
        }
      }
      return newInputs;
    }

    private void setCanonicalEntry(RecCV val, Arg canonicalVal) {
      canonical.put(val, canonicalVal);
      canonicalInv.put(canonicalVal, val);
    }

    /**
     * Keep index of where Arg appears.  When merge
     * happens, need to go through and replace old Arg with new
     * canonical Arg.
     */
    private void addInputIndex(Arg newInput, RecCV newCV) {
      componentIndex.put(newInput, newCV);
    }

    /**
      * Convert ComputedValue parameterized with <Arg> to
      *       ComputedValue parameterized with ComputedValue
     * @param cv
     * @return
     */
    private RecCV canonicalizeInputs(ArgCV cv) {
      List<Arg> inputs = cv.getInputs();
      List<RecCV> newInputs = new ArrayList<RecCV>(inputs.size());
      for (Arg input: inputs) {
        // Canonicalize inputs to get this into a canonical form
        Arg canonicalInput = findCanonical(new RecCV(input));
        if (canonicalInput != null) {
          // Add canonical
          newInputs.add(new RecCV(canonicalInput));
        } else {
          // input is only representative of class
          // TODO: should support RecCVs as inputs
          newInputs.add(new RecCV(input));
          throw new STCRuntimeError("Not yet supported: ComputedValues " +
          		                      "in ComputedValues");
        }
      }

      RecCV newCV = new RecCV(cv.op, cv.subop, newInputs);
      if (findCanonical(newCV) == null) {
        // Add to index if not present
        for (RecCV newInput: newInputs) {
          assert(newInput.isArg());
          addInputIndex(newInput.arg(), newCV);
        }
      }
      return newCV;
    }
    
    private void checkCanonicalInv(HashMap<RecCV, Arg> inEffect,
        MultiMap<Arg, RecCV> inEffectInv) {
      CongruentSet curr;
      // Verify all entries in CanonicalInv for active keys are correct
      curr = this;
      do {
        for (Entry<Arg, List<RecCV>> e: curr.canonicalInv.entrySet()) {
          Arg key1 = e.getKey();
          if (inEffectInv.containsKey(key1)) {
            // Currently in effect
            for (RecCV val: e.getValue()) {
              Arg key2 = inEffect.get(val);
              assert(key2 != null) : "No canonical entry for " + val +
                                     " in set " + key1;
              assert(key2.equals(key1)): val + " not in consistent set:" 
                       + key1 + " vs. " + key2;
            }
          } else {
            // Check it was swallowed up into another set
            Arg newKey = inEffect.get(new RecCV(key1));
            assert(newKey != null && !newKey.equals(key1)) :
              " Expected " + key1 + " to be part of another set, but " +
              " was part of " + newKey;
          }
        }
        curr = curr.parent;
      } while (curr != null);
    }

    /**
     * Implement Map interface to allow other modules to look up replacements
     * as if they were using a regular Map.
     */
    public class ReplacementMap extends AbstractMap<Var, Arg> {

      @Override
      public Arg get(Object key) {
        assert(key instanceof Var);
        Var v = (Var)key;
        RecCV cv = new RecCV(v.asArg());
        Arg replace = null;
        CongruentSet curr = CongruentSet.this;
        List<CongruentSet> visited = new ArrayList<CongruentSet>();
        do {
          replace = curr.canonical.get(cv);
          if (replace != null) {
            break;
          }
          visited.add(curr);
          curr = curr.parent;
        } while (curr != null);
        
        if (replace != null && replace.isVar()) {
          for (CongruentSet s: visited) {
            if (s.inaccessible.contains(replace.getVar())) {
              if (logger.isTraceEnabled()) {
                logger.trace(v + " => !!" + replace + "(" + congType + ")"
                      + ": INACCESSIBLE");
              }
              // Cannot access variable
              return null;
            }
          }
        }
        if (replace != null && contradictions.contains(replace)) {
          // Don't do any replacements in sets with contradictions
          if (logger.isTraceEnabled()) {
            logger.trace(v + " => !!" + replace + "(" + congType + ")" +
                  ": CONTRADICTION");
          }
          return null;
        }
        if (logger.isTraceEnabled()) {
          logger.trace(v + " => " + replace + "(" + congType + ")");
        }
        return replace;
      }
      
      @Override
      public boolean containsKey(Object key) {
        return get(key) != null;
      }

      @Override
      public Set<Map.Entry<Var, Arg>> entrySet() {
        throw new STCRuntimeError("entrySet() not supported");
      }
      
      @Override
      public boolean isEmpty() {
        // Not strictly accurate to interface, but in the contexts it is
        // used, isEmpty() is only used to check for shortcircuiting 
        return false;
      }
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