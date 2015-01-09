package exm.stc.ic.opt.valuenumber;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.Semantics;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.InitVariables.InitState;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgOrCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.CongruenceType;
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

  /**
   * Data about defined foreign functions
   */
  private final ForeignFunctions foreignFuncs;

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
   */
  private final Map<ArgOrCV, Arg> canonical;

  /**
   * Store reverse direction links from canonical. Must manually traverse
   * parents.  Note that links aren't deleted, so the presence of a link
   * from X -> Y in here doesn't imply that X is still canonical.
   */
  private final MultiMap<Arg, ArgOrCV> canonicalInv;

  /**
   * Record congruence between values without canonical location
   */
  private final MultiMap<ArgCV, ArgCV> equivalences;

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
  private final Map<Arg, Set<ArgCV>> componentIndex;

  /**
   * Whether unpassable variables are inherited from parent.
   */
  private final boolean varsFromParent;

  /**
   * Unpassable variables defined in this scope
   */
  private final Set<Var> unpassableDeclarations;

  /**
   * Queue of merges that need to be completed before returning
   */
  private final LinkedList<ToMerge> mergeQueue;

  /**
   * List of args that may allow recanonicalization of CVs with
   * them inside them.
   * E.g. a future that now has a constant value.
   */
  private final LinkedList<Arg> recanonicalizeQueue;

  private final boolean constShareEnabled;
  private final boolean constFoldEnabled;

  /**
   * Record the equivalence type being represented
   */
  public final CongruenceType congType;

  private CongruentSets(ForeignFunctions foreignFuncs,
          CongruenceType congType, CongruentSets parent,
          boolean varsFromParent) {
    this.foreignFuncs = foreignFuncs;
    this.congType = congType;
    this.parent = parent;
    this.canonical = new HashMap<ArgOrCV, Arg>();
    this.canonicalInv = new MultiMap<Arg, ArgOrCV>();
    this.equivalences = new MultiMap<ArgCV, ArgCV>();
    this.mergedInto = new MultiMap<Arg, Arg>();
    this.componentIndex = new HashMap<Arg, Set<ArgCV>>();
    this.varsFromParent = varsFromParent;
    this.unpassableDeclarations = new HashSet<Var>();
    this.mergeQueue = new LinkedList<ToMerge>();
    this.recanonicalizeQueue = new LinkedList<Arg>();

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
  public void printTraceInfo(Logger logger, GlobalConstants consts) {
    if (logger.isTraceEnabled()) {
      // Print congruence sets nicely
      MultiMap<Arg, ArgOrCV> sets = activeSets(consts);
      for (Entry<Arg, List<ArgOrCV>> e: sets.entrySet()) {
        assert(e.getValue().size() > 0);
        boolean printSet = true;
        if (e.getValue().size() == 1) {
          // Should be self-reference, don't print
          if (e.getValue().get(0).arg().equals(e.getKey())) {
            printSet = false;
          } else {
            // TODO: this happens occasionally, e..g test 385
            logger.debug("INTERNAL ERROR: Bad set: " +
                         e.getKey() + " " + e.getValue());
          }
        }

        if (printSet) {
          logger.trace(congType + " cong. class " + e.getKey() +
                       " => " + e.getValue());
        }
      }
      // print componentIndex and inaccessible directly
      int height = 0;
      CongruentSets curr = this;
      do {
        if (!curr.componentIndex.isEmpty()) {
          logger.trace("Components#" + height + ": " + curr.componentIndex);
        }
        if (!varsFromParent) {
          logger.trace("Vars not inherited from parent");
        }
        height++;
        curr = curr.parent;
      } while (curr != null);
    }
  }

  public void validate(GlobalConstants consts) {
    // Building active sets performs some validation
    activeSets(consts);

    // Should not have any outstanding work to complete
    assert(mergeQueue.isEmpty()) : mergeQueue;
    assert(recanonicalizeQueue.isEmpty()) : recanonicalizeQueue;
  }

  /**
   * Reconstruct current sets and check consistency between
   * canonical and canonicalInvs.
   * Note that this also checks internal consistency between
   * canonical and canonicalInv
   * @return
   */
  private MultiMap<Arg, ArgOrCV> activeSets(GlobalConstants consts) {
    HashMap<ArgOrCV, Arg> effective = new HashMap<ArgOrCV, Arg>();
    MultiMap<Arg, ArgOrCV> res = new MultiMap<Arg, ArgOrCV>();
    CongruentSets curr = this;
    do {
      for (Entry<ArgOrCV, Arg> e: curr.canonical.entrySet()) {
        if (effective.containsKey(e.getKey())) {
          // Seen in an inner scope, not active
          continue;
        } else {
          ArgOrCV val = e.getKey();
          ArgOrCV canonVal = null;
          if (val.isCV()) {
            // Check to see if canonicalized value is present
            canonVal = canonicalize(consts, val.cv());
            if (effective.containsKey(canonVal)) {
              continue;
            }
          }

          // In effect
          Arg canon = e.getValue();
          while (true) {
            Arg canonAgain = effective.get(new ArgOrCV(canon));
            if (canonAgain == null || canonAgain.equals(canon)) {
              break;
            } else {
              canon = canonAgain;
            }
          }
          res.put(canon, val);
          effective.put(val, canon);
          if (canonVal != null) {
            // Include both canonicalized and uncanonicalized
            res.put(canon, canonVal);
            effective.put(canonVal, canon);
          }
        }
      }
      curr = curr.parent;
    } while (curr != null);

    try {
      checkCanonicalInv(effective, res);
    } catch (RuntimeException e) {
      // Dump data structures
      dumpDataStructures();
      logger.trace("effective:");
      logger.trace(effective);
      logger.trace("effectiveInv:");
      logger.trace(res);
      throw e;
    }

    return res;
  }

  /**
   * Do raw dump of internal data structures for debugging
   */
  private void dumpDataStructures() {
    logger.trace("Dumping CongruentSets data structures");
    CongruentSets curr = this;
    int ancestor = 0;
    do {
      logger.trace("=======================");
      if (ancestor != 0) {
        logger.trace("ancestor " + ancestor);
      }
      logger.trace("congType:");
      logger.trace(curr.congType);
      logger.trace("canonical:");
      logger.trace(curr.canonical);
      logger.trace("canonicalInv:");
      logger.trace(curr.canonicalInv);
      logger.trace("mergedInto:");
      logger.trace(curr.mergedInto);
      logger.trace("componentIndex:");
      logger.trace(curr.componentIndex);
      logger.trace("varsFromParent:");
      logger.trace(curr.varsFromParent);
      logger.trace("mergeQueue:");
      logger.trace(curr.mergeQueue);
      logger.trace("recanonicalizeQueue:");
      logger.trace(curr.recanonicalizeQueue);
      ancestor++;
      curr = curr.parent;
    } while (curr != null);
  }

  public static CongruentSets makeRoot(ForeignFunctions foreignFuncs,
                                       CongruenceType congType) {
    return new CongruentSets(foreignFuncs, congType, null, true);
  }

  public CongruentSets makeChild(boolean varsFromParent) {
    return new CongruentSets(foreignFuncs, congType, this, varsFromParent);
  }

  private boolean isUnpassable(Arg arg) {
    return arg.isVar() && !Semantics.canPassToChildTask(arg.getVar());
  }

  /**
   * Add informaation that variable was declared in this scope
   * @param vars
   */
  public void varDeclarations(Collection<Var> vars) {
    for (Var var: vars) {
      if (!Semantics.canPassToChildTask(var)) {
        unpassableDeclarations.add(var);
      }
    }
  }

  /**
   * Find canonical representative of congruence set.
   * @param arg
   * @return not null
   */
  public Arg findCanonical(Arg arg) {
    assert(arg != null);
    // Don't need to canonicalize arg
    Arg result = findCanonicalInternal(new ArgOrCV(arg));
    assert(result != null) : "Could not find canonical for " + arg;
    return result;
  }


  /**
   * Find canonical for ArgCV, canonicalizing val firstt
   * @param constants
   * @param val
   * @return
   */
  public Arg findCanonical(GlobalConstants constants, ArgCV val) {
    return findCanonicalInternal(canonicalize(constants, val));
  }

  /**
   * Find canonical using an ArgCV that's already been canonicalized
   * @param val
   * @return
   */
  Arg findCanonicalInternal(ArgCV val) {
    return findCanonicalInternal(new ArgOrCV(val));
  }

  /**
   * Lookup canonical value of val
   * If val.isArg() and there is not a canonical location, this
   * will update structures to reflect that it's a single-element set.
   *
   * @param val arg or CV with canonicalized inputs
   * @return canonical representative of congruence set, maybe null
   */
  Arg findCanonicalInternal(ArgOrCV val) {
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
      if (val.isArg() && canon.equals(val.arg())) {
        return canon;
      } else {
        /* Re-canonicalize.  This is to handle the situation where a new CV
         * is added in a parent without the canonicalization in a child. */
        return findCanonicalInternal(new ArgOrCV(canon));
      }
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

  /**
   * Check if we can actually access a var here
   * @param var
   * @return
   */
  public boolean isAccessible(Var var) {
    Arg varArg = var.asArg();
    if (!isUnpassable(varArg)) {
      return true;
    }
    CongruentSets curr = this;
    boolean allPassed = true;
    while (curr != null) {
      if (curr.canonicalInv.containsKey(varArg)) {
        return allPassed;
      }
      allPassed = allPassed && curr.varsFromParent;
      curr = curr.parent;
    }
    throw new STCRuntimeError("Not found: " + var);
  }

  /**
   * Find value of a future
   * @param var
   * @return
   */
  public Arg findRetrieveResult(Arg var, boolean recursive) {
    assert(var.isVar());
    Arg canonVar = findCanonical(var);
    assert(canonVar.isVar());
    ArgCV cv = ComputedValue.retrieveCompVal(canonVar.getVar(), recursive);
    return findCanonicalInternal(cv);
  }

  public Map<Var, Arg> getReplacementMap(InitState init) {
    return new ReplacementMap(init);
  }
  /**
   * Return full list of values congruent to arg
   * @param arg
   * @return
   */
  public List<ArgOrCV> findCongruentValues(Arg arg) {
    CongruentSets curr = this;
    Arg canonical = findCanonical(arg);

    List<ArgOrCV> res = null;

    if (!arg.equals(canonical)) {
      // Canonical is different to arg
      res = new ArrayList<ArgOrCV>();
      res.add(new ArgOrCV(canonical));
    }

    do {
      for (ArgOrCV cv: curr.canonicalInv.get(canonical)) {
        if (res == null) {
          res = new ArrayList<ArgOrCV>();
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
  public void addToSet(GlobalConstants consts, ArgOrCV val, Arg canonicalVal) {
    assert(val != null);
    assert(canonicalVal != null);
    addSetEntry(val, canonicalVal);
    processQueues(consts);
  }

  private void addSetEntry(ArgOrCV val, Arg canonicalVal) {
    logger.trace("Add " + val + " to " + canonicalVal);
    boolean newEntry = setCanonicalEntry(val, canonicalVal);
    if (!newEntry) {
      return;
    }

    if (val.isCV()) {
      checkForRecanonicalization(canonicalVal, val.cv());
    }

    // Also add any equivalent values
    for (ArgCV equiv: lookupEquivalences(val)) {
      // Note: should avoid infinite recursion because of newEntry check above
      if (logger.isTraceEnabled()) {
        logger.trace("Add set entry from equiv: "
                     + equiv + " in " + canonicalVal);
      }
      addSetEntry(new ArgOrCV(equiv), canonicalVal);
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
    if (logger.isTraceEnabled() && !mergeQueue.isEmpty() &&
        !recanonicalizeQueue.isEmpty()) {
      logger.trace("Processing queues...");
      logger.trace("mergeQueue: " + mergeQueue);
      logger.trace("recanonicalizeQueue: " + recanonicalizeQueue);
    }
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
      while (!recanonicalizeQueue.isEmpty()) {
        Arg component = recanonicalizeQueue.removeFirst();
        updateCanonicalComponents(consts, component, null);
      }
      // Outer loop in case processing one queue results in additions
      // to another
    } while (!mergeQueue.isEmpty() || !recanonicalizeQueue.isEmpty());
  }

  /**
   * Merge together two sets.
   *
   * This updates mergeQueue and recanonicalizeQueue, so caller needs to
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
           oldCanon + ":" + oldCanon.type() + " " +
            newCanon + " " + newCanon.type();

    // Handle situation where oldCanonical is part of another ArgOrCV
   updateCanonicalComponents(consts, oldCanon, newCanon);

    // Find all the references to old and add new entry pointing to new
    CongruentSets curr = this;
    do {
      for (ArgOrCV val: curr.canonicalInv.get(oldCanon)) {
        Arg canonicalCheck = findCanonicalInternal(val);
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

    // Clear out-of-date entries in current scope
    // (leave outer scopes untouched since they're not harmful)
    this.canonicalInv.remove(oldCanon);

    this.mergedInto.put(newCanon, oldCanon);

    logger.trace("Done merging " + oldCanon + " into " + newCanon);
  }

  public Iterable<ArgOrCV> availableThisScope() {
    // TODO: will want to translate args back to canonical for parent scope
    List<ArgOrCV> avail = new ArrayList<ArgOrCV>();
    for (List<ArgOrCV> vals: canonicalInv.values()) {
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
    assert(newComponent == null || !oldComponent.equals(newComponent)) :
           oldComponent + " " + newComponent;
    CongruentSets curr = this;
    do {
      if (logger.isTraceEnabled()) {
        logger.trace("Iterating over components of: " + oldComponent + ": " +
                    curr.lookupComponentIndex(oldComponent));
      }
      for (ArgCV outerCV: curr.lookupComponentIndex(oldComponent)) {
        ArgCV newOuterCV1 = outerCV;
        if (newComponent != null &&
            outerCV.canSubstituteInputs(congType)) {
          // First substitute the inputs
          List<Arg> newInputs = replaceInput(
              outerCV.getInputs(), oldComponent, newComponent);
          newOuterCV1 = outerCV.substituteInputs(newInputs);
        }
        // Then do other canonicalization
        ArgOrCV newOuterCV2 = canonicalizeInternal(consts, newOuterCV1, false);
        if (newOuterCV2.isArg() || !newOuterCV2.cv().equals(outerCV)) {
          if (logger.isTraceEnabled()) {
            logger.trace("Sub " + oldComponent + " for "
                        + newComponent + " in " + outerCV);
          }
          if (newComponent != null && newOuterCV2.isCV()) {
            addInputIndex(newComponent, newOuterCV2.cv());
          }
          Arg canonical = findCanonicalInternal(outerCV);
          if (canonical != null) {
            addUpdatedCV(oldComponent, newComponent, newOuterCV2, canonical);
          } else {
            if (logger.isTraceEnabled()) {
              logger.trace("Could not update " + outerCV);
            }
          }
          updateEquivCanonicalization(outerCV, newOuterCV2);
        }
      }
      curr = curr.parent;
    } while (curr != null);
  }

  /**
   * Finish adding an updated CV obtained by substituting components
   * @param oldComponent original component
   * @param newComponent replaced with
   * @param newCV canonicalized computed value
   * @param canonical existing set that it is a member of
   */
  private void addUpdatedCV(Arg oldComponent, Arg newComponent, ArgOrCV newCV,
                            Arg canonical) {
    // Add new CV, handling special cases where CV bridges two sets
    Arg newCanonical = findCanonicalInternal(newCV);
    if (newCanonical == null || newCanonical.equals(canonical)) {
      // Add to same set
      addSetEntry(newCV, canonical);
    } else {
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
    }
  }

  private List<Arg> replaceInput(List<Arg> oldInputs,
      Arg inputToReplace, Arg newInput) {
    List<Arg> newInputs = new ArrayList<Arg>(oldInputs.size());
    for (Arg oldInput: oldInputs) {
      if (oldInput.equals(inputToReplace)) {
        newInputs.add(newInput);
      } else {
        newInputs.add(oldInput);
      }
    }
    return newInputs;
  }

  /**
   *
   * @param val
   * @param canonicalVal
   * @return true if new entry in this scope
   */
  private boolean setCanonicalEntry(ArgOrCV val, Arg canonicalVal) {
    /*if (canonicalVal.isVar() &&
        canonicalVal.getVar().name().equals("__v:1") &&
        val.isArg() && val.arg().isVar() &&
        val.arg().getVar().name().equals("__v:7")) {
      logger.warn("HERE: " + val + " = " + canonicalVal, new Exception());
    }*/

    Arg prev = canonical.put(val, canonicalVal);
    canonicalInv.put(canonicalVal, val);
    return prev == null;
  }

  /**
   * Keep index of where Arg appears.  When merge
   * happens, need to go through and replace old Arg with new
   * canonical Arg.  We also use this to propagate contradiction
   * info among different sets.  We need to track constants as these
   * are often the canonical member of a set.
   */
  private void addInputIndex(Arg input, ArgCV cv) {
    if (logger.isTraceEnabled()) {
      logger.trace("Add component: " + input + "=>" + cv);
    }
    Set<ArgCV> set = componentIndex.get(input);
    if (set == null) {
      set = new HashSet<ArgCV>();
      componentIndex.put(input, set);
    }
    set.add(cv);
  }

  private Set<ArgCV> lookupComponentIndex(Arg oldComponent) {
    Set<ArgCV> res = this.componentIndex.get(oldComponent);
    if (res != null) {
      return res;
    } else {
      return Collections.emptySet();
    }
  }

  private void checkCanonicalInv(HashMap<ArgOrCV, Arg> inEffect,
      MultiMap<Arg, ArgOrCV> inEffectInv) {
    CongruentSets curr;
    // Verify all entries in CanonicalInv for active keys are correct
    curr = this;
    do {
      for (Entry<Arg, List<ArgOrCV>> e: curr.canonicalInv.entrySet()) {
        Arg key1 = e.getKey();
        if (inEffectInv.containsKey(key1)) {
          // Canonical is currently in effect
          // for (ArgOrCV val: e.getValue()) {

            /*TODO: disabled checks since in various corner cases we can
             * have things in different sets without producing incorrect
             * optimization results
             * Arg key2 = inEffect.get(val);
             * assert(key2 != null) : "No canonical entry for " + val +
                                   " in set " + key1;
             * assert(key2.equals(key1)): val + " not in consistent set:"
             *                                + key1 + " vs. " + key2;
             */

          // }
        } else {
          // Check it was swallowed up into another set
          /* TODO: disabled this check too
          Arg newKey = inEffect.get(new ArgOrCV(key1));
          assert(newKey != null && !newKey.equals(key1)) :
            " Expected " + key1 + " to be part of another set, but " +
            " was part of " + newKey;
           */
        }
      }
      curr = curr.parent;
    } while (curr != null);
  }


  /**
   * Do any canonicalization of result value here, e.g. to implement
   * constant folding, etc.
   * @param consts
   * @param origVal
   * @return
   */
  public ArgOrCV canonicalize(GlobalConstants consts, ArgCV origVal) {
    return canonicalizeInternal(consts, origVal, true);
  }

  private ArgOrCV canonicalizeInternal(GlobalConstants consts, ArgCV origVal,
                                       boolean addComponentIndex) {
    // First replace the args with whatever current canonical values
    ArgCV replacedInputs = canonicalizeInputs(origVal, addComponentIndex);
    // Then perform additional canonicalization
    return canonicalizeInternal(consts, replacedInputs);
  }


  private ArgOrCV canonicalizeInternal(GlobalConstants consts, ArgCV val) {
    ArgOrCV result = null;
    // Then do additional transformations such as constant folding
    if (val.isCopy() || val.isAlias()) {
      // Strip out copy/alias operations, since we can use value directly
      return new ArgOrCV(val.getInput(0));
    }

    // Try to resolve dereferenced references
    if (val.isRetrieve(false)) {
      ArgCV resolved = tryResolve(val);
      if (resolved != null) {
        val = resolved;
      }
    }

    if (constFoldEnabled &&
               this.congType == CongruenceType.VALUE) {
      ArgOrCV constantFolded = tryConstantFold(val);
      if (constantFolded != null) {
        result = constantFolded;
      }
    }

    if (result == null) {
      result = new ArgOrCV(val);
    }

    // Replace a constant future with a global constant
    // This has effect of creating global constants for any used values
    if (constShareEnabled && result.isCV() && consts != null) {
      result = tryReplaceGlobalConstant(consts, result);
    }
    return result;
  }

  /**
    * Convert ComputedValue parameterized with <Arg> to
    *       ComputedValue parameterized with ComputedValue
   * @param cv
   * @param addComponentIndex whether to add to component index
   * @return
   */
  private ArgCV canonicalizeInputs(ArgCV cv, boolean addComponentIndex) {
    List<Arg> inputs = cv.getInputs();
    List<Arg> newInputs = new ArrayList<Arg>(inputs.size());
    for (Arg input: inputs) {
      // Canonicalize inputs to get this into a canonical form

      if (cv.canSubstituteInputs(congType)) {
        Arg canonicalInput = findCanonical(input);
        assert(canonicalInput != null);
        // Add canonical
        newInputs.add(canonicalInput);
      } else {
        // Not allowed to substitute, e.g. for filenames where we don't
        // have reference transparency for args
        newInputs.add(input);
      }
    }

    ArgCV newCV = new ArgCV(cv.op, cv.subop, newInputs);
    if (addComponentIndex && findCanonicalInternal(newCV) == null) {
      // Add to index if not present
      for (Arg newInput: newInputs) {
        addInputIndex(newInput, newCV);
      }
    }
    return newCV;
  }

  /**
   * Try to constant-fold the expression
   * @param val
   * @return
   */
  private ArgOrCV tryConstantFold(ArgCV val) {
    assert(constFoldEnabled);
    assert(this.congType == CongruenceType.VALUE);
    return ConstantFolder.constantFold(logger, foreignFuncs, this, val);
  }

  /**
   * Try to replace a future with a constant value with a shared
   * global constant
   * @param consts
   * @param result
   * @return
   */
  private ArgOrCV tryReplaceGlobalConstant(GlobalConstants consts, ArgOrCV result) {
    assert(constShareEnabled);
    ComputedValue<Arg> val = result.cv();
    if (val.op().isAssign()) {
      Arg assignedVal = val.getInput(0);
      if (assignedVal.isConst()) {
        Var globalConst = consts.getOrCreateByVal(assignedVal);
        result = new ArgOrCV(globalConst.asArg());
      }
    }
    return result;
  }

  /**
   * Try to resolve a lookup to the original thing
   * dereferenced
   * @param val
   * @return
   */
  private ArgCV tryResolve(ComputedValue<Arg> val) {
    assert(val.isRetrieve(false));
    Arg src = val.getInput(0);
    for (ArgOrCV v: findCongruentValues(src)) {
      if (v.isCV()) {
        ArgCV v2 = v.cv();
        if (v2.isArrayMember()) {
          return ComputedValue.derefArrayMember(v2);
        }
      }
    }
    return null;
  }

  /**
   * Check if a change to a value might require something else to be
   * recanonicalized.
   * @param canonicalVal
   * @param val
   */
  private void checkForRecanonicalization(Arg canonicalVal,
      ComputedValue<Arg> val) {
    if (val.op().isRetrieve() && canonicalVal.isConst()) {
      // If we found out the value of a future, add to queue for
      // later processing
      Arg future = val.getInput(0);
      assert(future.isVar());
      if (logger.isTraceEnabled()) {
        logger.trace("Enqueue future with val " + future);
      }
      recanonicalizeQueue.add(future);
    } else if (val.isArrayMember()) {
      // Might be able to dereference
      Arg arrayMemberRef = val.getInput(0);
      if (logger.isTraceEnabled()) {
        logger.trace("Enqueue array member ref " + arrayMemberRef);
      }
      recanonicalizeQueue.add(arrayMemberRef);
    } else if (val.isFilenameValCV()) {
      // Might be filename somewhere
      Arg file = val.getInput(0);
      if (logger.isTraceEnabled()) {
        logger.trace("Enqueue file for filename val " + file);
      }
      recanonicalizeQueue.add(file);
    }
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

    StackLite<Pair<CongruentSets, Arg>> work =
          new StackLite<Pair<CongruentSets, Arg>>();
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
   * Add equivalence for the case where we don't have a location
   * for either.  Should only be called if findCanonical() was null
   * for both values
   * @param val1
   * @param val2
   */
  public void addEquivalence(GlobalConstants consts, ArgCV val1, ArgCV val2) {
    ArgOrCV canon1 = canonicalizeInternal(consts, val1);
    ArgOrCV canon2 = canonicalizeInternal(consts, val2);
    // Shouldn't have canonical arg value if calling this function
    assert(canon1.isCV());
    assert(canon2.isCV());

    addEquivalenceEntry(canon1.cv(), canon2.cv());
    // TODO: when new CV added to canonical, check equivalence map
  }

  /**
   * Update equivalences when recanonicalization occurs
   * @param oldCV
   * @param newCV
   */
  private void updateEquivCanonicalization(ArgCV oldCV, ArgOrCV newCV) {
    if (newCV.isCV()) {
      for (ArgCV equiv: lookupEquivalences(oldCV)) {
        addEquivalenceEntry(equiv, newCV.cv());
      }
    }
  }

  private void addEquivalenceEntry(ArgCV val1, ArgCV val2) {
    // add to equivalence map in both directions
    equivalences.put(val1, val2);
    equivalences.put(val2, val1);
  }

  private List<ArgCV> lookupEquivalences(ArgOrCV val) {
    if (val.isCV()) {
      return lookupEquivalences(val.cv());
    } else {
      return Collections.emptyList();
    }
  }
  /**
   * Lookup equivalent value here and in parents
   * @param val
   * @return
   */
  private List<ArgCV> lookupEquivalences(ArgCV val) {
    CongruentSets curr = this;
    List<ArgCV> res = new ArrayList<ArgCV>();
    while (curr != null) {
      res.addAll(curr.equivalences.get(val));
      curr = curr.parent;
    }
    return res;
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

      if (v.isMapped() != Ternary.FALSE && congType == CongruenceType.VALUE) {
        // Don't replace mapped variables: referential transparency doesn't
        // apply
        return null;
      }

      ArgOrCV cv = new ArgOrCV(v.asArg());
      Arg replace = null;
      CongruentSets curr = CongruentSets.this;
      List<CongruentSets> visited = new ArrayList<CongruentSets>();
      int i = 0;
      do {
        if (logger.isTraceEnabled()) {
          logger.trace("ReplacementMap<" + congType + ">.get(" + v +
                        ") search up level " + i);
        }
        replace = curr.canonical.get(cv);
        if (replace != null) {
          break;
        }
        visited.add(curr);
        curr = curr.parent;
        i++;
      } while (curr != null);


      if (replace != null && !replacementIsAccessible(replace)) {
        if (logger.isTraceEnabled()) {
          logger.trace(v + " => !!" + replace + "(" + congType + ")"
                + ": INACCESSIBLE");
        }
        // TODO: find alternative?
        return null;
      }

      if (replace != null && !isInit(replace)) {
        replace = findAltReplacement(v, replace);
      }

      if (logger.isTraceEnabled()) {
        logger.trace("ReplacementMap<" + congType + ">.get(" + v + ") = " +
                      replace);

      }
      return replace;
    }

    private boolean replacementIsAccessible(Arg replace) {
      assert(replace != null);
      CongruentSets curr;
      if (isUnpassable(replace)) {
        assert(replace.isVar());

        // Search backwards for declaration
        curr = CongruentSets.this;
        do {
          if (curr.unpassableDeclarations.contains(replace.getVar())) {
            return true;
          }

          if (!curr.varsFromParent) {
            return false;
          }
          curr = curr.parent;
        } while (curr != null);

        Logging.getSTCLogger().debug(
            "Could not find declaration for " + replace);
        return false;
      } else {
        return true;
      }
    }

    /**
     * Find alternative replacement from set given that
     * replace isn't initialized
     * @param orig
     * @param replace
     * @return
     */
    private Arg findAltReplacement(Var orig, Arg replace) {
      // Check alternative canonical vals using DFS
      StackLite<Arg> replacementStack = new StackLite<Arg>();
      do {
        logger.trace(orig + " => " + replace + "(" + congType + ")" +
                         ": NOT INITIALIZED");
        List<Arg> alts = mergedInto.get(replace);
        if (alts.isEmpty()) {
          // Backtrack
          if (replacementStack.isEmpty()) {
            return null;
          }
          replace = replacementStack.pop();
        } else {
          replace = alts.get(0);
          replacementStack.addAll(alts.subList(1, alts.size()));
        }
      } while (!isInit(replace));
      return replace;
    }

    private boolean isInit(Arg replace) {
      if (replace.isConst()) {
        return true;
      }
      boolean output = (congType == CongruenceType.ALIAS);
      return initVars.isInitialized(replace.getVar(), output);
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