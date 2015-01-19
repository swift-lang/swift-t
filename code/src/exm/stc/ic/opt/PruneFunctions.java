package exm.stc.ic.opt;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Constants;
import exm.stc.common.lang.ForeignFunctions;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.StackLite;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.ICInstructions.Builtin;
import exm.stc.ic.tree.ICInstructions.CommonFunctionCall;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.BuiltinFunction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.Opcode;

/**
 * Remove unused functions to shrink IR tree
 */
public class PruneFunctions implements OptimizerPass {

  @Override
  public String getPassName() {
    return "Prune unused functions";
  }

  @Override
  public String getConfigEnabledKey() {
    return null;
  }

  @Override
  public void optimize(Logger logger, Program program) throws UserException {
    ForeignFunctions foreignFuncs = program.foreignFunctions();

    // Function a depends on function b
    DepFinder deps = new DepFinder(foreignFuncs);
    TreeWalk.walk(logger, program, deps);

    addLocalImpls(foreignFuncs, deps);

    Set<String> needed = findNeeded(deps);

    pruneFunctions(program, needed);

    pruneBuiltins(program, needed);
  }

  /**
   * Fill in dependency graph.
   */
  private static class DepFinder extends TreeWalker {
    public DepFinder(ForeignFunctions foreignFuncs) {
      super();
      this.foreignFuncs = foreignFuncs;
    }

    final ForeignFunctions foreignFuncs;
    final MultiMap<String, String> depGraph = new MultiMap<String, String>();

    @Override
    public void visit(Logger logger, Function currFn, Instruction inst) {
      if (inst instanceof CommonFunctionCall) {
        CommonFunctionCall fnCall = (CommonFunctionCall)inst;
        depGraph.put(currFn.getName(), fnCall.functionName());
      } else if (inst.op == Opcode.ASYNC_OP) {
        // Async ops can be implemented with builtins
        List<String> fnNames = foreignFuncs.findOpImpl(((Builtin)inst).subop);
        if (fnNames != null) {
          for (String fnName: fnNames) {
            depGraph.put(currFn.getName(), fnName);
          }
        }
      }
    }
  }

  /**
   * Find the set of needed functions given dependencies between functions
   * @param deps
   * @return
   */
  private Set<String> findNeeded(DepFinder deps) {
    Set<String> needed = new HashSet<String>();
    StackLite<String> workQueue = new StackLite<String>();

    // Entry point is always needed
    addFunction(needed, workQueue, Constants.ENTRY_FUNCTION);

    while (!workQueue.isEmpty()) {
      String curr = workQueue.pop();
      List<String> fnNames = deps.depGraph.remove(curr);
      addFunctions(needed, workQueue, fnNames);
    }
    return needed;
  }

  /**
   * Functions may be translated into local implementation
   * @param deps
   */
  private void addLocalImpls(ForeignFunctions foreignFuncs, DepFinder deps) {
    for (String func: foreignFuncs.getLocalImplKeys()) {
      deps.depGraph.put(func, foreignFuncs.getLocalImpl(func));
    }
  }

  private void addFunction(Set<String> needed, StackLite<String> workQueue,
                           String fnName) {
    boolean added = needed.add(fnName);
    if (added) {
      // Need to chase dependencies
      workQueue.add(fnName);
    }
  }

  private void addFunctions(Set<String> needed, StackLite<String> workQueue,
      List<String> fnNames) {
    for (String fnName: fnNames) {
      addFunction(needed, workQueue, fnName);
    }
  }

  private void pruneBuiltins(Program program, Set<String> needed) {
    Iterator<BuiltinFunction> bIt = program.builtinIterator();
    while (bIt.hasNext()) {
      BuiltinFunction f = bIt.next();
      if (!needed.contains(f.getName())) {
        bIt.remove();
      }
    }
  }

  private void pruneFunctions(Program program, Set<String> needed) {
    Iterator<Function> fIt = program.functionIterator();
    while (fIt.hasNext()) {
      Function f = fIt.next();
      if (!needed.contains(f.getName())) {
        fIt.remove();
      }
    }
  }

}
