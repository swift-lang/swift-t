package exm.stc.ic.opt.valuenumber;

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
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
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.ic.opt.InitVariables.InitState;
import exm.stc.ic.opt.Semantics;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.CongruenceType;
import exm.stc.ic.opt.valuenumber.ComputedValue.RecCV;
import exm.stc.ic.tree.ICTree.GlobalConstants;
import exm.stc.ic.tree.Opcode;

/**
 * Represent sets for a particular form of congruence, 
 * e.g. locations with same value.  We use this indirection
 * so that we can accurately track all congruence relations
 * and use them to replace variables with other alternatives
 *  
 * - All data structures are scoped, and new information
 *    added only by put() to the child map.
 */
class CongruentSets {
  private final Logger logger = Logging.getSTCLogger();
  
  private final CongruentSets parent;
  
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
   * Track sets that were merged into this one.  Allows
   * finding an alternate value if current is out of scope.
   */
  private final MultiMap<Arg, Arg> mergedInto;
  
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
   * Record the equivalence type being represented
   */
  public final CongruenceType congType;

  private CongruentSets(CongruenceType congType,
      CongruentSets parent, Set<Arg> contradictions) {
    this.congType = congType;
    this.parent = parent;
    this.canonical = new HashMap<RecCV, Arg>();
    this.canonicalInv = new MultiMap<Arg, RecCV>();
    this.mergedInto = new MultiMap<Arg, Arg>();
    this.componentIndex = new MultiMap<Arg, RecCV>();
    this.inaccessible = new HashSet<Var>();
    this.contradictions = contradictions;
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
      CongruentSets curr = this;
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
    CongruentSets curr = this;
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

  public static CongruentSets makeRoot(CongruenceType congType) {
    return new CongruentSets(congType, null, new HashSet<Arg>());
  }
  
  public CongruentSets makeChild() {
    return new CongruentSets(congType, this, contradictions);
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
    CongruentSets curr = this;
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
    CongruentSets curr = this;

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
  
  public Map<Var, Arg> getReplacementMap(InitState init) {
    return new ReplacementMap(init);
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
    CongruentSets curr = this;
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

  public boolean hasContradiction(Arg val) {
    return contradictions.contains(findCanonical(val));
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
    CongruentSets curr = this;
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
  public void addToSet(RecCV val, Arg canonicalVal) {
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
  public void changeCanonical(Arg oldCanonical,
                                           Arg newCanonical) {
    List<ToMerge> consequentMerges;
    // Do the initial merge
    consequentMerges = changeCanonicalOnce(oldCanonical, newCanonical);
    
    // The common case is that we do the one merge and we're done.
    // However, it's possible that each merge can trigger more merges.
    // Use a work queue here to iteratively process them (recursive
    // function calls would probably be a bad idea since there can be
    // long chains of merges).
    if (consequentMerges != null && !consequentMerges.isEmpty()) {
      // Merge in FIFO order
      LinkedList<ToMerge> mergeQ = new LinkedList<ToMerge>();
      mergeQ.addAll(consequentMerges);
      while (!mergeQ.isEmpty()) {
        ToMerge merge = mergeQ.pop();
        // recanonicalize in case of changes: may be redundant work
        oldCanonical = findCanonical(merge.oldSet);
        newCanonical = findCanonical(merge.newSet);
        if (!oldCanonical.equals(newCanonical)) {
          consequentMerges = changeCanonicalOnce(oldCanonical, newCanonical);
          if (consequentMerges != null) {
            mergeQ.addAll(consequentMerges);
          }
        }
      }
    }
  }
  
  /**
   * Merge together two sets
   * @param oldCanonical
   * @param newCanonical
   * 
   * @return list of additional merges that need to happen as a result of
   *      the change in components.  Must return so caller can decide
   *      what to do
   */
  private List<ToMerge> changeCanonicalOnce(Arg oldCanonical,
                                           Arg newCanonical) {
    logger.trace("Merging " + oldCanonical + " into " + newCanonical);
    assert(!oldCanonical.equals(newCanonical));
    // Check that types are compatible in sets being merged
    assert(oldCanonical.type().getImplType().equals(
           newCanonical.type().getImplType())) : "Types don't match: " +
           oldCanonical + ":" + oldCanonical.type() +
            newCanonical + " " + newCanonical.type();  
    
    // Handle situation where oldCanonical is part of another RecCV 
   List<ToMerge> toMerge = updateCanonicalComponents(oldCanonical,
                                                     newCanonical);
    
    // Find all the references to old and add new entry pointing to new
    CongruentSets curr = this;
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
    
    this.mergedInto.put(newCanonical, oldCanonical);
    
    logger.trace("Done merging " + oldCanonical + " into " + newCanonical);
    return toMerge;
  }

  public Iterable<RecCV> availableThisScope() {
    // TODO: will want to translate args back to canonical for parent scope
    List<RecCV> avail = new ArrayList<RecCV>();
    for (List<RecCV> vals: canonicalInv.values()) {
      avail.addAll(vals);
    }
    return avail;
  }

  public static class ToMerge {
    private ToMerge(Arg oldSet, Arg newSet) {
      this.oldSet = oldSet;
      this.newSet = newSet;
    }
    public final Arg oldSet;
    public final Arg newSet;
  }
  
  /**
   * Recanonicalize components.  If an old computed value had
   * a reference to oldCanonical in it, then it's no longer
   * canonical, so need to replace.
   * 
   * @param oldComponent
   * @param newComponent
   * @return list of merges that need to happen as a result of
   *      the change in components.  Must return so caller can decide
   *      what to do
   */
  private List<ToMerge> updateCanonicalComponents(Arg oldComponent,
                                                       Arg newComponent) {
    List<ToMerge> toMerge = new ArrayList<ToMerge>(); 
    CongruentSets curr = this;
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
          // Check to see if this CV bridges two sets
          Arg newCanonical = findCanonical(newOuterCV);
          if (newCanonical != null && !newCanonical.equals(canonical)) {
            // Already in a set, mark that we need to merge
            toMerge.add(new ToMerge(canonical, newCanonical));
          } else {
            // Add to same set
            addToSet(newOuterCV, canonical);
          }
          
          if (contradictions.contains(newComponent) &&
              !contradictions.contains(oldComponent)) {
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
    return toMerge;
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

  private void checkCanonicalInv(HashMap<RecCV, Arg> inEffect,
      MultiMap<Arg, RecCV> inEffectInv) {
    CongruentSets curr;
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
   * Do any canonicalization of result value here, e.g. to implement
   * constant folding, etc.
   * @param consts 
   * @param resVal
   * @return
   */
  public RecCV canonicalize(GlobalConstants consts, ArgCV origVal) {
    // First replace the args with whatever current canonical values
    RecCV result = canonicalizeInputs(origVal);
    
    if (result.isCV()) {
      // Then do additional transformations such as constant folding
      ComputedValue<RecCV> resultValue = result.cv();
      if (resultValue.isCopy() || resultValue.isAlias()) {
        // Strip out copy/alias operations, since we can use value directly
        result = result.cv().getInput(0);  
      } else if (this.congType == CongruenceType.VALUE) {
        RecCV constantFolded = tryConstantFold(resultValue);
        if (constantFolded != null) {
          result = constantFolded;
        }
      }  
    }

    // Replace a constant future with a global constant
    // This has effect of creating global constants for any used values
    if (result.isCV()) {
      result = tryReplaceGlobalConstant(consts, result);
    }
    
    return result;
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

  private RecCV tryConstantFold(ComputedValue<RecCV> val) {
    assert(this.congType == CongruenceType.VALUE);
    if (val.op == Opcode.ASYNC_OP || val.op == Opcode.LOCAL_OP) {
      List<Arg> inputs;
      if (val.op == Opcode.LOCAL_OP) {
        inputs = convertToArgs(val);
      } else {
        assert(val.op == Opcode.ASYNC_OP);
        inputs = findFutureValues(val);
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

  private RecCV tryReplaceGlobalConstant(GlobalConstants consts, RecCV result) {
    ComputedValue<RecCV> val = result.cv();
    if (val.op().isAssign()) {
      RecCV assignedVal = val.getInput(0);
      if (assignedVal.isArg() && assignedVal.arg().isConstant()) {
        Arg constVal = assignedVal.arg();
        Var globalConst = consts.getOrCreateByVal(constVal);
        result = new RecCV(globalConst.asArg());
      }
    }
    return result;
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
  private List<Arg> findFutureValues(ComputedValue<RecCV> val) {
    List<Arg> inputs = new ArrayList<Arg>(val.inputs.size());
    for (RecCV arg: val.inputs) {
      if (!arg.isArg()) {
        return null;
      }
      Arg storedConst = findValueOf(arg);
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
  private Arg findValueOf(RecCV arg) {
    assert(arg.arg().isVar());
    // Try to find constant load
    Opcode retrieveOp = Opcode.retrieveOpcode(arg.arg().getVar());
    assert(retrieveOp != null);
    RecCV retrieveVal = new RecCV(retrieveOp, arg.asList());
    return findCanonical(retrieveVal);
  }

  /**
   * @return iterator over all previous canonicals merged into this one
   */
  public Iterable<Arg> allMergedCanonicals(Arg canonical) {
    return mergedCanonicals(canonical, false);
  }
  
  public Iterable<Arg> mergedCanonicalsThisScope(Arg canonical) {
    return mergedCanonicals(canonical, true);
  }
  
  private Iterable<Arg> mergedCanonicals(Arg canonical,
                boolean followAncestors) {
    List<Arg> allMerged = new ArrayList<Arg>();
    
    Deque<Pair<CongruentSets, Arg>> work =
          new ArrayDeque<Pair<CongruentSets, Arg>>();
    work.push(Pair.create(this, canonical));
    
    while (!work.isEmpty()) {
      Pair<CongruentSets, Arg> x = work.pop();
      CongruentSets curr = x.val1;
      Arg set = x.val2;
      for (Arg mergedSet: curr.mergedInto.get(set)) {
        allMerged.add(mergedSet);
        // Track back those merged into this one
        work.push(Pair.create(curr, mergedSet));
      }
      if (followAncestors && curr.parent != null) {
        // Track back merges happening in parents
        work.push(Pair.create(curr.parent, set));
      }
    }
    return allMerged;
  }

  /**
   * Implement Map interface to allow other modules to look up replacements
   * as if they were using a regular Map.  Takes an InitState argument that
   * is used to check that a replacement is good 
   */
  public class ReplacementMap extends AbstractMap<Var, Arg> {
    private final InitState initVars;

    public ReplacementMap(InitState initVars) {
      this.initVars = initVars;
    }
    
    @Override
    public Arg get(Object key) {
      assert(key instanceof Var);
      Var v = (Var)key;
      RecCV cv = new RecCV(v.asArg());
      Arg replace = null;
      CongruentSets curr = CongruentSets.this;
      List<CongruentSets> visited = new ArrayList<CongruentSets>();
      do {
        replace = curr.canonical.get(cv);
        if (replace != null) {
          break;
        }
        visited.add(curr);
        curr = curr.parent;
      } while (curr != null);
      
      // Abort immediately if we have a contradiction
      if (replace != null && contradictions.contains(replace)) {
        // Don't do any replacements in sets with contradictions
        if (logger.isTraceEnabled()) {
          logger.trace(v + " => !!" + replace + "(" + congType + ")" +
                ": CONTRADICTION");
        }
        return null;
      }
      
      if (replace != null && replace.isVar()) {
        for (CongruentSets s: visited) {
          if (s.inaccessible.contains(replace.getVar())) {
            if (logger.isTraceEnabled()) {
              logger.trace(v + " => !!" + replace + "(" + congType + ")"
                    + ": INACCESSIBLE");
            }
            // Cannot access variable
            // TODO: find alternative?
            return null;
          }
        }
      }
      
      if (replace != null && replace.isVar()) {
        boolean output = (congType == CongruenceType.ALIAS);
        if (!initVars.isInitialized(replace.getVar(), output)) {
          // Can't use yet: not initialized
          // TODO: check alternative canonical vals
          logger.trace(v + " => " + replace + "(" + congType + ")" +
                        ": NOT INITIALIZED");
          return null;
        }
      }
      
      if (logger.isTraceEnabled()) {
        logger.trace(v + " => " + replace + "(" + congType + ")");
      }
      return replace;
    }
    
    public boolean isInitialized(Var var) {
      // TODO Auto-generated method stub
      return false;
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