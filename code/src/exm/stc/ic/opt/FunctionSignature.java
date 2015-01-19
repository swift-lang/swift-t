package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.lang.Semantics;
import exm.stc.common.lang.TaskProp.TaskProps;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Types.Type;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.common.lang.Var.VarProvenance;
import exm.stc.common.lang.WaitVar;
import exm.stc.common.util.Pair;
import exm.stc.ic.WrapUtil;
import exm.stc.ic.tree.ICInstructions.FunctionCall;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.TurbineOp;

/**
 * Optimize function signature
 */
public class FunctionSignature implements OptimizerPass {

  @Override
  public String getPassName() {
    return "Function signature changing";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_FUNCTION_SIGNATURE;
  }


  /**
   * Switch to passing values directly.
   * Do this before function inlining since function inlining
   * will clean it up.
   * @param logger
   * @param program
   */
  @Override
  public void optimize(Logger logger, Program program) {
    Set<String> usedFunctionNames = program.getFunctionNames();
    Map<String, Function> toInline = new HashMap<String, Function>();
    ListIterator<Function> fnIt = program.functionIterator();
    while (fnIt.hasNext()) {
      Function fn = fnIt.next();
      Function newFn = switchToValuePassing(logger, program.foreignFunctions(),
                                            fn, usedFunctionNames);
      if (newFn != null) {
        fnIt.remove(); // Remove old function
        fnIt.add(newFn);
        usedFunctionNames.add(newFn.getName());

        // We should inline
        toInline.put(fn.getName(), fn);
      }
    }

    // Inline all calls to the old function
    FunctionInline.inlineAllOccurrences(logger, program, toInline);
  }

  private Function switchToValuePassing(Logger logger, ForeignFunctions foreignFuncs,
            Function fn, Set<String> usedFunctionNames) {
    if (fn.blockingInputs().isEmpty())
      return null;

    // Collect list of variables we could switch
    List<Var> switchVars = new ArrayList<Var>();

    for (WaitVar input: fn.blockingInputs()) {
      // See if we can switch to value version
      if (Types.isPrimFuture(input.var)) {
        Type valueT = Types.retrievedType(input.var.type());
        if (Semantics.canPassToChildTask(valueT)) {
          switchVars.add(input.var);
        }
      }
    }

    if (switchVars.isEmpty())
      return null;

    List<Pair<Var, Var>> futValPairs = createValueVars(fn, switchVars);
    Map<Var, Var> switched = new HashMap<Var, Var>();
    for (Pair<Var, Var> fv: futValPairs) {
      switched.put(fv.val1, fv.val2);
      assert(fv.val2 != null);
    }
    List<Var> newIList = buildNewInputList(fn, switched);
    String newName = selectUniqueName(fn.getName(), usedFunctionNames);

    // Block that calls into new version
    Block callNewFunction = callNewFunctionCode(foreignFuncs, fn, newName,
                                                switchVars);
    Block newBlock = fn.swapBlock(callNewFunction);


    // Declare variables in new block and load values
    // Other optimization passes will clear up later
    for (Pair<Var, Var> fv: futValPairs) {
      // declare local stack var and replace argument in
      Var tmpfuture = new Var(fv.val1.type(), fv.val1.name(),
                       Alloc.STACK, DefType.LOCAL_USER,
                       VarProvenance.renamed(fv.val1));
      newBlock.renameVars(fn.getName(),
            Collections.singletonMap(fv.val1, tmpfuture.asArg()),
            RenameMode.REPLACE_VAR, true);
      newBlock.addVariable(tmpfuture);
      Instruction store =
          TurbineOp.storePrim(tmpfuture, fv.val2.asArg());
      newBlock.addInstructionFront(store);
    }

    List<WaitVar> newBlocking = new ArrayList<WaitVar>();
    for (WaitVar wv: fn.blockingInputs()) {
      if (!switchVars.contains(wv.var)) {
        newBlocking.add(wv);
      }
    }
    return new Function(newName, newIList, newBlocking,
                        fn.getOutputList(), fn.mode(), newBlock);
  }

  /**
   *
   * @return new main block for fn
   */
  private Block callNewFunctionCode(ForeignFunctions foreignFuncs,
                              Function fn, String newFunctionName,
                                    List<Var> switched) {
    Block main = new Block(fn);
    // these vars should already be closed.
    // load values and call new function

    List<Arg> fetched = OptUtil.fetchValuesOf(main, switched, false, false);

    List<Arg> callInputs = new ArrayList<Arg>(fn.getInputList().size());
    for (Var inArg: fn.getInputList()) {
      int ix = switched.indexOf(inArg);
      if (ix >= 0) {
        callInputs.add(fetched.get(ix));
      } else {
        callInputs.add(inArg.asArg());
      }
    }

    String frontendName = null;
    FunctionCall callNew = FunctionCall.createFunctionCall(newFunctionName,
                            frontendName, fn.getOutputList(), callInputs, fn.mode(),
                            new TaskProps(), foreignFuncs);
    main.addInstruction(callNew);
    return main;
  }

  private List<Var> buildNewInputList(Function fn, Map<Var, Var> switched) {
    List<Var> newIList = new ArrayList<Var>();
    for (Var oldInput: fn.getInputList()) {
      if (switched.containsKey(oldInput)) {
        newIList.add(switched.get(oldInput));
      } else {
        newIList.add(oldInput);
      }
    }
    return newIList;
  }

  private List<Pair<Var, Var>> createValueVars(Function fn, List<Var> switchVars) {
    List<Pair<Var, Var>> futValPairs = new ArrayList<Pair<Var,Var>>();
    // Create value vars
    for (Var toSwitch: switchVars) {
      // a value var that will have unique name in new context
      String valVarName = OptUtil.optVPrefix(fn.mainBlock(), toSwitch);
      Var valVar = WrapUtil.createValueVar(valVarName,
          Types.retrievedType(toSwitch), toSwitch);

      futValPairs.add(Pair.create(toSwitch, valVar));
    }
    return futValPairs;
  }

  private String selectUniqueName(String prefix, Set<String> used) {
    int nameCounter = 1;
    String newName;
    do {
      newName = prefix + "-" + nameCounter;
      nameCounter++;
    } while (used.contains(newName));
    return newName;
  }

}
