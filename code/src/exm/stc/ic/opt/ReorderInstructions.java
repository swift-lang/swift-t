package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;
import exm.stc.common.util.Pair;
import exm.stc.common.util.StackLite;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.Opcode;

/**
 * Reorder instructions within a block to try and maximize chances for
 * successful further optimization.
 *
 * E.g. if instruction A reads a dataflow variable and instruction B writes it, we
 *    should move A to after B
 *
 * This also works recursively for compound statements
 *
 * Overall we try to be conservative: if we can't determine whether A must precede B,
 * we assume that their current order is correct.
 */
public class ReorderInstructions extends FunctionOptimizerPass {

  public ReorderInstructions() {
  }

  @Override
  public String getPassName() {
    return "Reorder instructions";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_REORDER_INSTS;
  }

  @Override
  public void optimize(Logger logger, Function f) throws UserException {
    optimizeRec(logger, f, f.mainBlock());
  }

  private void optimizeRec(Logger logger, Function f, Block block) {
    reorderInBlock(logger, f, block);

    // Recurse on all sub-blocks
    for (Continuation c: block.allComplexStatements()) {
      for (Block inner: c.getBlocks()) {
        optimizeRec(logger, f, inner);
      }
    }
  }

  /**
   * Try reordering statements.
   * @param logger
   * @param block
   */
  private void reorderInBlock(Logger logger, Function fn, Block block) {
    logger.trace("tryReorder");

    // Compute StatementInfo objects
    ArrayList<StatementInfo> stmtInfos =
            new ArrayList<StatementInfo>(block.getStatements().size());
    for (Statement stmt: block.getStatements()) {
      stmtInfos.add(StatementInfo.make(logger, fn, stmt));
    }

    // Keep track of which instructions should go after (x -> goes before)
    // Instructions are identified by index of instruction before modifications
    MultiMap<Integer, Integer> before = new MultiMap<Integer, Integer>();

    // Do two passes for forward and backward edges.  Treat as hard and soft
    // dependencies respectively to avoid creating cycles
    for (int i = 0; i < stmtInfos.size(); i++) {
      addDependencies(logger, fn, stmtInfos, i, true, before);
    }

    for (int i = 0; i < stmtInfos.size(); i++) {
      addDependencies(logger, fn, stmtInfos, i, false, before);
    }

    for (int i = 0; i < stmtInfos.size(); i++) {
      StatementInfo info1 = stmtInfos.get(i);
      if (logger.isTraceEnabled())
        logger.trace("Inst " + info1 + "(" + i + ") before: " + before.get(i));
    }

    block.replaceStatements(rebuildInstructions(logger, block, before));
  }

  /**
   * Rebuild instruction list based on dependency info.
   *
   * Note that there are multiple possible valid orders.
   * @param block
   * @param before constraints.  if i -> j, then j must go before i
   * @return rebuilt list of instructions: should contain same as
   *      oldStatements in different order, subject to ordering constraints
   */
  private List<Statement> rebuildInstructions(Logger logger, Block block,
      MultiMap<Integer, Integer> before) {
    List<Statement> oldStatements = block.getStatements();

    // Accumulate instructions into this array
    ArrayList<Integer> newStatements = new ArrayList<Integer>();

    // Put all instructions back.  We do a topological sort to
    // make sure they end up in correct order
    Map<Integer, VisitState> visited = new HashMap<Integer, VisitState>();

    for (Integer alreadyAdded: newStatements) {
      visited.put(alreadyAdded, VisitState.DONE);
      before.remove(alreadyAdded);
    }

    if (logger.isTraceEnabled()) {
      logger.trace(before);
    }

    // Allow changing order of topological sort.  Useful for debugging
    List<Integer> startNodes = new ArrayList<Integer>();
    for (int i = 0; i < oldStatements.size(); i++) {
      startNodes.add(i);
    }

    //Collections.reverse(startNodes);

    // Do a topological sort with depth first search
    for (int dfsStart: startNodes) {
      VisitState startState = visited.get(dfsStart);
      if (startState != null) {
        // Shouldn't be incompletely processed
        assert(startState == VisitState.DONE);
        // Already added
        continue;
      }
      logger.trace("dfsStart: " + dfsStart);

      // (Statement index, whether processed)
      StackLite<Pair<Integer, Boolean>> stack =
            new StackLite<Pair<Integer, Boolean>>();
      stack.push(Pair.create(dfsStart, false));
      while (!stack.isEmpty()) {
        Pair<Integer, Boolean> curr = stack.pop();
        Integer currIx = curr.val1;
        Boolean processed = curr.val2;

        logger.trace("visit: " + curr);

        if (processed) {
          // All dependencies already added: add this below them
          newStatements.add(currIx);
          visited.put(currIx, VisitState.DONE);
        } else {
          VisitState state = visited.get(currIx);
          if (state != null) {
            // Already visited
            if (state == VisitState.IN_STACK) {
              List<Statement> cycle = new ArrayList<Statement>();
              for (Pair<Integer, Boolean> p: stack) {
                if (p.val2) {
                  cycle.add(oldStatements.get(p.val1));
                }
              }
              throw new STCRuntimeError("Circular dependency: " + cycle);
            }

          } else {
            // First time processing this node
            visited.put(currIx, VisitState.IN_STACK);
            stack.push(Pair.create(currIx, true));
            List<Integer> beforeIxs = before.remove(currIx);
            if (!beforeIxs.isEmpty()) {
              // sort into canonical order to elim non-determinism
              beforeIxs = new ArrayList<Integer>(beforeIxs);
              Collections.sort(beforeIxs);

              for (int beforeIx: beforeIxs) {
                stack.push(Pair.create(beforeIx, false));
              }
            }
          }
        }
      }

    }
    assert(before.isEmpty()) : before + " " + visited + " " + newStatements;

    assert(newStatements.size() == oldStatements.size());

    List<Statement> rebuilt = new ArrayList<Statement>(newStatements.size());
    for (int stmtIx: newStatements) {
      rebuilt.add(oldStatements.get(stmtIx));
    }
    if (logger.isTraceEnabled()) {
      logger.trace("New instruction order: " + newStatements);
    }
    return rebuilt;
  }

  private void addDependencies(Logger logger, Function fn,
          ArrayList<StatementInfo> stmtInfos, int i, boolean forwardEdges,
          MultiMap<Integer, Integer> before) {
    StatementInfo info1 = stmtInfos.get(i);

    if (logger.isTraceEnabled())
      logger.trace("addDependencies " + info1);

    // Find last instruction that writes inputs of inst1
    // Build a DAG of dependences between instructions
    for (int j = i + 1; j < stmtInfos.size(); j++) {
      StatementInfo info2 = stmtInfos.get(j);
      // TODO: should check for "expensive" statements to avoid
      // deferring execution by putting instruction after it
      if (forwardEdges && writesInputs(logger, info2, info1, false)) {
        // These edges wont create cycle - backward edge
        before.put(j, i);
      }

      if (!forwardEdges && writesInputs(logger, info1, info2, true)) {
        // Check that there isn't a path from inst1 to inst2
        if (pathExists(before, j, i)) {
          if (logger.isTraceEnabled()) {
            logger.trace("Drop edge " + j + " => " + i + " to avoid cycle");
          }
        } else {
          before.put(i, j);
        }
      }
    }
  }

  private boolean pathExists(MultiMap<Integer, Integer> after,
      int from, int to) {
    Set<Integer> visited = new HashSet<Integer>();
    visited.add(from);

    StackLite<Integer> stack = new StackLite<Integer>();
    stack.push(from);
    while (!stack.isEmpty()) {
      Integer curr = stack.pop();
      if (curr.equals(to)) {
        return true;
      }

      for (int dst: after.get(curr)) {
        if (!visited.contains(dst)) {
          stack.push(dst);
          visited.add(dst);
        }
      }
    }
    return false;
  }

  /**
   * Find list of variables that this instruction reads
   * @param inst
   * @return
   */
  private static List<Var> getAllInputs(Instruction inst) {
    List<Var> inst1Inputs = new ArrayList<Var>();
    for (Arg a: inst.getInputs()) {
      if (a.isVar()) {
        inst1Inputs.add(a.getVar());
      }
    }
    for (Var v: inst.getReadOutputs()) {
      inst1Inputs.add(v);
    }
    return inst1Inputs;
  }


  private boolean writesInputs(Logger logger,
      StatementInfo info1,
      StatementInfo info2, boolean checkNotCircular) {
    if (!writesInputs(logger, info1, info2))
      return false;
    // Check if there is some sort of circular dependency
    if (checkNotCircular && writesInputs(logger, info2,
                                         info1, false))
      return false;
    else
      return true;
  }

  /**
   * Return true if inst2 writes some of inst1's required vars
   *
   * TODO: the current approach can result in false positives, e.g.
   * alias instructions look like they're reading variable.
   * @param logger
   * @param inst1
   * @param inst1Inputs
   * @param inst2
   * @param inst2Inputs
   * @return
   */
  private boolean writesInputs(Logger logger,
          StatementInfo info1,
          StatementInfo info2) {

    for (Instruction storeRef: info2.getByOpcode(Opcode.STORE_REF)) {
      assert(storeRef.op == Opcode.STORE_REF);
      if (info1.piecewiseAssigned.contains(storeRef.getOutput(0))) {
        // Special case for address_of: otherwise looks like they both write it
        if (logger.isTraceEnabled())
          logger.trace(info2 + " pieces " + info1);
        return true;
      }
    }

    // Check for initialization of outputs (inputs covered by other logic)
    if (initializesOutputs(info1, info2)) {
      if (logger.isTraceEnabled())
        logger.trace(info2 + " initializes output of " + info1);
      return true;
    }


    for (Var inst2Output: info2.modifiedOutputs) {
      if (info1.inputVars.contains(inst2Output)) {
        if (logger.isTraceEnabled())
          logger.trace(info2 + " modifies input of " + info1);
        return true;
      }
    }
    return false;
  }

  /**
   * Check if inst2 initialized outputs of inst2
   * @param logger
   * @param inst1
   * @param inst2
   * @return
   */
  private boolean initializesOutputs(StatementInfo info1,
                                     StatementInfo info2) {
    Collection<Var> init = info2.initialized;
    if (!init.isEmpty()) {
      for (Var output: info1.outputs) {
        if (Types.outputRequiresInitialization(output) &&
            init.contains(output)) {
          return true;
        }
      }
    }
    return false;
  }

  private static enum VisitState {
    IN_STACK,
    DONE,
  }

  /**
   * Collect relevant info about statement for ordering
   */
  private static class StatementInfo {

    public StatementInfo(Statement stmt,
        MultiMap<Opcode, Instruction> opcodeMap,
        Collection<Var> inputVars,
        Collection<Var> outputs,
        Collection<Var> modifiedOutputs,
        Collection<Var> initialized,
        Collection<Var> piecewiseAssigned) {
      super();
      this.stmt = stmt;
      this.opcodeMap = opcodeMap;
      this.inputVars = inputVars;
      this.outputs = outputs;
      this.modifiedOutputs = modifiedOutputs;
      this.initialized = initialized;
      this.piecewiseAssigned = piecewiseAssigned;
    }

    public static StatementInfo make(Logger logger,
        Function fn, Statement stmt) {
      switch (stmt.type()) {
        case INSTRUCTION: {
          Instruction inst = stmt.instruction();
          return new StatementInfo(stmt,
              null,
              getAllInputs(inst),
              inst.getOutputs(),
              inst.getModifiedOutputs(),
              Pair.extract1(inst.getInitialized()),
              inst.getPiecewiseAssignedOutputs());
        }
        case CONDITIONAL: {
          // need to walk recursively and collect info
          StatementInfo info = new StatementInfo(stmt,
              new MultiMap<Opcode, Instruction>(),
              new HashSet<Var>(),
              new HashSet<Var>(),
              new HashSet<Var>(),
              new HashSet<Var>(),
              new HashSet<Var>());
          ConditionalWalker walker = new ConditionalWalker(info);
          TreeWalk.walk(logger, fn, (Conditional)stmt, true, walker);
          return info;
        }
        default:
          throw new STCRuntimeError("Unknown statement type " + stmt.type());
      }
    }

    final Statement stmt;
    final Collection<Var> inputVars;
    final Collection<Var> outputs;
    final Collection<Var> modifiedOutputs;
    final Collection<Var> initialized;
    // Variables maybe piecewise assigned anywhere in statement
    final Collection<Var> piecewiseAssigned;

    final MultiMap<Opcode, Instruction> opcodeMap;

    /**
     * Find instructions matching opcode
     * @param op
     * @return
     */
    public List<Instruction> getByOpcode(Opcode op) {
      if (opcodeMap != null) {
        return opcodeMap.get(op);
      } else {
        assert(stmt instanceof Instruction);
        Instruction inst = (Instruction)stmt;
        if (inst.op == op) {
          return Collections.singletonList(inst);
        } else {
          return Collections.emptyList();
        }
      }
    }

    // Print shorted version of statement
    @Override
    public String toString() {
      String full = stmt.toString();
      return full.substring(0, Math.min(40, full.length()))
                 .replace('\n', ' ');
    }
  }

  private static class ConditionalWalker extends TreeWalker {
    final StatementInfo info;
    public ConditionalWalker(StatementInfo info) {
      this.info = info;
    }

    @Override
    protected void visit(Continuation cont) {
      info.inputVars.addAll(cont.requiredVars(false));
    }
    @Override
    protected void visitDeclaration(Var declared) {

    }
    @Override
    protected void visit(Instruction inst) {
      // TODO: don't need to track variables declared in lower scopes
      info.opcodeMap.put(inst.op, inst);
      info.inputVars.addAll(getAllInputs(inst));
      info.outputs.addAll(inst.getOutputs());
      info.modifiedOutputs.addAll(inst.getModifiedOutputs());
      info.initialized.addAll(Pair.extract1(inst.getInitialized()));
      info.piecewiseAssigned.addAll(inst.getPiecewiseAssignedOutputs());
    }

    @Override
    protected void visit(CleanupAction cleanup) {
      visit(cleanup.action()); // treat same as instruction
    }
  }
}
