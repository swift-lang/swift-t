package exm.stc.ic.opt.valuenumber;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;

import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.Semantics;
import exm.stc.common.lang.Var;
import exm.stc.common.util.Pair;
import exm.stc.common.util.ScopedUnionFind;
import exm.stc.common.util.ScopedUnionFind.UnionFindSubscriber;
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
   * A "union-find" data structure to  represent congruent sets of computed
   * values. This links every member of the set to the canonical member of
   * the set.  It is incrementally updated so that, for every member X of
   * the set including the canonical member Y, X -> Y exists.
   * Representative of set should always be an arg (variable or constant
   *  if appropriate)
   *  A variable starts off as part of one-element set (itself)
   */
  private final ScopedUnionFind<ArgOrCV> canonical;

  /**
   * Track sets that were merged into this one.  Allows
   * finding an alternate value if current is out of scope.
   */
  private final ListMultimap<ArgOrCV, ArgOrCV> mergedInto;

  /**
   * Track places where CV1 appears inside CV2, so that we can
   * go through and recanonicalize them if needed.
   * Must manually traverse parents to find all.
   */
  private final SetMultimap<Arg, ArgCV> componentIndex;

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
    if (parent == null) {
      this.canonical = ScopedUnionFind.createRoot();
    } else {
      this.canonical = parent.canonical.newScope();
    }
    this.mergedInto = ArrayListMultimap.create();
    this.componentIndex = HashMultimap.create();
    this.varsFromParent = varsFromParent;
    this.unpassableDeclarations = new HashSet<Var>();
    this.mergeQueue = new LinkedList<ToMerge>();
    this.recanonicalizeQueue = new LinkedList<Arg>();

    if (parent != null) {
      this.constShareEnabled = parent.constShareEnabled;
      this.constFoldEnabled = parent.constFoldEnabled;
    } else {
      this.constShareEnabled = Settings.getBooleanUnchecked(
                                        Settings.OPT_SHARED_CONSTANTS);
      this.constFoldEnabled = Settings.getBooleanUnchecked(
                                        Settings.OPT_CONSTANT_FOLD);
    }
  }

  /**
   * Dump data structures if tracing enabled
   */
  public void printTraceInfo(Logger logger, GlobalConstants consts) {
    if (logger.isTraceEnabled()) {
      // Print congruence sets nicely
      SetMultimap<ArgOrCV, ArgOrCV> sets = canonical.sets();
      for (Entry<ArgOrCV, Collection<ArgOrCV>> e: sets.asMap().entrySet()) {
        logger.trace(congType + " cong. class " + e.getKey() +
                       " => " + e.getValue());
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
    // TODO: check internal consistency

    // Should not have any outstanding work to complete
    assert(mergeQueue.isEmpty()) : mergeQueue;
    assert(recanonicalizeQueue.isEmpty()) : recanonicalizeQueue;
  }

  /**
   * Do raw dump of internal data structures for debugging
   */
  @SuppressWarnings("unused")
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
   * Add information that variable was declared in this scope
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
  public ArgOrCV findCanonical(Arg arg) {
    assert(arg != null);
    // Don't need to canonicalize arg
    ArgOrCV result = findCanonicalInternal(new ArgOrCV(arg));
    assert(result != null);
    return result;
  }


  /**
   * Find canonical for ArgCV, canonicalizing val firstt
   * @param constants
   * @param val
   * @return non-null value (may be val itself)
   */
  public ArgOrCV findCanonical(GlobalConstants constants, ArgCV val) {
    return findCanonicalInternal(canonicalize(constants, val));
  }

  /**
   * Find canonical using an ArgCV that's already been canonicalized
   * @param val
   * @return non-null value (may be val itself)
   */
  ArgOrCV findCanonicalInternal(ArgCV val) {
    return findCanonicalInternal(new ArgOrCV(val));
  }

  /**
   * Lookup canonical value of val
   * If val.isArg() and there is not a canonical location, this
   * will update structures to reflect that it's a single-element set.
   *
   * @param val arg or CV with canonicalized inputs
   * @return canonical representative of congruence set, maybe val itself
   */
  ArgOrCV findCanonicalInternal(ArgOrCV val) {
    return canonical.lookup(val);
  }

  /**
   * Check if we can actually access a var here
   * @param var
   * @return
   */
  public boolean isAccessible(Var var) {
    if (!isUnpassable(var.asArg())) {
      return true;
    }

    // Search backwards for declaration
    CongruentSets curr = this;
    boolean allPassed = true;
    while (curr != null) {
      if (curr.unpassableDeclarations.contains(var)) {
        return allPassed;
      }
      allPassed = allPassed && curr.varsFromParent;
      curr = curr.parent;
    }

    logger.debug("Could not find: " + var);

    return allPassed;
  }

  /**
   * Find value of a future
   * @param var
   * @return
   */
  public ArgOrCV findRetrieveResult(Arg var, boolean recursive) {
    assert(var.isVar());
    ArgOrCV canonVar = findCanonical(var);
    assert(canonVar.isArg() && canonVar.arg().isVar());
    ArgCV cv = ComputedValue.retrieveCompVal(canonVar.arg().getVar(), recursive);
    return findCanonicalInternal(cv);
  }

  public Map<Var, Arg> getReplacementMap(InitState init) {
    return new ReplacementMap(init);
  }

  public Set<ArgOrCV> findCongruentValues(Arg val) {
    return findCongruentValues(new ArgOrCV(val));
  }

  /**
   * Return full list of values congruent to arg
   * @param arg
   * @return
   */
  public Set<ArgOrCV> findCongruentValues(ArgOrCV val) {
    return this.canonical.members(findCanonicalInternal(val));
  }

  /**
   * Replace oldCanonical with newCanonical as canonical member of a set
   * @param oldCanon
   * @param newCanon
   */
  public void changeCanonical(GlobalConstants consts, ArgOrCV oldCanon,
                                                      ArgOrCV newCanon) {
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
    ArgOrCV oldCanon;
    ArgOrCV newCanon;
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
        oldCanon = findCanonicalInternal(merge.oldSet);
        newCanon = findCanonicalInternal(merge.newSet);
        if (!oldCanon.equals(newCanon)) {
          changeCanonicalOnce(consts, oldCanon, newCanon);
        }
      }
      while (!recanonicalizeQueue.isEmpty()) {
        Arg component = recanonicalizeQueue.removeFirst();

        updateCanonicalComponents(consts, component);
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
                                    ArgOrCV oldCanon, ArgOrCV newCanon) {
    logger.trace("Merging " + oldCanon + " into " + newCanon);
    assert(!oldCanon.equals(newCanon));
    // Check that types are compatible in sets being merged
    if (oldCanon.isArg() && newCanon.isArg()) {
      assert(oldCanon.arg().type().getImplType().equals(
             newCanon.arg().type().getImplType())) : "Types don't match: " +
             oldCanon + ":" + oldCanon.arg().type() + " " +
              newCanon + " " + newCanon.arg().type();
    }

    assert(newCanon.isArg() || !oldCanon.isArg()) :
      "Shouldn't replace arg with non-arg";

    /*
     * Handle situation where oldCanonical is part of another ArgOrCV.
     * This is ok to call here because we won't get into a recursive loop -
     * it only updates entries where the value is a CV, while this entry
     * is an arg.
     */
    if (oldCanon.isArg()) {
      assert(newCanon.isArg());
      recanonicalizeQueue.add(oldCanon.arg());
    }

    // Find all the references to old and add new entry pointing to new
    setCanonicalEntry(consts, oldCanon, newCanon);
    this.mergedInto.put(newCanon, oldCanon);

    logger.trace("Done merging " + oldCanon + " into " + newCanon);
  }

  private void addSetEntry(GlobalConstants consts, ArgOrCV val,
                            ArgOrCV canonicalVal) {
    logger.trace("Add " + val + " to " + canonicalVal);
    boolean newEntry = setCanonicalEntry(consts, val, canonicalVal);
    if (!newEntry) {
      return;
    }

    /*
     * The canonical entry may have changed - this can result in additional
     * updates.
     */
    if (val.isCV()) {
      checkForRecanonicalization(canonicalVal, val.cv());
    }
  }

  public Iterable<ArgOrCV> availableThisScope() {
    return canonical.keys();
  }

  public static class ToMerge {
    private ToMerge(ArgOrCV oldSet, ArgOrCV newSet) {
      this.oldSet = oldSet;
      this.newSet = newSet;
    }
    public final ArgOrCV oldSet;
    public final ArgOrCV newSet;
  }

  /**
   * Recanonicalize components.  If an old computed value had
   * a reference to oldCanonical in it, then it may be no longer
   * canonical, so need to replace.
   *
   * This will add to the merge queue, so callers need to process it
   * before returning to other modules
   *
   * @param oldComponent
   * @param newComponent if null, just recanonicalize
   */
  private void updateCanonicalComponents(GlobalConstants consts,
                            Arg oldComponent) {
    Arg newComponent = findCanonical(oldComponent).arg();
    CongruentSets curr = this;
    do {
      if (logger.isTraceEnabled()) {
        logger.trace("Iterating over components of: " + oldComponent + ": " +
                    curr.componentIndex.get(oldComponent));
      }
      for (ArgCV outerCV: curr.componentIndex.get(oldComponent)) {
        ArgCV newOuterCV1 = outerCV;
        if (outerCV.canSubstituteInputs(congType)) {
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
          ArgOrCV canonical = findCanonicalInternal(outerCV);
          if (canonical != null) {
            addUpdatedCV(consts, oldComponent, newComponent, newOuterCV2,
                         canonical);
          } else {
            if (logger.isTraceEnabled()) {
              logger.trace("Could not update " + outerCV);
            }
          }
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
  private void addUpdatedCV(GlobalConstants consts, Arg oldComponent,
      Arg newComponent, ArgOrCV newCV, ArgOrCV canonical) {
    // Add new CV, handling special cases where CV bridges two sets
    ArgOrCV newCanonical = findCanonicalInternal(newCV);
    if (newCanonical.equals(canonical)) {
      // Add newCV to same set
      addSetEntry(consts, newCV, canonical);
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
  private boolean setCanonicalEntry(final GlobalConstants consts,
                                    ArgOrCV val, ArgOrCV canonicalVal) {
    Set<ArgOrCV> affected = canonical.merge(canonicalVal, val);

    /*
     * Need to recanonicalize if it changes in parent.
     * This avoids infinite loops since notifications only go down the tree.
     */
    if (parent != null) {
      parent.canonical.subscribe(canonicalVal, false,
          new UnionFindSubscriber<ArgOrCV>() {

        @Override
        public void notifyMerge(ArgOrCV winner, ArgOrCV loser) {
          if (logger.isTraceEnabled()) {
            logger.trace("notifyMerge " + loser + " into " + winner);
          }
          changeCanonical(consts, loser, winner);
        }

      });
    }

    return affected.size() > 0;
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
    componentIndex.put(input, cv);
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
        ArgOrCV canonicalInput = findCanonical(input);
        assert(canonicalInput != null);
        assert(canonicalInput.isArg());
        // Add canonical
        newInputs.add(canonicalInput.arg());
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
  private void checkForRecanonicalization(ArgOrCV canonicalVal,
      ComputedValue<Arg> val) {
    if (val.op().isRetrieve() &&
        canonicalVal.isArg() && canonicalVal.isConstArg()) {
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
  public Iterable<ArgOrCV> allCongruent(ArgOrCV val) {
    return canonical.members(val);
  }

  public Iterable<ArgOrCV> mergedCanonicalsThisScope(ArgOrCV canonical) {
    return mergedCanonicals(canonical, true);
  }

  private Iterable<ArgOrCV> mergedCanonicals(ArgOrCV canonical,
                boolean followAncestors) {
    List<ArgOrCV> allMerged = new ArrayList<ArgOrCV>();

    StackLite<Pair<CongruentSets, ArgOrCV>> work =
          new StackLite<Pair<CongruentSets, ArgOrCV>>();
    work.push(Pair.create(this, canonical));

    while (!work.isEmpty()) {
      Pair<CongruentSets, ArgOrCV> x = work.pop();
      CongruentSets curr = x.val1;
      ArgOrCV set = x.val2;
      for (ArgOrCV mergedSet: curr.mergedInto.get(set)) {
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

      if (v.isMapped() != Ternary.FALSE && congType == CongruenceType.VALUE) {
        // Don't replace mapped variables: referential transparency doesn't
        // apply
        return null;
      }

      ArgOrCV cv = new ArgOrCV(v.asArg());
      ArgOrCV replace = canonical.lookup(cv);

      if (replace != null && replace.isArg()) {
        if (!replacementIsAccessible(replace.arg())) {
          if (logger.isTraceEnabled()) {
            logger.trace(v + " => !!" + replace + "(" + congType + ")"
                  + ": INACCESSIBLE");
          }
          // NOTE: could attempt to find accessible alternative in some scenarios
          // - this misses some opportunities
          return null;
        }

        if (!isInit(replace.arg())) {
          replace = findAltReplacement(v, replace);
        }
      }


      if (logger.isTraceEnabled()) {
        logger.trace("ReplacementMap<" + congType + ">.get(" + v + ") = " +
                      replace);

      }

      if (replace != null && replace.isArg()) {
        return replace.arg();
      }

      return null;
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
    private ArgOrCV findAltReplacement(Var orig, ArgOrCV replace) {
      // Check alternative canonical vals using DFS
      StackLite<ArgOrCV> replacementStack = new StackLite<ArgOrCV>();
      do {
        logger.trace(orig + " => " + replace + "(" + congType + ")" +
                         ": NOT INITIALIZED");
        List<ArgOrCV> alts = mergedInto.get(replace);
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
      } while (!replace.isArg() || !isInit(replace.arg()));

      if (replace != null && replace.isArg()) {
        return replace;
      }

      return null;
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