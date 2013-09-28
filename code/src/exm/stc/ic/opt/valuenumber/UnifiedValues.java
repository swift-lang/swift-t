package exm.stc.ic.opt.valuenumber;

import java.util.ArrayList;
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
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.util.Pair;
import exm.stc.common.util.Sets;
import exm.stc.common.util.TernaryLogic.Ternary;
import exm.stc.ic.opt.OptUtil;
import exm.stc.ic.opt.valuenumber.ComputedValue.EquivalenceType;
import exm.stc.ic.opt.valuenumber.ValLoc.Closed;
import exm.stc.ic.opt.valuenumber.ValLoc.IsValCopy;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.TurbineOp;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;

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
   * @param parentState
   * @param branchStates
   * @return
   */
  public static UnifiedValues unify(Logger logger, boolean reorderingAllowed,
                 ValueTracker parentState, Continuation cont,
                 List<ValueTracker> branchStates, List<Block> branchBlocks) {
    if (logger.isTraceEnabled()) {
      logger.trace("Unifying state from " + branchBlocks.size() +
                   " branches with continuation type " + cont.getType());
      for (int i = 0; i < branchBlocks.size(); i++) {
        logger.trace("Branch " + (i + 1) + " type was " +
                    branchBlocks.get(i).getType());
      }
      logger.trace(cont.toString());
    }
    if (branchStates.isEmpty()) {
      return EMPTY;
    } else {
      Set<Var> closed = new HashSet<Var>();
      Set<Var> recClosed = new HashSet<Var>();
      unifyClosed(parentState, branchStates, closed, recClosed);
      
      List<ValLoc> availVals = new ArrayList<ValLoc>();
      List<ComputedValue<Arg>> allUnifiedCVs =
                  new ArrayList<ComputedValue<Arg>>();
      
      // Track which sets of args from each branch are mapped into a
      // unified var
      Map<List<Arg>, Var> unifiedVars = new HashMap<List<Arg>, Var>(); 
      int iter = 1;
      boolean newCVs;
      do {
        if (logger.isTraceEnabled()) {
          logger.trace("Start iteration " + iter + " of unification");
        }
        List<ComputedValue<Arg>> newAllBranchCVs = findAllBranchCVs(
            parentState, branchStates, allUnifiedCVs);
        Pair<List<ValLoc>, Boolean> result = unifyCVs(reorderingAllowed,
                                 cont.parent(), branchStates, branchBlocks,
                                             newAllBranchCVs, unifiedVars);
        availVals.addAll(result.val1);
        newCVs = result.val2;
        allUnifiedCVs.addAll(newAllBranchCVs);
        
        if (logger.isTraceEnabled()) {
          logger.trace("Finish iteration " + iter + " of unification.  "
                     + "New CVs: " + result.val1);
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
   */
  private static Pair<List<ValLoc>, Boolean> unifyCVs(boolean
      reorderingAllowed, Block parent,
      List<ValueTracker> branchStates, List<Block> branchBlocks,
      List<ComputedValue<Arg>> allBranchCVs, Map<List<Arg>, Var> unifiedLocs) {
    List<ValLoc> availVals = new ArrayList<ValLoc>();
    
    boolean createdNewBranchCVs = false;
    
    for (ComputedValue<Arg> cv: allBranchCVs) {
      // See what is same across all branches
      boolean allVals = true;
      boolean allSameLocation = true;
      Closed allClosed = Closed.YES;
      IsValCopy anyValCopy = IsValCopy.NO;
      
      // Keep track of all locations to use as key into map
      List<Arg> branchLocs = new ArrayList<Arg>(branchStates.size());
          
      int br = 0;
      ValLoc firstLoc = branchStates.get(0).lookupCV(cv);
      for (ValueTracker bs: branchStates) {
        ValLoc loc = bs.lookupCV(cv);
        assert(loc != null);
        
        if (loc != firstLoc && !loc.location().equals(firstLoc.location())) {
          allSameLocation = false;
        }
        
        if (!Types.isPrimValue(loc.location().type())) {
          allVals = false;
        }
        
        if (!loc.locClosed()) {
          allClosed = Closed.MAYBE_NOT;
        }
        
        if (loc.isValCopy()) {
          anyValCopy = IsValCopy.YES;
        }
        
        branchLocs.add(loc.location());
        
        Logger logger = Logging.getSTCLogger();
        if (logger.isTraceEnabled()) {
          logger.trace("Branch " + br + ": " + bs.availableVals);
        }
        br++;
      }
      

      if (Logging.getSTCLogger().isTraceEnabled()) {
        Logging.getSTCLogger().trace(cv + " appears on all branches " +
                    "allSame: " + allSameLocation + " allVals: " + allVals);
      }
      
      if (allSameLocation) {
        availVals.add(createUnifiedCV(cv, firstLoc.location(), allClosed, anyValCopy));
      } else if (unifiedLocs.containsKey(branchLocs)) {
        // We already unified this list of variables: just reuse that
        Var unifiedLoc = unifiedLocs.get(branchLocs);
        availVals.add(createUnifiedCV(cv, unifiedLoc.asArg(), allClosed, anyValCopy));
      } else {
        Var unifiedLoc = createUnifyingVar(parent, branchStates,
                  branchBlocks, branchLocs, firstLoc.location().type());
        createdNewBranchCVs = true;

        // Store the new location
        unifiedLocs.put(branchLocs, unifiedLoc);
        
        // Signal that value is stored in new var
        availVals.add(createUnifiedCV(cv, unifiedLoc.asArg(), allClosed, anyValCopy));
      }
    }
    
    return Pair.create(availVals, createdNewBranchCVs);
  }


  private static Var createUnifyingVar(Block parent,
      List<ValueTracker> branchStates, List<Block> branchBlocks,
      List<Arg> locs, Type type) {
    boolean isValue = Types.isPrimValue(type);
    // declare new temporary value in outer block
    Var unifiedLoc;
    EquivalenceType equivType; // Equivalence of src and dst
    if (isValue) {
      unifiedLoc = parent.declareVariable(type, 
          OptUtil.optVPrefix(parent, "unified"), Alloc.LOCAL,
          DefType.LOCAL_COMPILER, null);
      equivType = EquivalenceType.VALUE;
    } else {
      unifiedLoc = parent.declareVariable(type, 
          OptUtil.optVPrefix(parent, "unified"), Alloc.ALIAS,
          DefType.LOCAL_COMPILER, null);
      equivType = EquivalenceType.ALIAS;
    }
    
    
    for (int i = 0; i < branchStates.size(); i++) {
      Arg loc = locs.get(i);
      Block branchBlock = branchBlocks.get(i);
      Instruction copyInst;
      if (isValue) {
        copyInst = ICInstructions.valueSet(unifiedLoc, loc);
      } else {
        assert (loc.isVar()) : loc + " " + loc.getKind();
        copyInst = TurbineOp.copyRef(unifiedLoc, loc.getVar());
      }
      branchBlock.addInstruction(copyInst);
      
      // Add in additional computed values resulting from copy
      ValueTracker branchState = branchStates.get(i);
      branchState.addComputedValues(ValueTracker.makeCopiedRVs(branchState, unifiedLoc,
                       loc, copyInst.getMode(), equivType), Ternary.MAYBE);
    }
    return unifiedLoc;
  }


  private static ValLoc createUnifiedCV(ComputedValue<Arg> cv, Arg loc,
            Closed allClosed, IsValCopy anyValCopy) {
    return new ValLoc(cv, loc, allClosed, anyValCopy);
  }
  
  
  /**
   * Find computed values that appear in all branches but not parent
   * @param parentState
   * @param branchStates
   * @param alreadyAdded ignore these
   * @return
   */
  private static List<ComputedValue<Arg>> findAllBranchCVs(ValueTracker parentState,
      List<ValueTracker> branchStates, List<ComputedValue<Arg>> alreadyAdded) {
    List<ComputedValue<Arg>> allBranchCVs = new ArrayList<ComputedValue<Arg>>();
    ValueTracker firstState = branchStates.get(0);
    for (ComputedValue<Arg> val: firstState.availableVals.keySet()) {
      if (!alreadyAdded.contains(val) && !parentState.isAvailable(val)) {
        int nBranches = branchStates.size();
        boolean presentInAll = true;
        for (ValueTracker otherState: branchStates.subList(1, nBranches)) {
          if (!otherState.isAvailable(val)) {
            presentInAll = false;
            break;
          }
        }
        if (presentInAll) {
          allBranchCVs.add(val);
        }
      }
    }
    return allBranchCVs;
  }
  
  private static void unifyClosed(ValueTracker parentState,
      List<ValueTracker> branchStates,
      Set<Var> closed, Set<Var> recClosed) {
    List<Set<Var>> branchClosed = new ArrayList<Set<Var>>();
    List<Set<Var>> branchRecClosed = new ArrayList<Set<Var>>();
    for (ValueTracker branchState: branchStates) {
      branchClosed.add(branchState.closed);
      branchRecClosed.add(branchState.recursivelyClosed);
    }
    

    // Add variables closed in first branch that aren't closed in parent
    // to output sets
    for (Var closedVar: Sets.intersectionIter(branchClosed)) {
      if (!parentState.isClosed(closedVar)) {
        closed.add(closedVar);
      }
    }
    
    for (Var recClosedVar: Sets.intersectionIter(branchRecClosed)) {
      if (!parentState.isRecursivelyClosed(recClosedVar)) {
        recClosed.add(recClosedVar);
      }
    }
  }
  
  final Set<Var> closed;
  final Set<Var> recursivelyClosed;
  final List<ValLoc> availableVals;
}