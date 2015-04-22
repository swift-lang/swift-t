package exm.stc.ic.opt;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.ForeignFunctions;
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

    Set<FnID> needed = findNeeded(deps);

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
    final ListMultimap<FnID, FnID> depGraph = ArrayListMultimap.create();

    @Override
    public void visit(Logger logger, Function currFn, Instruction inst) {
      if (inst instanceof CommonFunctionCall) {
        CommonFunctionCall fnCall = (CommonFunctionCall)inst;
        depGraph.put(currFn.id(), fnCall.functionID());
      } else if (inst.op == Opcode.ASYNC_OP) {
        // Async ops can be implemented with builtins
        List<FnID> fnIDs = foreignFuncs.findOpImpl(((Builtin)inst).subop);
        if (fnIDs != null) {
          for (FnID fnID: fnIDs) {
            depGraph.put(currFn.id(), fnID);
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
  private Set<FnID> findNeeded(DepFinder deps) {
    Set<FnID> needed = new HashSet<FnID>();
    StackLite<FnID> workQueue = new StackLite<FnID>();

    // Entry point is always needed
    addFunction(needed, workQueue, FnID.ENTRY_FUNCTION);

    while (!workQueue.isEmpty()) {
      FnID curr = workQueue.pop();
      List<FnID> fnIDs = deps.depGraph.removeAll(curr);
      addFunctions(needed, workQueue, fnIDs);
    }
    return needed;
  }

  /**
   * Functions may be translated into local implementation
   * @param deps
   */
  private void addLocalImpls(ForeignFunctions foreignFuncs, DepFinder deps) {
    for (FnID func: foreignFuncs.getLocalImplKeys()) {
      deps.depGraph.put(func, foreignFuncs.getLocalImpl(func));
    }
  }

  private void addFunction(Set<FnID> needed, StackLite<FnID> workQueue,
                           FnID fnID) {
    boolean added = needed.add(fnID);
    if (added) {
      // Need to chase dependencies
      workQueue.add(fnID);
    }
  }

  private void addFunctions(Set<FnID> needed, StackLite<FnID> workQueue,
      List<FnID> fnIDs) {
    for (FnID fnID: fnIDs) {
      addFunction(needed, workQueue, fnID);
    }
  }

  private void pruneBuiltins(Program program, Set<FnID> needed) {
    Iterator<BuiltinFunction> bIt = program.builtinIterator();
    while (bIt.hasNext()) {
      BuiltinFunction f = bIt.next();
      if (!needed.contains(f.id())) {
        bIt.remove();
      }
    }
  }

  private void pruneFunctions(Program program, Set<FnID> needed) {
    Iterator<Function> fIt = program.functionIterator();
    while (fIt.hasNext()) {
      Function f = fIt.next();
      if (!needed.contains(f.id())) {
        fIt.remove();
      }
    }
  }

}
