package exm.stc.ic.opt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.util.MultiMap;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.Conditionals.Conditional;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Statement;

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

  // Track statistics about how many times we moved something
  private static int move = 0;
  private static int noMove = 0;
  
  // If true, try to move multiple instructions.  Requires more processing
  // but in some cases will expose more opportunities for reduction
  private final boolean aggressive;
  
  public ReorderInstructions(boolean aggressive) {
    this.aggressive = aggressive;
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
    
    // Accumulate instructions into this array
    ArrayList<Statement> newStmts = new ArrayList<Statement>();
    
    // Keep track of which instructions should go after (x -> goes after y)
    MultiMap<Statement, Statement> after = new MultiMap<Statement, Statement>();
    HashSet<Statement> mustMove = new HashSet<Statement>(); 
    boolean moved = false;
    
    for (int i = 0; i < stmtInfos.size(); i++) {
      StatementInfo info1 = stmtInfos.get(i);
      boolean move = searchForInputWriter(logger, fn, stmtInfos, i, info1,
                                          after, mustMove);

      if (logger.isTraceEnabled())
        logger.trace("Inst " + info1 + " move: " + move);
      if (move) {
        // Note that we should put it later
        moved = true;
        if (logger.isTraceEnabled())
          logger.trace("Inst " + info1 + " after: " + after.get(info1.stmt));
      } else {
        // Don't move
        newStmts.add(info1.stmt);
      }
    }
    

    if (moved) {
      rebuildInstructions(block, block.getStatements(), newStmts, after);
      block.replaceStatements(newStmts);
      move++;
    } else {
      noMove++;
    }
    if (logger.isTraceEnabled())
      logger.trace("reorder instructions: moved " + move + "/" + (move + noMove));
  }

  private void rebuildInstructions(Block block,
      List<Statement> oldStatements,
      ArrayList<Statement> newStatements,
      MultiMap<Statement, Statement> after) {
    // Put all instructions back.  We do a topological sort to
    // make sure they end up in correct order
    ArrayDeque<Statement> stack = new ArrayDeque<Statement>();
    HashSet<Statement> visited = new HashSet<Statement>();
    visited.addAll(newStatements);
    while (!after.isEmpty()) {
      for (Entry<Statement, List<Statement>> e: after.entrySet()) {
        Iterator<Statement> it = e.getValue().iterator();
        while (it.hasNext()) {
          Statement inst = it.next();
          // Find instruction with nothing dependent on it
          if (visited.contains(inst)) {
            // Already added
            it.remove();
          } else if (after.get(inst).isEmpty()) {
            stack.push(inst);
            visited.add(inst);
            it.remove();
          }
        }
      }
    }
    
    // Add any instructions nothing is dependent on at top
    if (visited.size() < oldStatements.size()) {
      for (Statement stmt: oldStatements) {
        if (!visited.contains(stmt)) {
          stack.push(stmt);
        }
      }
    }
    
    while (!stack.isEmpty()) {
      newStatements.add(stack.pop());
    }
    assert(newStatements.size() == oldStatements.size());
  }

  private boolean searchForInputWriter(Logger logger,
          Function fn,
          ArrayList<StatementInfo> stmtInfos, int i,
          StatementInfo info1, MultiMap<Statement, Statement> after,
          HashSet<Statement> mustMove) {
    if (logger.isTraceEnabled())
      logger.trace("Try to move " + info1);
    
    // Find last instruction that writes inputs of inst1
    // Build a DAG of dependences between instructions
    boolean move = mustMove.contains(info1.stmt) || aggressive;
    boolean canMoveFurther = true;
    for (int j = i + 1; j < stmtInfos.size(); j++) {
      StatementInfo info2 = stmtInfos.get(j);
      if (writesInputs(logger, info2, info1, false)) {
        // These edges wont create cycle - forward edge
        after.put(info1.stmt, info2.stmt);
        // We must place inst2 based on dependencies
        mustMove.add(info2.stmt);
        canMoveFurther = false;
        if (!move) {
          // Not going to move
          break;
        }
      }
      
      if (canMoveFurther && 
          writesInputs(logger, info1, info2, true)) {
        // Check that there isn't a path from inst1 to inst2
        if (pathExists(after, info1.stmt, info2.stmt)) {
          canMoveFurther = false;
        } else {
          after.put(info2.stmt, info1.stmt);
          move = true;
        }
      }
    }
    return move;
  }

  private boolean pathExists(MultiMap<Statement, Statement> after,
      Statement from, Statement to) {
    ArrayDeque<Statement> stack = new ArrayDeque<Statement>();
    stack.push(from);
    while (!stack.isEmpty()) {
      Statement curr = stack.pop();
      if (curr == to) {
        return true;
      }
      stack.addAll(after.get(curr));
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
              inst.getInitialized(),
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
      if (declared.mapping() != null) {
        info.inputVars.add(declared.mapping());
      }
    }
    @Override
    protected void visit(Instruction inst) {
      // TODO: don't need to track variables declared in lower scopes
      info.opcodeMap.put(inst.op, inst);
      info.inputVars.addAll(getAllInputs(inst));
      info.outputs.addAll(inst.getOutputs());
      info.modifiedOutputs.addAll(inst.getModifiedOutputs());
      info.initialized.addAll(inst.getInitialized());
      info.piecewiseAssigned.addAll(inst.getPiecewiseAssignedOutputs());
    }
    
    @Override
    protected void visit(CleanupAction cleanup) {
      visit(cleanup.action()); // treat same as instruction
    }
  }
}
