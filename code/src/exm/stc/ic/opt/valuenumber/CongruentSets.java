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
import exm.stc.common.Settings;
import exm.stc.common.exceptions.InvalidOptionException;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.ic.opt.InitVariables.InitState;
import exm.stc.ic.opt.Semantics;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.CongruenceType;
import exm.stc.ic.opt.valuenumber.ComputedValue.RecCV;
import exm.stc.ic.tree.ICTree.GlobalConstants;

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
   */
  private final Set<Arg> contradictions;
  
  /**
   * Queue of merges that need to be completed before returning
   */
  private final LinkedList<ToMerge> mergeQueue;
  
  /**
   * List of future vars that may have obtained a constant value,
   * requiring further processing before returning
   */
  private final LinkedList<Var> futureValQueue;
  
  private final boolean constShareEnabled;
  private final boolean constFoldEnabled;
  
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
    this.mergeQueue = new LinkedList<ToMerge>();
    this.futureValQueue = new LinkedList<Var>();
    
    if (parent != null) {
      this.constShareEnabled = parent.constShareEnabled;
      this.constFoldEnabled = parent.constFoldEnabled;
    } else {
      try {
        this.constShareEnabled = Settings.getBoolean(
                                        Settings.OPT_SHARED_CONSTANTS);
        this.constFoldEnabled = Settings.getBoolean(
                                        Settings.OPT_CONSTANT_FOLD);
      } catch (InvalidOptionException e) {
        e.printStackTrace();
        throw new STCRuntimeError(e.getMessage());
      }
    }
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
    
    // Should not have any outstanding work to complete
    assert(mergeQueue.isEmpty()) : mergeQueue;
    assert(futureValQueue.isEmpty()) : futureValQueue;
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
  public void addToSet(GlobalConstants consts, RecCV val, Arg canonicalVal) {
    assert(val != null);
    assert(canonicalVal != null);
    addSetEntry(val, canonicalVal);
    processQueues(consts);
  }

  private void addSetEntry(RecCV val, Arg canonicalVal) {
    logger.trace("Add " + val + " to " + canonicalVal);
    setCanonicalEntry(val, canonicalVal);
    if (containsContradictoryArg(val)) {
      // See if anything contradictory contained in val, if so
      // conservatively mark this congruence set as containing
      // a contradiction
      markContradiction(canonicalVal);
    }
    if (val.isCV()) {
      ComputedValue<RecCV> val2 = val.cv();
      if (val2.op().isRetrieve() && canonicalVal.isConstant()) {
        // If we found out the value of a future, add to queue for
        // later processing
        Var future = val2.getInput(0).arg().getVar();
        futureValQueue.add(future);
      }
    }
  }


  /**
   * Replace oldCanonical with newCanonical as canonical member of a set
   * @param oldCanon
   * @param newCanon
   */
  public void changeCanonical(GlobalConstants consts, Arg oldCanon,
                                                      Arg newCanon) {
    // Do the initial merge
    changeCanonicalOnce(consts, oldCanon, newCanon);
    
    // The common case is that we do the one merge and we're done.
    // However, it's possible that each merge can trigger more merges.
    processQueues(consts);
  }

  private void processQueues(GlobalConstants consts) {
    Arg oldCanon;
    Arg newCanon;
    /*
     * Merge in FIFO order
     * Use a work queue here to iteratively process them (recursive
     * function calls would probably be a bad idea since there can be
     * long chains of merges).
     */
    do {
      while (!mergeQueue.isEmpty()) {
        ToMerge merge = mergeQueue.removeFirst();
        // recanonicalize in case of changes: may be redundant work
        oldCanon = findCanonical(merge.oldSet);
        newCanon = findCanonical(merge.newSet);
        if (!oldCanon.equals(newCanon)) {
          changeCanonicalOnce(consts, oldCanon, newCanon);
        }
      }
      while (!futureValQueue.isEmpty()) {
        Var futureWithVal = futureValQueue.removeFirst();
        updateCanonicalComponents(consts, futureWithVal.asArg(), null);
      }
      // Outer loop in case processing one queue results in additions
      // to another
    } while (!mergeQueue.isEmpty() || !futureValQueue.isEmpty());
  }
  
  /**
   * Merge together two sets.
   * 
   * This updates mergeQueue and futureValQueue, so caller needs to
   * process those.
   * @param oldCanon
   * @param newCanon
   */
  private void changeCanonicalOnce(GlobalConstants consts,
                                    Arg oldCanon, Arg newCanon) {
    logger.trace("Merging " + oldCanon + " into " + newCanon);
    assert(!oldCanon.equals(newCanon));
    // Check that types are compatible in sets being merged
    assert(oldCanon.type().getImplType().equals(
           newCanon.type().getImplType())) : "Types don't match: " +
           oldCanon + ":" + oldCanon.type() +
            newCanon + " " + newCanon.type();  
    
    // Handle situation where oldCanonical is part of another RecCV 
   updateCanonicalComponents(consts, oldCanon, newCanon);
    
    // Find all the references to old and add new entry pointing to new
    CongruentSets curr = this;
    do {
      for (RecCV val: curr.canonicalInv.get(oldCanon)) {
        Arg canonicalCheck = findCanonical(val);
        // Confirm that oldCanonical was actually the canonical one
        // This should only be necessary on recursive calls when
        // updating components
        if (canonicalCheck.equals(oldCanon)) {
          // val wasn't canonical before, so just need to redirect
          // it to new canonical
          setCanonicalEntry(val, newCanon);
        }
      }
      curr = curr.parent;
    } while (curr != null);
    // If either set being merged has contradictions, mark both as
    // contradictions
    if (contradictions.contains(oldCanon)) {
      markContradiction(newCanon);
    } else if (contradictions.contains(newCanon)) {
      markContradiction(oldCanon);
    }
    
    // Clear out-of-date entries in current scope
    // (leave outer scopes untouched since they're not harmful)
    this.canonicalInv.remove(oldCanon);
    
    this.mergedInto.put(newCanon, oldCanon);
    
    logger.trace("Done merging " + oldCanon + " into " + newCanon);
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
   * This will add to the merge queue, so callers need to process it
   * before returning to other modules
   * 
   * @param oldComponent
   * @param newComponent if null, just recanonicalize
   */
  private void updateCanonicalComponents(GlobalConstants consts,
                              Arg oldComponent, Arg newComponent) { 
    CongruentSets curr = this;
    do {
      for (RecCV outerCV: curr.componentIndex.get(oldComponent)) {
        assert(outerCV.isCV());
        RecCV newOuterCV = outerCV;
        if (newComponent != null) {
          // First substitute the inputs
          List<RecCV> newInputs = replaceInput(
              outerCV.cv().getInputs(), oldComponent, newComponent);
          newOuterCV = new RecCV(outerCV.cv().substituteInputs(newInputs));
        }
        // Then do other canonicalization
        newOuterCV = canonicalize(consts, newOuterCV);
        if (newOuterCV != outerCV) {
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
              mergeQueue.add(new ToMerge(canonical, newCanonical));
              if (logger.isTraceEnabled()) {
                if (newComponent != null) {
                  logger.trace("Merging " + oldComponent + " into " +
                    newComponent + " causing merging of " + canonical +
                    " into " + newCanonical);
                } else {
                  logger.trace("Getting value of " + oldComponent +
                                " causing merging of " + canonical +
                                " into " + newCanonical);
                }
              }
            } else {
              // Add to same set
              addToSet(consts, newOuterCV, canonical);
            }
            
            if (contradictions.contains(newComponent) &&
                !contradictions.contains(oldComponent)) {
              // Propagate contradiction to set
              markContradiction(canonical);
            }
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
    // First replace the args with whatever current canonical values,
    // then perform additional canonicalization
    return canonicalize(consts, canonicalizeInputs(origVal));
  }
    

  private RecCV canonicalize(GlobalConstants consts, RecCV result) {
    if (result.isCV()) {
      // Then do additional transformations such as constant folding
      ComputedValue<RecCV> resultValue = result.cv();
      if (resultValue.isCopy() || resultValue.isAlias()) {
        // Strip out copy/alias operations, since we can use value directly
        result = result.cv().getInput(0);  
      } else if (constFoldEnabled &&
                 this.congType == CongruenceType.VALUE) {
        RecCV constantFolded = tryConstantFold(resultValue);
        if (constantFolded != null) {
          result = constantFolded;
        }
      }  
    }

    // Replace a constant future with a global constant
    // This has effect of creating global constants for any used values
    if (constShareEnabled && result.isCV()) {
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
    assert(constFoldEnabled);
    assert(this.congType == CongruenceType.VALUE);
    return ConstantFolder.constantFold(logger, this, val);
  }

  private RecCV tryReplaceGlobalConstant(GlobalConstants consts, RecCV result) {
    assert(constShareEnabled);
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