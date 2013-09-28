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
import exm.stc.common.util.Pair;
import exm.stc.common.util.Sets;
import exm.stc.ic.opt.OptUtil;
import exm.stc.ic.opt.valuenumber.ComputedValue.ArgCV;
import exm.stc.ic.opt.valuenumber.ComputedValue.CongruenceType;
import exm.stc.ic.opt.valuenumber.ComputedValue.RecCV;
import exm.stc.ic.opt.valuenumber.ValLoc.Closed;
import exm.stc.ic.opt.valuenumber.ValLoc.IsValCopy;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
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
   */
  public static UnifiedValues unify(Logger logger, Function fn,
                 boolean reorderingAllowed,
                 CongruentVars state, Continuation cont,
                 List<CongruentVars> branchStates, List<Block> branchBlocks) {
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
      unifyClosed(state, branchStates, closed, recClosed);
      
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
          Pair<List<ValLoc>, Boolean> result = unifyCVs(fn, reorderingAllowed,
                                  cont.parent(), congType, branchStates,
                                  branchBlocks, newAllBranchCVs, unifiedVars);
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
   */
  private static Pair<List<ValLoc>, Boolean> unifyCVs(Function fn,
      boolean reorderingAllowed, Block parent, CongruenceType congType,
      List<CongruentVars> branchStates, List<Block> branchBlocks,
      List<ArgCV> allBranchCVs, Map<List<Arg>, Var> unifiedLocs) {
    List<ValLoc> availVals = new ArrayList<ValLoc>();
    
    boolean createdNewBranchCVs = false;
    
    for (ArgCV cv: allBranchCVs) {
      // See what is same across all branches
      // TODO: this is imperfect in situations where the canonical
      //       name of a value has been changed in a child branch.
      //       May not be able to do much about it.
      boolean allVals = true;
      boolean allSameLocation = true;
      Closed allClosed = Closed.YES_RECURSIVE;
      IsValCopy anyValCopy = IsValCopy.NO;
      
      // Keep track of all locations to use as key into map
      List<Arg> branchLocs = new ArrayList<Arg>(branchStates.size());

      Arg firstLoc = branchStates.get(0).findCanonical(cv, congType);
      for (CongruentVars bs: branchStates) {
        Arg loc = bs.findCanonical(cv, congType);
        assert(loc != null);
        
        if (loc != firstLoc && !loc.equals(firstLoc)) {
          allSameLocation = false;
        }
        
        if (!Types.isPrimValue(loc.type())) {
          allVals = false;
        }
        
        if (allClosed == Closed.YES_RECURSIVE &&
            !bs.isRecClosed(loc)) {
          if (bs.isClosed(loc)) {
            allClosed = Closed.MAYBE_NOT;
          } else {
            allClosed = Closed.YES_NOT_RECURSIVE;
          }
        } else if (allClosed == Closed.YES_NOT_RECURSIVE &&
            !bs.isClosed(loc)) {
          allClosed = Closed.MAYBE_NOT;
        }
        
        branchLocs.add(loc);
      }
      

      if (Logging.getSTCLogger().isTraceEnabled()) {
        Logging.getSTCLogger().trace(cv + " appears on all branches " +
                    "allSame: " + allSameLocation + " allVals: " + allVals);
      }
      
      if (allSameLocation) {
        availVals.add(createUnifiedCV(cv, firstLoc, allClosed, anyValCopy));
      } else if (unifiedLocs.containsKey(branchLocs)) {
        // We already unified this list of variables: just reuse that
        Var unifiedLoc = unifiedLocs.get(branchLocs);
        availVals.add(createUnifiedCV(cv, unifiedLoc.asArg(), allClosed, anyValCopy));
      } else {
        Var unifiedLoc = createUnifyingVar(fn, parent, branchStates,
                  branchBlocks, branchLocs, firstLoc.type());
        createdNewBranchCVs = true;

        // Store the new location
        unifiedLocs.put(branchLocs, unifiedLoc);
        
        // Signal that value is stored in new var
        availVals.add(createUnifiedCV(cv, unifiedLoc.asArg(), allClosed, anyValCopy));
      }
    }
    
    return Pair.create(availVals, createdNewBranchCVs);
  }


  private static Var createUnifyingVar(Function fn,
      Block parent, List<CongruentVars> branchStates, List<Block> branchBlocks,
      List<Arg> locs, Type type) {
    boolean isValue = Types.isPrimValue(type);
    // declare new temporary value in outer block
    Var unifiedLoc;
    if (isValue) {
      unifiedLoc = parent.declareVariable(type, 
          OptUtil.optVPrefix(parent, "unified"), Alloc.LOCAL,
          DefType.LOCAL_COMPILER, null);
    } else {
      unifiedLoc = parent.declareVariable(type, 
          OptUtil.optVPrefix(parent, "unified"), Alloc.ALIAS,
          DefType.LOCAL_COMPILER, null);
    }
    
    
    for (int i = 0; i < branchStates.size(); i++) {
      Arg loc = locs.get(i);
      Block branchBlock = branchBlocks.get(i);
      Instruction copyInst;
      ValLoc copyVal;
      if (isValue) {
        copyInst = ICInstructions.valueSet(unifiedLoc, loc);
        copyVal = ValLoc.makeCopy(unifiedLoc, loc);
      } else {
        assert (loc.isVar()) : loc + " " + loc.getKind();
        copyInst = TurbineOp.copyRef(unifiedLoc, loc.getVar());
        copyVal = ValLoc.makeAlias(unifiedLoc, loc.getVar());
      }
      branchBlock.addInstruction(copyInst);
      
      // Add in additional computed values resulting from copy
      CongruentVars branchState = branchStates.get(i);
      branchState.update(fn.getName(), copyVal);
    }
    return unifiedLoc;
  }


  private static ValLoc createUnifiedCV(ArgCV cv, Arg loc,
            Closed allClosed, IsValCopy anyValCopy) {
    return new ValLoc(cv, loc, allClosed, anyValCopy);
  }
  
  
  /**
   * Find computed values that appear in all branches but not parent
   * @param parentState
   * @param congType 
   * @param branchStates
   * @param alreadyAdded ignore these
   * @return
   */
  private static List<ArgCV> findAllBranchCVs(CongruentVars parentState,
      CongruenceType congType, List<CongruentVars> branchStates, List<ArgCV> alreadyAdded) {
    List<ArgCV> allBranchCVs = new ArrayList<ArgCV>();
    CongruentVars firstState = branchStates.get(0);
    // iterate over values stored in the bottom level only?
    for (RecCV val: firstState.availableThisScope(congType)) {
      ArgCV convertedVal = parentState.convertToArgs(val, congType);
      if (convertedVal != null) {
        if (!alreadyAdded.contains(convertedVal) &&
            !parentState.isAvailable(convertedVal, congType)) {
          int nBranches = branchStates.size();
          boolean presentInAll = true;
          for (CongruentVars otherState: branchStates.subList(1, nBranches)) {
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
  
  private static void unifyClosed(CongruentVars parentState,
      List<CongruentVars> branchStates,
      Set<Var> closed, Set<Var> recClosed) {
    List<Set<Var>> branchClosed = new ArrayList<Set<Var>>();
    List<Set<Var>> branchRecClosed = new ArrayList<Set<Var>>();
    for (CongruentVars branchState: branchStates) {
      // Inspect all variables that are closed in each branch
      branchClosed.add(branchState.getClosed());
      branchRecClosed.add(branchState.getRecursivelyClosed());
    }
    

    // Add variables closed in first branch that aren't closed in parent
    // to output sets
    for (Var closedVar: Sets.intersectionIter(branchClosed)) {
      if (!parentState.isClosed(closedVar)) {
        closed.add(closedVar);
      }
    }
    
    for (Var recClosedVar: Sets.intersectionIter(branchRecClosed)) {
      if (!parentState.isRecClosed(recClosedVar)) {
        recClosed.add(recClosedVar);
      }
    }
  }
  
  final Set<Var> closed;
  final Set<Var> recursivelyClosed;
  final List<ValLoc> availableVals;
}