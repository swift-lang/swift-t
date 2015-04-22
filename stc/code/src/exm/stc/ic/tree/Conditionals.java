package exm.stc.ic.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.CompilerBackend;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.ExecTarget;
import exm.stc.common.lang.Var;
import exm.stc.ic.ICUtil;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.GenInfo;
import exm.stc.ic.tree.ICTree.RenameMode;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;

public class Conditionals {
  public static final String indent = ICUtil.indent;

  public static abstract class Conditional extends Continuation
      implements Statement {
    @Override
    public StatementType type() {
      return StatementType.CONDITIONAL;
    }
    @Override
    public Conditional conditional() {
      return this;
    }
    @Override
    public Instruction instruction() {
      throw new STCRuntimeError("Not an instruction");
    }

    @Override
    public ExecTarget target() {
      return ExecTarget.syncAny();
    }
    @Override
    public boolean isLoop() {
      return false;
    }

    @Override
    public boolean isConditional() {
      return true;
    }

    @Override
    public boolean isExhaustiveSyncConditional() {
      return true;
    }

    @Override
    public boolean executesBlockOnce() {
      return false;
    }

    @Override
    public boolean isNoop() {
      for (Block b: getBlocks()) {
        if (!b.isEmpty()) {
          return false;
        }
      }
      return true;
    }

    @Override
    public Statement cloneStatement() {
      return clone();
    }

    @Override
    public abstract Conditional clone();


    /**
     * @return value of argument it is conditional on
     */
    public abstract Arg conditionArg();


    @Override
    public Block tryInline(Set<Var> closedVars, Set<Var> recClosedVars,
                           boolean keepExplicitDependencies) {
      return branchPredict(this.conditionArg());
    }

    /**
     * See if we can predict branch and flatten this to a block
     * @return a block which is the branch that will run
     */
    public abstract Block branchPredict(Arg condition);

    public Block branchPredict() {
      return branchPredict(this.conditionArg());
    }

  }

  public static class IfStatement extends Conditional {
    private final Block thenBlock;
    private final Block elseBlock;
    private Arg condition;

    public IfStatement(Arg condition) {
      this(condition, new Block(BlockType.THEN_BLOCK, null),
                      new Block(BlockType.ELSE_BLOCK, null), true);
    }

    private IfStatement(Arg condition, Block thenBlock, Block elseBlock,
                        boolean emptyBlocks) {
      super();
      assert(thenBlock != null);
      assert(elseBlock != null);
      this.condition = condition;
      this.thenBlock = thenBlock;
      this.thenBlock.setParent(this, emptyBlocks);
      // Always have an else block to make more uniform: empty block is then
      // equivalent to no else block
      this.elseBlock = elseBlock;
      this.elseBlock.setParent(this, emptyBlocks);
    }

    @Override
    public IfStatement clone() {
      return new IfStatement(condition, thenBlock.clone(),
                             elseBlock.clone(), false);
    }

    public Block thenBlock() {
      return thenBlock;
    }

    public Block elseBlock() {
      return elseBlock;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      boolean hasElse = !(elseBlock.isEmpty());
      gen.startIfStatement(condition, hasElse);
      this.thenBlock.generate(logger, gen, info);
      if (hasElse) {
        gen.startElseBlock();
        this.elseBlock.generate(logger, gen, info);
      }
      gen.endIfStatement();
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      String newIndent = currentIndent + indent;
      sb.append(currentIndent + "if (");
      sb.append(this.condition.toString());
      sb.append(") {\n");
      thenBlock.prettyPrint(sb, newIndent);
      if (!elseBlock.isEmpty()) {
        sb.append(currentIndent + "} else {\n");
        elseBlock.prettyPrint(sb, newIndent);
      }
      sb.append(currentIndent + "}\n");
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(thenBlock, elseBlock);
    }

    @Override
    protected void replaceConstructVars(Map<Var, Arg> renames,
                                       RenameMode mode) {
      condition = ICUtil.replaceArg(renames, condition, false);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.IF_STATEMENT;
    }

    @Override
    public Collection<Var> requiredVars(boolean forDeadCodeElim) {
      if (condition.isVar()) {
        return Arrays.asList(condition.getVar());
      } else {
        return Var.NONE;
      }
    }

    @Override
    public void removeVars(Set<Var> removeVars) {
      removeVarsInBlocks(removeVars);
      assert(!condition.isVar() ||
            (!removeVars.contains(condition.getVar())));
    }

    @Override
    public Block branchPredict(Arg condVal) {
      if (condVal.isVar()) {
        return null;
      }

      assert(condVal.isInt() || condVal.isBool());
      if (condVal.isInt() && condVal.getInt() != 0) {
        return thenBlock;
      } else if (condVal.isBool() && condVal.getBool()) {
        return thenBlock;
      } else {
        return elseBlock;
      }
    }

    /**
     * Can these be fused into one if statement
     * @param other
     * @return
     */
    public boolean fuseable(IfStatement other) {
      return this.condition.equals(other.condition);

    }

    /**
     * Fuse other if statement into this
     * @param other
     * @param insertAtTop if true, insert code from other about
     *    code from this in blcoks
     */
    public void fuse(IfStatement other, boolean insertAtTop) {
      thenBlock.insertInline(other.thenBlock, insertAtTop);
      elseBlock.insertInline(other.elseBlock, insertAtTop);

    }

    @Override
    public Arg conditionArg() {
      return condition;
    }
  }

  public static class SwitchStatement extends Conditional {
    private final ArrayList<Integer> caseLabels;
    private final ArrayList<Block> caseBlocks;
    private final Block defaultBlock;
    private Arg switchVar;

    public SwitchStatement(Arg switchVar, List<Integer> caseLabels) {
      this(switchVar, new ArrayList<Integer>(caseLabels),
          new ArrayList<Block>(), new Block(BlockType.CASE_BLOCK, null),
          true);

      // number of non-default cases
      int caseCount = caseLabels.size();
      for (int i = 0; i < caseCount; i++) {
        Block caseBlock = new Block(BlockType.CASE_BLOCK, this);
        this.caseBlocks.add(caseBlock);
        caseBlock.setParent(this, true);
      }
    }

    private SwitchStatement(Arg switchVar,
        ArrayList<Integer> caseLabels, ArrayList<Block> caseBlocks,
        Block defaultBlock, boolean emptyBlocks) {
      super();
      this.switchVar = switchVar;
      this.caseLabels = caseLabels;
      this.caseBlocks = caseBlocks;
      for (Block caseBlock: caseBlocks) {
        caseBlock.setParent(this, emptyBlocks);
      }
      this.defaultBlock = defaultBlock;
      this.defaultBlock.setParent(this, emptyBlocks);
    }

    @Override
    public SwitchStatement clone() {
      return new SwitchStatement(switchVar,
          new ArrayList<Integer>(this.caseLabels),
          ICUtil.cloneBlocks(this.caseBlocks), this.defaultBlock.clone(),
          false);

    }

    public List<Block> caseBlocks() {
      return Collections.unmodifiableList(caseBlocks);
    }

    public Block getDefaultBlock() {
      return this.defaultBlock;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info) {
      boolean hasDefault = !defaultBlock.isEmpty();
      gen.startSwitch(switchVar, caseLabels, hasDefault);

      for (Block b: this.caseBlocks) {
        b.generate(logger, gen, info);
        gen.endCase();
      }

      if (hasDefault) {
        defaultBlock.generate(logger, gen, info);
        gen.endCase();
      }

      gen.endSwitch();
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      assert(this.caseBlocks.size() == this.caseLabels.size());
      String caseIndent = currentIndent + indent;
      String caseBlockIndent = caseIndent + indent;
      sb.append(currentIndent + "switch (" + switchVar.toString() + ") {\n");
      for (int i = 0; i < caseLabels.size(); i++) {
        sb.append(caseIndent + "case " + caseLabels.get(i) + " {\n");
        caseBlocks.get(i).prettyPrint(sb, caseBlockIndent);
        sb.append(caseIndent + "}\n");
      }
      if (!defaultBlock.isEmpty()) {
        sb.append(caseIndent + "default {\n");
        defaultBlock.prettyPrint(sb, caseBlockIndent);
        sb.append(caseIndent + "}\n");
      }
      sb.append(currentIndent + "}\n");
    }

    @Override
    public List<Block> getBlocks() {
      List<Block> result = new ArrayList<Block>();
      result.addAll(this.caseBlocks);
      result.add(defaultBlock);
      return result;
    }

    @Override
    public void replaceConstructVars(Map<Var, Arg> renames,
                                     RenameMode mode) {
      switchVar = ICUtil.replaceArg(renames, switchVar, false);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.SWITCH_STATEMENT;
    }

    @Override
    public Collection<Var> requiredVars(boolean forDeadCodeElim) {
      if (switchVar.isVar()) {
        return Arrays.asList(switchVar.getVar());
      } else {
        return Var.NONE;
      }
    }

    @Override
    public void removeVars(Set<Var> removeVars) {
      assert(!switchVar.isVar()
          || !removeVars.contains(switchVar.getVar()));
      defaultBlock.removeVars(removeVars);
      for (Block caseBlock: this.caseBlocks) {
        caseBlock.removeVars(removeVars);
      }

    }

    @Override
    public Block branchPredict(Arg switchVal) {
      if (switchVal.isVar()) {
        // Variable condition
        return null;
      }
      long val = switchVal.getInt();
      // Check cases
      for (int i = 0; i < caseLabels.size(); i++) {
        if (val == caseLabels.get(i)) {
          return caseBlocks.get(i);
        }
      }
      // Otherwise return (maybe empty) default block
      return defaultBlock;
    }

    @Override
    public Arg conditionArg() {
      return switchVar;
    }
  }
}
