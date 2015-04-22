package exm.stc.ic.opt.valuenumber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.util.Pair;
import exm.stc.common.util.Sets;
import exm.stc.ic.ICUtil;
import exm.stc.ic.opt.OptUtil;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgOrCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.CongruenceType;
import exm.stc.ic.opt.valuenumber.Congruences.OptUnsafeError;
import exm.stc.ic.opt.valuenumber.ValLoc.Closed;
import exm.stc.ic.opt.valuenumber.ValLoc.IsAssign;
import exm.stc.ic.opt.valuenumber.ValLoc.IsValCopy;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.GlobalConstants;
import exm.stc.ic.tree.TurbineOp;

/**
 * Handle unifying values from multiple branches.
 */
public class UnifiedValues {

  // Cap number of unification iterations to avoid chance of infinite
  // loops in case of bugs, etc.
  static final int MAX_UNIFY_ITERATIONS = 20;

  private UnifiedValues(
      Set<Var> closed, Set<Var> recursivelyClosed,
      List<ValLoc> availableVals) {
    super();
    this.closed = closed;
    this.recursivelyClosed = recursivelyClosed;
    this.availableVals = availableVals;
  }

  public static final UnifiedValues EMPTY = new UnifiedValues(
      Collections.<Var>emptySet(), Collections.<Var>emptySet(),
      Collections.<ValLoc>emptyList());

  /**
   * Assuming that branches are exhaustive, work out the set of variables
   * closed after the conditional has executed.
   *
   * @param state
   * @param branchStates
   * @return
   * @throws OptUnsafeError
   */
  public static UnifiedValues unify(Logger logger, GlobalConstants consts,
                 Function fn, boolean reorderingAllowed, int parentStmtIndex,
                 Congruences state, Continuation cont,
                 List<Congruences> branchStates, List<Block> branchBlocks)
                     throws OptUnsafeError {
    if (logger.isTraceEnabled()) {
      logger.trace("Unifying state from " + branchBlocks.size() +
                   " branches with continuation type " + cont.getType());
      for (int i = 0; i < branchBlocks.size(); i++) {
        logger.trace("Branch " + (i + 1) + " type was " +
                    branchBlocks.get(i).getType());
      }
    }
    if (branchStates.isEmpty()) {
      return EMPTY;
    } else {
      Set<Var> closed = new HashSet<Var>();
      Set<Var> recClosed = new HashSet<Var>();
      unifyClosed(branchStates, closed, recClosed, parentStmtIndex);

      List<ValLoc> availVals = new ArrayList<ValLoc>();
      List<ArgCV> allUnifiedCVs = new ArrayList<ArgCV>();

      // Track which sets of args from each branch are mapped into a
      // unified var
      Map<List<Arg>, Var> unifiedVars = new HashMap<List<Arg>, Var>();
      int iter = 1;
      boolean newCVs;
      do {
        if (logger.isTraceEnabled()) {
          logger.trace("Start iteration " + iter + " of unification");
        }
        newCVs = false;

        for (CongruenceType congType: Arrays.asList(CongruenceType.VALUE,
                                                    CongruenceType.ALIAS)) {

          List<ArgCV> newAllBranchCVs = findAllBranchCVs(state, congType,
                                            branchStates, allUnifiedCVs);

          Pair<List<ValLoc>, Boolean> result = unifyCVs(consts, fn,
                  reorderingAllowed, cont.parent(), parentStmtIndex, congType,
                  branchStates, branchBlocks, newAllBranchCVs, unifiedVars);
          availVals.addAll(result.val1);
          if (result.val2) {
            newCVs = true;
          }

          allUnifiedCVs.addAll(newAllBranchCVs);
          if (logger.isTraceEnabled()) {
            logger.trace("Finish iteration " + iter + " of unification for "
                       + congType + " New CVs: " + result.val1);
          }
        }

        if (iter >= MAX_UNIFY_ITERATIONS) {
          logger.debug("Exceeded max unify iterations.");
          if (logger.isTraceEnabled()) {
            logger.trace(cont); // Dump IR for inspection
          }
          break;
        }
        iter++;
      } while (newCVs);

      return new UnifiedValues(closed, recClosed, availVals);
    }
  }


  /**
   * Merge computed values from different conditional branches
   * @param parent
   * @param branchStates
   * @param branchBlocks
   * @param allBranchCVs
   * @param unifiedLocs
   * @return
   * @throws OptUnsafeError
   */
  private static Pair<List<ValLoc>, Boolean> unifyCVs(GlobalConstants consts,
    Function fn, boolean reorderingAllowed, Block parent, int parentStmtIndex,
    CongruenceType congType, List<Congruences> branchStates,
    List<Block> branchBlocks, List<ArgCV> allBranchCVs,
    Map<List<Arg>, Var> unifiedLocs) throws OptUnsafeError {
    List<ValLoc> availVals = new ArrayList<ValLoc>();

    boolean createdNewBranchCVs = false;

    for (ArgCV cv: allBranchCVs) {
      // See what is same across all branches
      // TODO: this is imperfect in situations where the canonical
      //       name of a value has been changed in a child branch.
      //       Reverse the change using mergedInto map.  Of course,
      //       if the same merge has happened in all branches, we want
      //       to make it happen in the parent too!
      boolean allVals = true;
      boolean allSameLocation = true;
      Closed allClosed = Closed.YES_RECURSIVE;
      IsValCopy anyValCopy = IsValCopy.NO;
      boolean skip = false;

      // Keep track of all locations to use as key into map
      List<Arg> branchLocs = new ArrayList<Arg>(branchStates.size());

      Arg firstLoc = branchStates.get(0).findCanonical(cv, congType);

      for (int i = 0; i < branchStates.size(); i++) {
        Congruences bs = branchStates.get(i);
        Arg loc = bs.findCanonical(cv, congType);
        if (loc == null) {
          // Detect errors in canonicalisation
          Logging.getSTCLogger().warn("Internal warning: Could not locate "
                      + cv + " " + congType + " on branch + " + i,
                      new Exception());
          skip = true;
          continue;
        }

        if (loc != firstLoc && !loc.equals(firstLoc)) {
          allSameLocation = false;
        }

        if (!Types.isPrimValue(loc)) {
          allVals = false;
        }

        int branchStmtCount = branchBlocks.get(i).getStatements().size();
        if (allClosed.isRecClosed() &&
            bs.isRecClosed(loc, branchStmtCount)) {
          allClosed = Closed.YES_RECURSIVE;
        } else if (allClosed.isClosed() &&
              bs.isClosed(loc,branchStmtCount)) {
          allClosed = Closed.YES_NOT_RECURSIVE;
        } else {
          allClosed = Closed.MAYBE_NOT;
        }

        branchLocs.add(loc);
      }


      if (Logging.getSTCLogger().isTraceEnabled()) {
        Logging.getSTCLogger().trace(cv + " appears on all branches " +
                    "allSame: " + allSameLocation + " allVals: " + allVals);
      }

      // TODO: fill in with correct
      IsAssign isAssign = IsAssign.NO;

      if (skip) {
        // Do nothing
      } else if (allSameLocation) {
        availVals.add(createUnifiedCV(cv, firstLoc, allClosed, anyValCopy,
                                      isAssign));
      } else if (unifiedLocs.containsKey(branchLocs)) {
        // We already unified this list of variables: just reuse that
        Var unifiedLoc = unifiedLocs.get(branchLocs);
        availVals.add(createUnifiedCV(cv, unifiedLoc.asArg(), allClosed,
                                      anyValCopy, isAssign));
      } else {
        Var unifiedLoc = createUnifyingVar(consts, fn, parent, branchStates,
                  branchBlocks, branchLocs, firstLoc.type());
        createdNewBranchCVs = true;

        // Store the new location
        unifiedLocs.put(branchLocs, unifiedLoc);

        // Signal that value is stored in new var
        availVals.add(createUnifiedCV(cv, unifiedLoc.asArg(), allClosed, anyValCopy,
                                      isAssign));
      }
    }

    return Pair.create(availVals, createdNewBranchCVs);
  }


  private static Var createUnifyingVar(GlobalConstants consts, Function fn,
      Block parent, List<Congruences> branchStates, List<Block> branchBlocks,
      List<Arg> locs, Type type) throws OptUnsafeError {
    boolean isValue = Types.isPrimValue(type);
    // declare new temporary value in outer block
    Var unifiedLoc;
    if (isValue) {
      unifiedLoc = parent.declareUnmapped(type,
          OptUtil.optVPrefix(parent, "unified"), Alloc.LOCAL,
          DefType.LOCAL_COMPILER,
          VarProvenance.unified(ICUtil.extractVars(locs)));
    } else {
      unifiedLoc = parent.declareUnmapped(type,
          OptUtil.optVPrefix(parent, "unified"), Alloc.ALIAS,
          DefType.LOCAL_COMPILER,
          VarProvenance.unified(ICUtil.extractVars(locs)));
    }


    for (int i = 0; i < branchStates.size(); i++) {
      Arg loc = locs.get(i);
      Block branchBlock = branchBlocks.get(i);
      Instruction copyInst;
      ValLoc copyVal;
      if (isValue) {
        copyInst = ICInstructions.valueSet(unifiedLoc, loc);
        copyVal = ValLoc.makeCopy(unifiedLoc, loc, IsAssign.TO_LOCATION);
      } else {
        assert (loc.isVar()) : loc + " " + loc.getKind();
        copyInst = TurbineOp.copyRef(unifiedLoc, loc.getVar());
        copyVal = ValLoc.makeAlias(unifiedLoc, loc.getVar());
      }
      branchBlock.addInstruction(copyInst);

      // Add in additional computed values resulting from copy
      Congruences branchState = branchStates.get(i);
      int branchStmts = branchBlock.getStatements().size();
      branchState.update(consts, fn.id().uniqueName(), copyVal, branchStmts);
    }
    return unifiedLoc;
  }


  private static ValLoc createUnifiedCV(ArgCV cv, Arg loc,
            Closed allClosed, IsValCopy anyValCopy,
            IsAssign maybeAssigned) {
    return new ValLoc(cv, loc, allClosed, anyValCopy, maybeAssigned);
  }


  /**
   * Find computed values that appear in all branches but not parent
   * @param parentState
   * @param congType
   * @param branchStates
   * @param alreadyAdded ignore these
   * @return
   */
  private static List<ArgCV> findAllBranchCVs(Congruences parentState,
      CongruenceType congType, List<Congruences> branchStates, List<ArgCV> alreadyAdded) {
    List<ArgCV> allBranchCVs = new ArrayList<ArgCV>();
    Congruences firstState = branchStates.get(0);
    // iterate over values stored in the bottom level only?
    for (ArgOrCV val: firstState.availableThisScope(congType)) {
      ArgCV convertedVal = parentState.convertToArgs(val, congType);
      if (convertedVal != null) {
        if (!alreadyAdded.contains(convertedVal) &&
            !parentState.isAvailable(convertedVal, congType)) {
          int nBranches = branchStates.size();
          boolean presentInAll = true;
          for (Congruences otherState: branchStates.subList(1, nBranches)) {
            if (!otherState.isAvailable(convertedVal, congType)) {
              presentInAll = false;
              break;
            }
          }
          if (presentInAll) {
            allBranchCVs.add(convertedVal);
          }
        }
      }
    }
    return allBranchCVs;
  }

  private static void unifyClosed(List<Congruences> branchStates,
      Set<Var> closed, Set<Var> recClosed, int parentStmtIndex) {
    List<Set<Var>> branchClosed = new ArrayList<Set<Var>>();
    List<Set<Var>> branchRecClosed = new ArrayList<Set<Var>>();
    for (Congruences branchState: branchStates) {
      // Inspect all variables that are closed in each branch
      branchClosed.add(branchState.getScopeClosed(false));
      branchRecClosed.add(branchState.getScopeClosed(true));
    }


    // Add variables closed in first branch that aren't closed in parent
    // to output sets
    for (Var closedVar: Sets.intersectionIter(branchClosed)) {
      closed.add(closedVar);
    }

    for (Var recClosedVar: Sets.intersectionIter(branchRecClosed)) {
      recClosed.add(recClosedVar);
    }
  }

  final Set<Var> closed;
  final Set<Var> recursivelyClosed;
  final List<ValLoc> availableVals;
}