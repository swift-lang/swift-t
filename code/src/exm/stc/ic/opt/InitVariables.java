package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ForeignFunctions.SpecialFunction;
import exm.stc.common.lang.PassedVar;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.Sets;
import exm.stc.ic.ICUtil;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.CommonFunctionCall;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Statement;

/**
 * Analysis of which variables are initialized.
 */
public class InitVariables {
  public static class InitState {
    public final HierarchicalSet<Var> initVars;
    public final HierarchicalSet<Var> assignedVals;
    
    private InitState(HierarchicalSet<Var> initVars,
        HierarchicalSet<Var> assignedVals) {
      this.initVars = initVars;
      this.assignedVals = assignedVals;
    }
    
    public InitState() {
      this(new HierarchicalSet<Var>(), new HierarchicalSet<Var>());
    }
    
    public InitState makeChild() {
      return new InitState(initVars.makeChild(), assignedVals.makeChild());
    }
    

    /**
     * Create new state initialized for function
     * @param fn
     * @return
     */
    public static InitState enterFunction(Function fn) {
      InitState state = new InitState();
      for (Var v: fn.getInputList()) {
        if (varMustBeInitialized(v, false)) {
          state.initVars.add(v);
        }
        if (assignBeforeRead(v)) {
          state.assignedVals.add(v);
        }
      }
      for (Var v: fn.getOutputList()) {
        if (varMustBeInitialized(v, true)) {
          state.initVars.add(v);
        }
      }
      return state;
    }
    

    /**
     * Create child initialized for continuation
     * @param cont
     * @return
     */
    public InitState enterContinuation(Continuation cont) {
      InitState contState = makeChild();

      for (Var v: cont.constructDefinedVars()) {
        if (varMustBeInitialized(v, false)) {
          contState.initVars.add(v);
        }
        if (assignBeforeRead(v)) {
          contState.assignedVals.add(v);
        }
      }
      return contState;
    }
    
    public static boolean canUnifyBranches(Continuation cont) {
      return cont.isExhaustiveSyncConditional();
    }
    
    /**
     * Unify any information from branches of continuation
     * @param cont
     * @param branchStates
     */
    public void unifyBranches(Continuation cont,
                              List<InitState> branchStates) {
      assert(canUnifyBranches(cont));
      List<Set<Var>> branchInitVars = new ArrayList<Set<Var>>();
      List<Set<Var>> branchAssigned = new ArrayList<Set<Var>>();
      for (InitState branchState: branchStates) {
        branchInitVars.add(branchState.initVars);
        branchAssigned.add(branchState.assignedVals);
      }
      initVars.addAll(Sets.intersection(branchInitVars));
      assignedVals.addAll(Sets.intersection(branchAssigned));
    }
    
  }
  
  

  /**
   * Analysis that performs validation of variable initialization
   * within a function. Throws a runtime error if a problem is
   * found.
   * 
   * @param logger
   * @param fn
   * @param block
   */
  public static void checkVarInit(Logger logger, Function fn) {
    recurseOnBlock(logger, fn.mainBlock(), InitState.enterFunction(fn), true);
  }
  
  /**
   * Perform analysis on a statement to determine what is initialized
   * by it. This doesn't perform any validation.
   * @param logger
   * @return
   */
  public static InitState analyze(Logger logger, Statement stmt) {
    InitState state = new InitState();
    updateInitVars(logger, stmt, state, false);
    return state;
  }
  
  /**
   * Check variable initialization recursively on continuation.
   * This is the workhorse of this module that traverses the intermediate
   * representation and finds out which variables are initialized 
   * @param logger
   * @param fn
   * @param state Initialized vars.  Updated if we discover that more vars
   *      are initialized after continuation
   * @param validate if true, validate correct usage of initialized variables.
   *              For validation to work, initVars and assignedVals must
   *              be updated correctly to reflect initializations that
   *              occurred in outer continuations
   * @param c
   */
  public static void checkInitCont(Logger logger, InitState state,
      Continuation c, boolean validate) {
    
    if (validate) {
      for (Var v: c.requiredVars(false)) {
        checkInitialized(c.getType(), state.initVars, v, false);
        checkAssigned(c.getType(), state.assignedVals, v);
      }
    }
    if (validate && c.isAsync()) {
      // If alias var passed to async continuation, must be initialized
      for (PassedVar pv: c.getPassedVars()) {
        checkInitialized(c.getType(), state.initVars, pv.var, false);
        checkAssigned(c.getType(), state.assignedVals, pv.var);
      }
    }

    
    if (validate || InitState.canUnifyBranches(c)) {
      recurseOnContinuation(logger, state, c, validate);
    }
  }

  private static void recurseOnContinuation(Logger logger, InitState state,
      Continuation cont, boolean validate) {
    // Only recurse if we're validating, or if we need info from inner blocks
    boolean unifyBranches = InitState.canUnifyBranches(cont);
    List<InitState> branchStates = null;
    if (unifyBranches) {
      branchStates = new ArrayList<InitState>();
    }
    
    InitState contState = state.enterContinuation(cont);
    for (Block inner: cont.getBlocks()) {
      InitState blockState = contState.makeChild();

      recurseOnBlock(logger, inner, blockState, validate);
      if (unifyBranches) {
        branchStates.add(blockState);
      }
    }
    
    // Unify information from branches into parent
    if (unifyBranches) {
      state.unifyBranches(cont, branchStates);
    }
  }

  private static void recurseOnBlock(Logger logger,
      Block block, InitState state, boolean validate) {
    if (validate) {
      for (Var v: block.getVariables()) {
        if (v.mapping() != null) {
          if (varMustBeInitialized(v.mapping(), false)) {
            assert (state.initVars.contains(v.mapping())):
              v + " mapped to uninitialized var " + v.mapping();
          }
          if (assignBeforeRead(v.mapping())) {
            assert(state.assignedVals.contains(v.mapping())) :
              v + " mapped to unassigned var " + v.mapping();
          }
        }
      }
    }
    
    for (Statement stmt: block.getStatements()) {
      updateInitVars(logger, stmt, state, validate);
    }
    
    for (Continuation c: block.getContinuations()) {
      if (validate || InitState.canUnifyBranches(c)) {
        // Only recurse if this might result in more initialized vars
        // being detected, or if we're recursively validating things
        checkInitCont(logger, state, c, validate);
      }
    }
  }

  private static void checkInitialized(Object context,
      HierarchicalSet<Var> initVars, Var var, boolean output) {
    if (varMustBeInitialized(var, output) &&
        !initVars.contains(var)) {
      throw new STCRuntimeError("Uninitialized var " +
                    var + " in " + context.toString());
    }
  }
  
  private static void checkAssigned(Object context,
      HierarchicalSet<Var> assignedVals, Var inVar) {
    if (assignBeforeRead(inVar)
        && !assignedVals.contains(inVar)) {
      throw new STCRuntimeError("Var " + inVar + " was an unassigned value " +
                               " read in instruction " + context.toString());
    }
  }
  
  
  /**
   * Update state with variables initialized by statement
   * @param logger
   * @param stmt
   * @param state
   * @param validate
   */
  public static void updateInitVars(Logger logger, 
            Statement stmt, InitState state, boolean validate) {
    switch (stmt.type()) {
      case INSTRUCTION:
        updateInitVars(stmt.instruction(), state, validate);
        break;
      case CONDITIONAL:
        // Recurse on the conditional.
        // This will also fill in information about which variables are closed on the branch
        checkInitCont(logger, state, stmt.conditional(), validate);
        break;
      default:
        throw new STCRuntimeError("Unknown statement type" + stmt.type());
    }
  }
  
  private static void updateInitVars(Instruction inst, InitState state,
                                    boolean validate) {
    
    if (validate) {
      for (Arg in: inst.getInputs()) {
        if (in.isVar()) {
          checkInitialized(inst, state.initVars, in.getVar(), false);
          
          checkAssigned(inst, state.assignedVals, in.getVar());
        }
      }
    }
    List<Var> regularOutputs = inst.getOutputs();
    List<Var> initialized = inst.getInitialized();
    
    if (initialized.size() > 0) {
      regularOutputs = new ArrayList<Var>(regularOutputs);
      for (Var init: initialized) {
        assert(Types.outputRequiresInitialization(init)) : inst + " " + init;
        
        // some functions initialise and assign the future at once
        if (!initAndAssignLocalFile(inst))
          ICUtil.remove(regularOutputs, init);
        if (validate && state.initVars.contains(init)) {
          throw new STCRuntimeError("double initialized variable " + init);
        }
        state.initVars.add(init);
      }
    }
    
    for (Var out: regularOutputs) {
      if (validate) {
        checkInitialized(inst, state.initVars, out, true);
      }
      
      if (assignBeforeRead(out)) {
        boolean added = state.assignedVals.add(out);

        if (validate && !added) {
          throw new STCRuntimeError("double assigned val " + out);
        }
      }
    }
  }

  public static boolean assignBeforeRead(Var v) {
    return v.storage() == Alloc.LOCAL;
  }

  public static boolean varMustBeInitialized(Var v, boolean output) {
    if (output) {
      return Types.outputRequiresInitialization(v);
    } else {
      return Types.inputRequiresInitialization(v);
    } 
  }

  public static boolean initAndAssignLocalFile(Instruction inst) {
    if (inst.op == Opcode.LOAD_FILE) {
      return true;
    } else if (inst.op == Opcode.CALL_FOREIGN_LOCAL &&
          ((CommonFunctionCall)inst).isImpl(SpecialFunction.INPUT_FILE)) {
      return true;
    } else {
      return false;
    }
  }

}
