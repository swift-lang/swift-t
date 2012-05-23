package exm.parser.ic;

import java.util.*;

import org.apache.log4j.Logger;

import exm.ast.Builtins.LocalOpcode;
import exm.ast.*;
import exm.ast.Variable.DefType;
import exm.ast.Variable.VariableStorage;
import exm.parser.CompilerBackend;
import exm.parser.ic.ICInstructions.LocalBuiltin;
import exm.parser.ic.ICInstructions.LoopBreak;
import exm.parser.ic.ICInstructions.LoopContinue;
import exm.parser.ic.ICInstructions.Oparg;
import exm.parser.ic.ICInstructions.OpargType;
import exm.parser.ic.SwiftIC.Block;
import exm.parser.ic.SwiftIC.BlockType;
import exm.parser.ic.SwiftIC.GenInfo;
import exm.parser.util.STCRuntimeError;
import exm.parser.util.UndefinedTypeException;

public class ICContinuations {
  public static final String indent = ICUtil.indent;

  public static abstract class Continuation {
    public abstract ContinuationType getType();

    public abstract void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException;

    public abstract void prettyPrint(StringBuilder sb, String currentIndent);


    /** Returns all nested blocks in this continuation */
    public abstract List<Block> getBlocks();


    /**
     * Replace usage of the values of the specified variables throughotu
     * where possible.  It is ok if the usedVariables get messed up a bit
     * here: we will fix them up later
     * @param renames
     */
    public abstract void replaceInputs(Map<String, Oparg> renames);

    public abstract void replaceVars(Map<String, Oparg> renames);
    protected void replaceVarsInBlocks(Map<String, Oparg> renames,
        boolean inputsOnly) {
      for (Block b: this.getBlocks()) {
        HashMap<String, Oparg> shadowed =
                new HashMap<String, Oparg>();

        // See if any of the renamed vars are redeclared,
        //  and if so temporarily remove them from the
        //  rename map
        for (Variable v: b.getVariables()) {
          String vName = v.getName();
          if (renames.containsKey(vName)) {
            shadowed.put(vName, renames.get(vName));
            renames.remove(vName);
          }
        }
        b.renameVars(renames, inputsOnly);

        renames.putAll(shadowed);
      }
    }

    public abstract void removeVars(Set<String> removeVars);

    protected void removeVarsInBlocks(Set<String> removeVars) {
      for (Block b: this.getBlocks()) {
        b.removeVars(removeVars);
      }
    }

    /**
     * @return all variables whose values are needed to evaluate this construct
     * (e.g. branch condition).  empty list if none
     */
    public abstract Collection<Variable> requiredVars();

    /**
     * See if we can predict branch and flatten this to a block
     * @param knownConstants
     * @return a block which is the branch that will run
     */
    public abstract Block branchPredict(Map<String, Oparg> knownConstants);

    /**
     * replace variables with constants in loop construct
     * @param knownConstants
     * @return true if anything changed
     */
    public boolean constantReplace(Map<String, Oparg> knownConstants) {
      // default: do nothing
      return false;
    }

    /** @return true if the continuation does nothing */
    public abstract boolean isNoop();

    /** Return list of variables that the continuations waits for
     * before executing
     * @return
     */
    public abstract List<Variable> blockingVars();

    /**
     * Return list of variables that are defined by construct and
     * accessible inside
     * @return
     */
    public abstract List<Variable> constructDefinedVars();

    /**
     * @return true if all variables in block containing continuation are
     *        automatically visible in inner blocks
     */
    public abstract boolean variablesPassedInAutomatically();

    public abstract Collection<Variable> getPassedInVars();

    public abstract void addPassedInVar(Variable variable);

    public abstract void removePassedInVar(Variable variable);

    /**
     * Remove this continuation from block, inlining one of
     * the nested blocks inside the continuation (e.g. the predicted branch
     *  of an if statement)
     * @param b
     */
    public void inlineInto(Block block, Block predictedBranch) {
      // Default implementation
      block.insertInline(predictedBranch);
      block.removeContinuation(this);
    }

    /**
     * Returns true if a change was made.
     *
     * It is ok if the unrolling introduced duplicate variable names in
     * nested blocks (so long as they don't shadow each other) - a
     * subsequent pass will make those names unique
     * @param logger
     * @param outerBlock
     * @return
     */
    public boolean tryUnroll(Logger logger, Block outerBlock) {
      // default: do nothing
      return false;
    }

    /**
     * Try to inline a block, depending on which variables are closed
     * This is also a mechanism to let the continuation know what variables
     * are closed so it can make internal optimizations
     * @param closedVars
     * @return null if it cannot be inlined, a block that is equivalent to
     *          the continuation otherwise
     */
    public Block tryInline(Set<String> closedVars) {
      // Default: do nothing
      return null;
    }

    @Override
    public abstract Continuation clone();
  }

  public enum ContinuationType {
    NESTED_BLOCK,
    IF_STATEMENT,
    SWITCH_STATEMENT,
    FOREACH_LOOP,
    RANGE_LOOP,
    LOOP,
    WAIT_STATEMENT
  }

  public static abstract class AbstractLoop extends Continuation {
    protected Block loopBody;

    protected final List<Variable> usedVariables;
    protected final List<Variable> containersToRegister;

    public AbstractLoop(Block block, List<Variable> usedVariables,
        List<Variable> containersToRegister) {

      this.usedVariables = new ArrayList<Variable>(usedVariables);
      this.containersToRegister =
                      new ArrayList<Variable>(containersToRegister);
      this.loopBody = block;
    }

    public Block getLoopBody() {
      return loopBody;
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(loopBody);
    }

    @Override
    public Collection<Variable> requiredVars() {
      ArrayList<Variable> res = new ArrayList<Variable>();
      for (Variable c: containersToRegister) {
        if (c.getStorage() == VariableStorage.ALIAS) {
          // We might be holding it open so that a different var can
          // be written inside
        }
      }
      return res;
    }

    protected void checkNotRemoved(Variable v, Set<String> removeVars) {
      if (removeVars.contains(v.getName())) {
        throw new STCRuntimeError("bad optimization: tried to remove" +
        		" required variable " + v.toString());
      }
    }
    protected void checkNotRemoved(Oparg o, Set<String> removeVars) {
      if (o.type == OpargType.VAR) {
        checkNotRemoved(o.getVar(), removeVars);
      }
    }

    @Override
    public Collection<Variable> getPassedInVars() {
      return Collections.unmodifiableList(this.usedVariables);
    }

    @Override
    public void addPassedInVar(Variable variable) {
      assert(variable != null);
      this.usedVariables.add(variable);
    }

    @Override
    public void removePassedInVar(Variable variable) {
      ICUtil.removeVarInList(usedVariables, variable.getName());
    }

    @Override
    public void inlineInto(Block block, Block predictedBranch) {
      throw new STCRuntimeError("Can't inline loops yet");
    }

    protected void fuseIntoAbstract(AbstractLoop o, boolean insertAtTop) {
      this.loopBody.insertInline(o.loopBody, insertAtTop);
      this.containersToRegister.addAll(o.containersToRegister);
      ICUtil.removeDuplicates(this.containersToRegister);
      this.usedVariables.addAll(o.usedVariables);
      ICUtil.removeDuplicates(this.usedVariables);
    }
  }

  public static class ForeachLoop extends AbstractLoop {
    private Variable arrayVar;
    private boolean arrayClosed;
    private Variable loopCounterVar;
    private Variable loopVar;
    private final boolean isSync;
    public Variable getArrayVar() {
      return arrayVar;
    }

    private final int splitDegree;

    private ForeachLoop(Block block, Variable arrayVar, Variable loopVar,
        Variable loopCounterVar, boolean isSync, int splitDegree,
        boolean arrayClosed,
        List<Variable> usedVariables, List<Variable> containersToRegister) {
      super(block, usedVariables, containersToRegister);
      this.arrayVar = arrayVar;
      this.loopVar = loopVar;
      this.loopCounterVar = loopCounterVar;
      this.isSync = isSync;
      this.arrayClosed = arrayClosed;
      this.splitDegree = splitDegree;
    }

    public ForeachLoop(Variable arrayVar, Variable loopVar,
        Variable loopCounterVar, boolean isSync, int splitDegree,
        boolean arrayClosed, List<Variable> usedVariables,
        List<Variable> containersToRegister) {
      this(new Block(BlockType.FOREACH_BODY), arrayVar, loopVar, loopCounterVar,
          isSync, splitDegree, arrayClosed, usedVariables, containersToRegister);
    }

    @Override
    public ForeachLoop clone() {
      return new ForeachLoop(this.loopBody.clone(),
          arrayVar, loopVar, loopCounterVar, isSync, splitDegree, arrayClosed,
          new ArrayList<Variable>(usedVariables),
          new ArrayList<Variable>(containersToRegister));
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.FOREACH_LOOP;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startForeachLoop(arrayVar, loopVar, loopCounterVar, isSync,
                splitDegree, arrayClosed, usedVariables, containersToRegister);
      this.loopBody.generate(logger, gen, info);
      gen.endForeachLoop(isSync, splitDegree, arrayClosed,
                                          containersToRegister);
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      if (isSync) {
        sb.append(currentIndent + "@sync\n");
      }
      if (arrayClosed) {
        sb.append(currentIndent + "@skiparrayblock\n");
      }
      sb.append(currentIndent + "foreach " + loopVar.getName());
      if (loopCounterVar != null) {
        sb.append(", " + loopCounterVar.getName());
      }
      sb.append(" in " + arrayVar.getName() + " ");
      ICUtil.prettyPrintVarInfo(sb, usedVariables, containersToRegister);
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceVars(Map<String, Oparg> renames) {
      this.replaceVarsInBlocks(renames, false);
      if (renames.containsKey(arrayVar.getName())) {
        arrayVar = renames.get(arrayVar.getName()).getVar();
      }
      if (renames.containsKey(loopVar.getName())) {
        loopVar = renames.get(loopVar.getName()).getVar();
      }
      if (this.loopCounterVar != null &&
          renames.containsKey(loopCounterVar.getName())) {
        loopCounterVar = renames.get(loopCounterVar.getName()).getVar();
      }
      ICUtil.replaceVarsInList(renames, usedVariables, true);
      ICUtil.replaceVarsInList(renames, containersToRegister, true);
    }

    @Override
    public void replaceInputs(Map<String, Oparg> renames) {
      // Replace only those we're reading
      this.replaceVarsInBlocks(renames, true);
      if (renames.containsKey(arrayVar.getName())) {
        arrayVar = renames.get(arrayVar.getName()).getVar();
      }
      ICUtil.replaceVarsInList(renames, usedVariables, true);
    }

    @Override
    public Collection<Variable> requiredVars() {
      Collection<Variable> res = super.requiredVars();
      res.add(arrayVar);
      return res;
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      removeVarsInBlocks(removeVars);
      checkNotRemoved(arrayVar, removeVars);
      checkNotRemoved(loopVar, removeVars);
      if (loopCounterVar != null) {
        checkNotRemoved(loopCounterVar, removeVars);
      }
      ICUtil.removeVarsInList(this.containersToRegister, removeVars);
      ICUtil.removeVarsInList(this.usedVariables, removeVars);
    }

    @Override
    public Block branchPredict(Map<String, Oparg> knownConstants) {
      return null;
    }

    @Override
    public boolean isNoop() {
      return this.loopBody.isEmpty();
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return false;
    }

    @Override
    public List<Variable> constructDefinedVars() {
      return loopCounterVar == null ?
                Arrays.asList(loopVar)
              : Arrays.asList(loopCounterVar, loopVar);
    }

    @Override
    public List<Variable> blockingVars() {
      return null;
    }

    @Override
    public Block tryInline(Set<String> closedVars) {
      if (closedVars.contains(arrayVar.getName())) {
        this.arrayClosed = true;
      }
      return null;
    }

    public Variable getLoopVar() {
      return loopVar;
    }

    public boolean fuseable(ForeachLoop o) {
      // annotation parameters should match to respect any
      // user settings
      return this.arrayVar.getName().equals(o.arrayVar.getName())
          && this.isSync == o.isSync
          && this.splitDegree == o.splitDegree;
    }

    public void fuseInto(ForeachLoop o, boolean insertAtTop) {
      Map<String, Oparg> renames = new HashMap<String, Oparg>();
      renames.put(o.loopVar.getName(), Oparg.createVar(this.loopVar));
      // Handle optional loop counter var
      if (o.loopCounterVar != null) {
        if (this.loopCounterVar != null) {
          renames.put(o.loopCounterVar.getName(),
                      Oparg.createVar(this.loopCounterVar));    
        } else {
          this.loopCounterVar = o.loopCounterVar;
        }
      }
      o.replaceVars(renames);
      
      fuseIntoAbstract(o, insertAtTop);
    }

  }

  public static class IfStatement extends Continuation {
    private final Block thenBlock;
    private final Block elseBlock;
    private Oparg condition;

    public IfStatement(Oparg condition) {
      this(condition, new Block(BlockType.THEN_BLOCK),
                          new Block(BlockType.ELSE_BLOCK));
    }

    private IfStatement(Oparg condition, Block thenBlock, Block elseBlock) {
      assert(thenBlock != null);
      assert(elseBlock != null);
      this.condition = condition;
      this.thenBlock = thenBlock;
      // Always have an else block to make more uniform: empty block is then
      // equivalent to no else block
      this.elseBlock = elseBlock;
    }

    @Override
    public IfStatement clone() {
      return new IfStatement(condition, thenBlock.clone(), elseBlock.clone());
    }

    public Block getThenBlock() {
      return thenBlock;
    }

    public Block getElseBlock() {
      return elseBlock;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
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
    public void replaceVars(Map<String, Oparg> renames) {
      replaceShared(renames, false);
    }

    @Override
    public void replaceInputs(Map<String, Oparg> renames) {
      replaceShared(renames, true);
    }

    private void replaceShared(Map<String, Oparg> renames,
          boolean inputsOnly) {
      replaceVarsInBlocks(renames, inputsOnly);
      condition = ICUtil.replaceOparg(renames, condition, false);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.IF_STATEMENT;
    }

    @Override
    public Collection<Variable> requiredVars() {
      if (condition.getType() == OpargType.VAR) {
        return Arrays.asList(condition.getVar());
      } else {
        return Collections.emptyList();
      }
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      removeVarsInBlocks(removeVars);
      assert(condition.getType() != OpargType.VAR ||
            (!removeVars.contains(condition.getVar().getName())));
    }

    @Override
    public Block branchPredict(Map<String, Oparg> knownConstants) {
      Oparg val;
      
      if (condition.getType() == OpargType.VAR) {
        val = knownConstants.get(condition.getVar().getName());
        if (val == null) {
          return null;
        }
      } else {
       val = condition; 
      }
      
      assert(val.getType() == OpargType.INTVAL
            || val.getType() == OpargType.BOOLVAL);
      if (val.getType() == OpargType.INTVAL
          && val.getIntLit() != 0) {
        return thenBlock;
      } else if (val.getType() == OpargType.BOOLVAL &&
          val.getBoolLit()) {
        return thenBlock;
      } else {
        return elseBlock;
      }
    }

    @Override
    public boolean isNoop() {
      return thenBlock.isEmpty() && elseBlock.isEmpty();
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return true;
    }

    @Override
    public Collection<Variable> getPassedInVars() {
      // doesn't apply
      return null;
    }

    @Override
    public void addPassedInVar(Variable variable) {
      throw new STCRuntimeError("addPassedInVar not supported on if");
    }

    @Override
    public void removePassedInVar(Variable variable) {
      throw new STCRuntimeError("removePassedInVar not supported on " +
      		"if");
    }

    @Override
    public List<Variable> constructDefinedVars() {
      return null;
    }

    @Override
    public List<Variable> blockingVars() {
      return null;
    }

    public Oparg getCondition() {
      return condition;
    }
  }

  public static class Loop extends AbstractLoop {
    private final String loopName;
    private final Variable condVar;
    private final List<Variable> loopVars;
    private final List<Variable> initVals;

    /*
     * Have handles to the termination instructions
     */
    // private LoopBreak loopBreak;

    private LoopContinue loopContinue;
    private final ArrayList<Boolean> blockingVars;


    public Loop(String loopName, List<Variable> loopVars,
            List<Variable> initVals, List<Variable> usedVariables,
            List<Variable> containersToRegister, List<Boolean> blockingVars) {
      this(loopName, new Block(BlockType.LOOP_BODY), loopVars, initVals,
          usedVariables, containersToRegister, blockingVars);
    }

    private Loop(String loopName, Block loopBody,
        List<Variable> loopVars,  List<Variable> initVals,
        List<Variable> usedVariables, List<Variable> containersToRegister,
        List<Boolean> blockingVars) {
      super(loopBody, usedVariables, containersToRegister);
      this.loopName = loopName;
      this.condVar = loopVars.get(0);
      this.loopVars = new ArrayList<Variable>(loopVars);
      this.initVals = new ArrayList<Variable>(initVals);
      this.blockingVars = new ArrayList<Boolean>(blockingVars);
      assert(loopVars.size() == initVals.size());
      for (int i = 0; i < loopVars.size(); i++) {
        Variable loopV = loopVars.get(i);
        Variable initV = initVals.get(i);
        if (!loopV.getType().equals(initV.getType())) {
          throw new STCRuntimeError("loop variable " + loopV.toString()
              + " is given init value of wrong type: " + initV.toString());
        }
      }
    }

    @Override
    public Loop clone() {
      // Constructor creates copies of variable lists
      return new Loop(loopName, this.loopBody.clone(), loopVars, initVals,
          usedVariables, containersToRegister, blockingVars);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.LOOP;
    }

    public void setLoopBreak(LoopBreak loopBreak) {
      // this.loopBreak = loopBreak;
    }

    public void setLoopContinue(LoopContinue loopContinue) {
      this.loopContinue = loopContinue;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startLoop(loopName, loopVars, initVals,
                    usedVariables, containersToRegister, blockingVars);
      this.loopBody.generate(logger, gen, info);
      gen.endLoop();
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      sb.append(currentIndent + "loop /*" + loopName + "*/\n");
      sb.append(currentIndent + indent + indent + "while (");
      sb.append(condVar.getType().typeName() + " " + condVar.getName());
      sb.append(")\n" + currentIndent + indent + indent + "loopvars (");
      boolean first = true;
      for (int i = 0; i < loopVars.size(); i++) {
        Variable loopV = loopVars.get(i);
        Variable initV = initVals.get(i);
        if (first) {
          first = false;
        } else {
          sb.append(", ");
        }
        sb.append(loopV.getType().typeName() + " " + loopV.getName() + "="
            + initV.getName());
      }

      sb.append(")\n" + currentIndent + indent + indent);
      ICUtil.prettyPrintVarInfo(sb, usedVariables, containersToRegister);
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceVars(Map<String, Oparg> renames) {
      this.replaceVarsInBlocks(renames, false);
      ICUtil.replaceVarsInList(renames, initVals, false);
      ICUtil.replaceVarsInList(renames, usedVariables, true);
      ICUtil.replaceVarsInList(renames, containersToRegister, true);
    }

    @Override
    public void replaceInputs(Map<String, Oparg> renames) {
      this.replaceVarsInBlocks(renames, true);
      ICUtil.replaceVarsInList(renames, initVals, false);
    }

    @Override
    public Collection<Variable> requiredVars() {
      Collection<Variable> res = super.requiredVars();
      res.addAll(initVals);
      return res;
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      removeVarsInBlocks(removeVars);
      // check it isn't removing initial values
      for (Variable v: this.initVals) {
        checkNotRemoved(v, removeVars);
      }
      for (Variable v: this.loopVars) {
        checkNotRemoved(v, removeVars);
      }
      ICUtil.removeVarsInList(this.containersToRegister, removeVars);
      ICUtil.removeVarsInList(this.usedVariables, removeVars);
    }

    @Override
    public Block branchPredict(Map<String, Oparg> knownConstants) {
      return null;
    }

    @Override
    public boolean isNoop() {
      // TODO: think about particular conditions that would render it a noop.
      //
      return false;
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return false;
    }

    @Override
    public List<Variable> constructDefinedVars() {
      ArrayList<Variable> defVars = new ArrayList<Variable>(
                                      loopVars.size() + 1);
      defVars.addAll(this.loopVars);
      defVars.add(this.condVar);
      return defVars;
    }

    @Override
    public List<Variable> blockingVars() {
      ArrayList<Variable> res = new ArrayList<Variable>();
      res.add(condVar);
      for (int i = 0; i < loopVars.size(); i++) {
        if (blockingVars.get(i)) {
          res.add(loopVars.get(i));
        }
      }
      return res;
    }


    @Override
    public void addPassedInVar(Variable variable) {
      // special implementation to also fix up the loopContinue instruction
      assert(variable != null);
      super.addPassedInVar(variable);
      this.loopContinue.addUsedVar(variable);
    }

    @Override
    public void removePassedInVar(Variable variable) {
      // special implementation to also fix up the loopContinue instruction
      super.removePassedInVar(variable);
      this.loopContinue.removeUsedVar(variable);
    }

    public Variable getInitCond() {
      return this.initVals.get(0);
    }
  }

  /**
   * For now, treat nested blocks as continuations.  If we implement a
   * proper variable renaming scheme for nested blocks we can just
   * flatten them into the main block
   */
  static class NestedBlock extends Continuation {
    private final Block block;

    public NestedBlock() {
      this(new Block(BlockType.NESTED_BLOCK));
    }


    private NestedBlock(Block block) {
      this.block = block;
    }

    @Override
    public NestedBlock clone() {
      return new NestedBlock(this.block.clone());
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startNestedBlock();
      block.generate(logger, gen, info);
      gen.endNestedBlock();
    }

    public Block getBlock() {
      return this.block;
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      sb.append(currentIndent + "{\n");
      block.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(block);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.NESTED_BLOCK;
    }

    @Override
    public void replaceVars(Map<String, Oparg> renames) {
      replaceVarsInBlocks(renames, false);
    }

    @Override
    public void replaceInputs(Map<String, Oparg> renames) {
      replaceVarsInBlocks(renames, true);
    }

    @Override
    public Collection<Variable> requiredVars() {
      return new ArrayList<Variable>(0);
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      removeVarsInBlocks(removeVars);
    }

    @Override
    public Block branchPredict(Map<String, Oparg> knownConstants) {
      return null;
    }

    @Override
    public boolean isNoop() {
      return block.isEmpty();
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return true;
    }

    @Override
    public Collection<Variable> getPassedInVars() {
      return null;
    }

    @Override
    public void addPassedInVar(Variable variable) {
      throw new STCRuntimeError("addPassedInVar not supported on " +
      		"nested");
    }

    @Override
    public void removePassedInVar(Variable variable) {
      throw new STCRuntimeError("removePassedInVar not supported on " +
          "nested block");
    }

    @Override
    public List<Variable> constructDefinedVars() {
      return null;
    }

    @Override
    public List<Variable> blockingVars() {
      return null;
    }
  }

  public static class RangeLoop extends AbstractLoop {
    // arguments can be either value variable or integer literal
    private final String loopName;
    private Variable loopVar;
    private Oparg start;
    public int getDesiredUnroll() {
      return desiredUnroll;
    }

    public void setDesiredUnroll(int desiredUnroll) {
      this.desiredUnroll = desiredUnroll;
    }

    public Variable getLoopVar() {
      return loopVar;
    }

    public boolean isSync() {
      return isSync;
    }

    public int getSplitDegree() {
      return splitDegree;
    }

    private Oparg end;
    private Oparg increment;
    private final boolean isSync;
    private int desiredUnroll;
    private final int splitDegree;

    public RangeLoop(String loopName, Variable loopVar,
        Oparg start, Oparg end, Oparg increment,
        boolean isSync,
        List<Variable> usedVariables, List<Variable> containersToRegister,
        int desiredUnroll, int splitDegree) {
      this(loopName, new Block(BlockType.RANGELOOP_BODY), loopVar,
          start, end, increment, isSync, usedVariables, containersToRegister,
          desiredUnroll, splitDegree);
    }

    private RangeLoop(String loopName, Block block, Variable loopVar,
        Oparg start, Oparg end, Oparg increment,
        boolean isSync,
        List<Variable> usedVariables, List<Variable> containersToRegister,
        int desiredUnroll, int splitDegree) {
      super(block,
            usedVariables, containersToRegister);
      assert(start.getType() == OpargType.INTVAL ||
          (start.getType() == OpargType.VAR &&
              start.getVar().getType().equals(Types.VALUE_INTEGER)));
      assert(end.getType() == OpargType.INTVAL ||
          (end.getType() == OpargType.VAR &&
              end.getVar().getType().equals(Types.VALUE_INTEGER)));
      assert(increment.getType() == OpargType.INTVAL ||
          (increment.getType() == OpargType.VAR &&
                      increment.getVar().getType().equals(Types.VALUE_INTEGER)));
      assert(loopVar.getType().equals(Types.VALUE_INTEGER));
      this.loopName = loopName;
      this.loopVar = loopVar;
      this.start = start;
      this.end = end;
      this.increment = increment;
      this.isSync = isSync;
      this.desiredUnroll = desiredUnroll;
      this.splitDegree = splitDegree;
    }

    @Override
    public RangeLoop clone() {
      return new RangeLoop(loopName, this.loopBody.clone(), loopVar,
          start.clone(), end.clone(), increment.clone(), isSync,
          new ArrayList<Variable>(usedVariables),
          new ArrayList<Variable>(containersToRegister), desiredUnroll,
          splitDegree);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.RANGE_LOOP;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
      gen.startRangeLoop(loopName, loopVar, start, end, increment,
                         isSync,
                         usedVariables, containersToRegister,
                         desiredUnroll, splitDegree);
      this.loopBody.generate(logger, gen, info);
      gen.endRangeLoop(isSync, containersToRegister, splitDegree);
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      if (isSync) {
        sb.append(currentIndent + "@sync\n");
      }
      sb.append(currentIndent +   "for " + loopVar.getName());

      sb.append(" = " + start.toString() + " to " + end.toString() + " ");

      if (increment.getType() != OpargType.INTVAL ||
            increment.getIntLit() != 1) {
          sb.append("incr " + increment.toString() + " ");
      }
      ICUtil.prettyPrintVarInfo(sb, usedVariables, containersToRegister);
      sb.append(" {\n");
      loopBody.prettyPrint(sb, currentIndent + indent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public void replaceVars(Map<String, Oparg> renames) {
      this.replaceVarsInBlocks(renames, false);
      if (renames.containsKey(loopVar.getName())) {
        loopVar = renames.get(loopVar.getName()).getVar();
      }
      start = renameRangeArg(start, renames);
      end = renameRangeArg(end, renames);
      increment = renameRangeArg(increment, renames);

      ICUtil.replaceVarsInList(renames, usedVariables, true);
      ICUtil.replaceVarsInList(renames, containersToRegister, true);
    }
    @Override
    public void replaceInputs(Map<String, Oparg> renames) {
      this.replaceVarsInBlocks(renames, true);
      start = renameRangeArg(start, renames);
      end = renameRangeArg(end, renames);
      increment = renameRangeArg(increment, renames);
    }

    private Oparg renameRangeArg(Oparg val, Map<String, Oparg> renames) {
      if (val.type == OpargType.VAR) {
        String vName = val.getVar().getName();
        if (renames.containsKey(vName)) {
          Oparg o = renames.get(vName);
          assert(o != null);
          return o;
        }
      }
      return val;
    }

    @Override
    public Collection<Variable> requiredVars() {
      Collection<Variable> res = super.requiredVars();
      for (Oparg o: Arrays.asList(start, end, increment)) {
        if (o.getType() == OpargType.VAR) {
          res.add(o.getVar());
        }
      }
      return res;
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      checkNotRemoved(start, removeVars);
      checkNotRemoved(end, removeVars);
      checkNotRemoved(increment, removeVars);
      removeVarsInBlocks(removeVars);
      ICUtil.removeVarsInList(this.containersToRegister, removeVars);
      ICUtil.removeVarsInList(this.usedVariables, removeVars);
    }

    @Override
    public Block branchPredict(Map<String, Oparg> knownConstants) {
      // Could inline loop if there is only one iteration...
      if (start.getType() == OpargType.INTVAL && end.getType() == OpargType.INTVAL) {
        long startV = start.getIntLit();
        long endV = end.getIntLit();
        boolean singleIter = false;
        if (endV < startV) {
          // Doesn't run - return empty block
          return new Block(BlockType.FOREACH_BODY);
        } else if (endV == startV) {
          singleIter = true;
        } else if (increment.getType() == OpargType.INTVAL) {
          long incV = increment.getIntLit();
          if (startV + incV > endV) {
            singleIter = true;
          }
        }

        if (singleIter) {
          return this.loopBody;
        }
      }
      return null;
    }

    @Override
    public void inlineInto(Block block, Block predictedBranch) {
      assert(predictedBranch == this.loopBody);
      // Shift loop variable to body and inline loop body
      this.loopBody.declareVariable(loopVar);
      this.loopBody.addInstructionFront(
          new LocalBuiltin(LocalOpcode.COPY_INT, this.loopVar, start));
      block.insertInline(loopBody);
      block.removeContinuation(this);
    }

    @Override
    public boolean constantReplace(Map<String, Oparg> knownConstants) {
      boolean anyChanged = false;
      Oparg oldVals[] = new Oparg[] {start, end, increment };
      Oparg newVals[] = new Oparg[3];
      for (int i = 0; i < oldVals.length; i++) {
        Oparg old = oldVals[i];
        if (old.type == OpargType.VAR) {
          Oparg replacement = knownConstants.get(old.getVar().getName());
          if (replacement != null) {
            assert(replacement.getType() == OpargType.INTVAL);
            anyChanged = true;
            newVals[i] = replacement;
          } else {
            newVals[i] = old;
          }
        } else {
          newVals[i] = old;
        }
      }
      start = newVals[0];
      end = newVals[1];
      increment = newVals[2];

      assert(start != null); assert(end != null); assert(increment  != null);
      return anyChanged;
    }

    @Override
    public boolean isNoop() {
      return this.loopBody.isEmpty();
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return false;
    }

    @Override
    public List<Variable> constructDefinedVars() {
      return Arrays.asList(loopVar);
    }

    @Override
    public List<Variable> blockingVars() {
      return null;
    }

    @Override
    public boolean tryUnroll(Logger logger, Block outerBlock) {
      logger.trace("DesiredUnroll for " + loopName + ": " + desiredUnroll);
      if (this.desiredUnroll > 1) {
        logger.debug("Unrolling range loop " + desiredUnroll + " times ");
        Oparg oldStep = this.increment;

        long checkIter; // the time we need to check
        if(increment.getType() == OpargType.INTVAL &&
            start.getType() == OpargType.INTVAL &&
            end.getType() == OpargType.INTVAL) {
          long startV = start.getIntLit();
          long endV = end.getIntLit();
          long incV = increment.getIntLit();

          long diff = (endV - startV + 1);
          // Number of loop iterations
          long iters = ( (diff - 1) / incV ) + 1;

          // 0 if the number of iterations will go exactly into the
          // unroll factor
          long extra = iters % desiredUnroll;

          if (extra == 0) {
            checkIter = desiredUnroll;
          } else {
            checkIter = extra;
          }
        } else {
          checkIter = -1;
        }

        // Update step
        if (oldStep.getType() == OpargType.INTVAL) {
          this.increment = Oparg.createIntLit(oldStep.getIntLit() * desiredUnroll);
        } else {
          Variable old = oldStep.getVar();
          Variable newIncrement = new Variable(old.getType(),
              old.getName() + "@unroll" + desiredUnroll,
              VariableStorage.LOCAL,
              DefType.LOCAL_COMPILER, null);
          outerBlock.declareVariable(newIncrement);
          outerBlock.addInstruction(new LocalBuiltin(LocalOpcode.MULT_INT,
              newIncrement, Arrays.asList(oldStep, Oparg.createIntLit(desiredUnroll))));

          this.increment = Oparg.createVar(newIncrement);
        }

        // Create a copy of the original loop body for reference
        Block orig = loopBody;
        this.loopBody = new Block(BlockType.LOOP_BODY);
        Block curr = loopBody;
        Variable nextIter = loopVar; // Variable with current iter number

        for (int i = 0; i < desiredUnroll; i++) {
          // Put everything in nested block
          NestedBlock nb = new NestedBlock(orig.clone(BlockType.NESTED_BLOCK));
          curr.addContinuation(nb);
          if (i != 0) {
            // Replace references to the iteration counter
            nb.replaceVars(Collections.singletonMap(this.loopVar.getName(),
                                            Oparg.createVar(nextIter)));
          }

          if (i < desiredUnroll - 1) {
            // Next iteration number and boolean check
            Variable lastIter = nextIter;
            nextIter = new Variable(Types.VALUE_INTEGER,
                this.loopVar.getName() + "@" + (i + 1), VariableStorage.LOCAL,
                DefType.LOCAL_COMPILER, null);

            curr.addVariable(nextIter);
            // Loop counter
            curr.addInstruction(new LocalBuiltin(LocalOpcode.PLUS_INT,
                nextIter, Arrays.asList(Oparg.createVar(lastIter),
                                        oldStep)));

            boolean mustCheck = checkIter < 0 || i + 1 == checkIter;
            if (mustCheck) {
              Variable nextIterCheck = new Variable(Types.VALUE_BOOLEAN,
                  this.loopVar.getName() + "@" + (i + 1) + "_check",
                  VariableStorage.LOCAL, DefType.LOCAL_COMPILER, null);
              curr.addVariable(nextIterCheck);
              curr.addInstruction(new LocalBuiltin(LocalOpcode.LTE_INT,
                  nextIterCheck, Arrays.asList(Oparg.createVar(nextIter),
                                          this.end)));
              // check to see if we should run next iteration
              IfStatement ifSt = new IfStatement(Oparg.createVar(
                                                        nextIterCheck));
              curr.addContinuation(ifSt);

              curr = ifSt.getThenBlock();
            }
          } else {
            curr = null;
          }
        }
        this.desiredUnroll = 1;
        return true;
      }
      return false;
    }

    public Oparg getStart() {
      return start;
    }

    public Oparg getEnd() {
      return end;
    }

    public Oparg getIncrement() {
      return increment;
    }
    
    public boolean fuseable(RangeLoop o) {
      // Make sure loop bounds line up, but also annotations since we
      // want to respect any user annotations
      return this.start.equals(o.start)
          && this.increment.equals(o.increment)
          && this.end.equals(o.end)
          && this.desiredUnroll == o.desiredUnroll
          && this.splitDegree == o.splitDegree
          && this.isSync == o.isSync;
    }
    
    /**
     * Fuse the other loop into this loop
     */
    public void fuseInto(RangeLoop o, boolean insertAtTop) {
      Map<String, Oparg> renames = new HashMap<String, Oparg>();
      // Update loop var in other loop
      renames.put(o.loopVar.getName(),
                  Oparg.createVar(this.loopVar));
      o.replaceVars(renames);
     
      this.fuseIntoAbstract(o, insertAtTop);
    }
  }

  static class SwitchStatement extends Continuation {
    private final ArrayList<Integer> caseLabels;
    private final ArrayList<Block> caseBlocks;
    private final Block defaultBlock;
    private Oparg switchVar;

    public SwitchStatement(Oparg switchVar, List<Integer> caseLabels) {
      this(switchVar, new ArrayList<Integer>(caseLabels),
          new ArrayList<Block>(), new Block(BlockType.CASE_BLOCK));

      // number of non-default cases
      int caseCount = caseLabels.size();
      for (int i = 0; i < caseCount; i++) {
        this.caseBlocks.add(new Block(BlockType.CASE_BLOCK));
      }
    }

    private SwitchStatement(Oparg switchVar, ArrayList<Integer> caseLabels,
        ArrayList<Block> caseBlocks, Block defaultBlock) {
      super();
      this.switchVar = switchVar;
      this.caseLabels = caseLabels;
      this.caseBlocks = caseBlocks;
      this.defaultBlock = defaultBlock;
    }

    @Override
    public SwitchStatement clone() {
      return new SwitchStatement(switchVar,
          new ArrayList<Integer>(this.caseLabels),
          ICUtil.cloneBlocks(this.caseBlocks), this.defaultBlock.clone());

    }

    public List<Block> caseBlocks() {
      return Collections.unmodifiableList(caseBlocks);
    }

    public Block getDefaultBlock() {
      return this.defaultBlock;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {
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
    public void replaceVars(Map<String, Oparg> renames) {
      replaceVarsInBlocks(renames, false);
      switchVar = ICUtil.replaceOparg(renames, switchVar, false);
    }

    @Override
    public void replaceInputs(Map<String, Oparg> renames) {
      replaceVarsInBlocks(renames, true);
      switchVar = ICUtil.replaceOparg(renames, switchVar, false);
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.SWITCH_STATEMENT;
    }

    @Override
    public Collection<Variable> requiredVars() {
      if (switchVar.getType() == OpargType.VAR) {
        return Arrays.asList(switchVar.getVar());
      } else {
        return Collections.emptyList();
      }
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      assert(switchVar.getType() != OpargType.VAR 
          || !removeVars.contains(switchVar.getVar().getName()));
      defaultBlock.removeVars(removeVars);
      for (Block caseBlock: this.caseBlocks) {
        caseBlock.removeVars(removeVars);
      }

    }

    @Override
    public Block branchPredict(Map<String, Oparg> knownConstants) {
      long val;
      if (switchVar.getType() == OpargType.VAR) {
        Oparg switchVal = knownConstants.get(switchVar.getVar().getName());
        if (switchVal == null) {
          return null;
        }
        assert(switchVal.getType() == OpargType.INTVAL);
        val = switchVal.getIntLit();
      } else {
        val = switchVar.getIntLit();
      }
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
    public boolean isNoop() {
      for (Block b: caseBlocks) {
        if (!b.isEmpty()) {
          return false;
        }
      }
      return this.defaultBlock.isEmpty();
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return true;
    }

    @Override
    public Collection<Variable> getPassedInVars() {
      return null;
    }

    @Override
    public void addPassedInVar(Variable variable) {
      throw new STCRuntimeError("addPassedInVar not supported on switch");
    }

    @Override
    public void removePassedInVar(Variable variable) {
      throw new STCRuntimeError("removePassedInVar not supported on " +
          "switch");
    }

    @Override
    public List<Variable> constructDefinedVars() {
      return null;
    }

    @Override
    public List<Variable> blockingVars() {
      return null;
    }
  }

  /**
   * A new construct that blocks on a list of variables (blockVars),
   * and only runs the contents once all of those variables are closed
   *
   */
  public static class WaitStatement extends Continuation {
    /** Label name to use in final generated code */
    private final String procName;
    private final Block block;
    private final ArrayList<Variable> waitVars;
    private final ArrayList<Variable> usedVariables;
    private final ArrayList<Variable> containersToRegister;
    /* True if this wait was compiler-generated so can be removed if needed
     * We can only remove an explicit wait if we know that the variables are
     * already closed*/
    private final boolean explicit;

    public WaitStatement(String procName, List<Variable> waitVars,
                    List<Variable> usedVariables,
                    List<Variable> containersToRegister,
                    boolean explicit) {
      this(procName, new Block(BlockType.WAIT_BLOCK),
                        new ArrayList<Variable>(waitVars),
                        new ArrayList<Variable>(usedVariables),
                        new ArrayList<Variable>(containersToRegister),
                        explicit);
    }

    private WaitStatement(String procName, Block block,
        ArrayList<Variable> waitVars, ArrayList<Variable> usedVariables,
        ArrayList<Variable> containersToRegister, boolean explicit) {
      super();
      this.procName = procName;
      this.block = block;
      this.waitVars = waitVars;
      ICUtil.removeDuplicates(waitVars);
      this.usedVariables = usedVariables;
      this.containersToRegister = containersToRegister;
      this.explicit = explicit;
    }

    @Override
    public WaitStatement clone() {
      return new WaitStatement(procName, this.block.clone(),
          new ArrayList<Variable>(waitVars),
          new ArrayList<Variable>(usedVariables),
          new ArrayList<Variable>(containersToRegister), explicit);
    }

    public Block getBlock() {
      return block;
    }

    @Override
    public void generate(Logger logger, CompilerBackend gen, GenInfo info)
        throws UndefinedTypeException {

      gen.startWaitStatement(procName, waitVars, usedVariables,
          containersToRegister, explicit);
      this.block.generate(logger, gen, info);
      gen.endWaitStatement(containersToRegister);
    }

    @Override
    public void prettyPrint(StringBuilder sb, String currentIndent) {
      String newIndent = currentIndent + indent;
      sb.append(currentIndent + "wait (");
      ICUtil.prettyPrintVarList(sb, waitVars);
      sb.append(") ");
      sb.append("/*" + procName + "*/ " );
      ICUtil.prettyPrintVarInfo(sb, usedVariables, containersToRegister);
      sb.append(" {\n");
      block.prettyPrint(sb, newIndent);
      sb.append(currentIndent + "}\n");
    }

    @Override
    public List<Block> getBlocks() {
      return Arrays.asList(block);
    }

    @Override
    public void replaceVars(Map<String, Oparg> renames) {
      replaceVarsInBlocks(renames, false);
      ICUtil.replaceVarsInList(renames, waitVars, true);
      ICUtil.replaceVarsInList(renames, usedVariables, true);
      ICUtil.replaceVarsInList(renames, containersToRegister, true);
    }

    @Override
    public void replaceInputs(Map<String, Oparg> renames) {
      replaceVarsInBlocks(renames, true);
      ICUtil.replaceVarsInList(renames, waitVars, true);
    }

    public boolean isExplicit() {
      return explicit;
    }

    @Override
    public ContinuationType getType() {
      return ContinuationType.WAIT_STATEMENT;
    }

    @Override
    public Collection<Variable> requiredVars() {
      ArrayList<Variable> res = new ArrayList<Variable>();
      for (Variable c: containersToRegister) {
        if (c.getStorage() == VariableStorage.ALIAS) {
          // Might be alias for actual container written inside
          res.add(c);
        }
      }
      if (explicit) {
        for (Variable v: waitVars) {
          res.add(v);
        }
      }
      return res; // can later eliminate waitVars, etc
    }

    public List<Variable> getWaitVars() {
      return Collections.unmodifiableList(this.waitVars);
    }

    @Override
    public void removeVars(Set<String> removeVars) {
      removeVarsInBlocks(removeVars);
      ICUtil.removeVarsInList(waitVars, removeVars);
      ICUtil.removeVarsInList(usedVariables, removeVars);
      ICUtil.removeVarsInList(containersToRegister, removeVars);
    }

    @Override
    public Block branchPredict(Map<String, Oparg> knownConstants) {
      // We can't really do branch prediction for a wait statement, but
      // it is a useful mechanism to piggy-back on to remove the wait
      return tryInline(knownConstants.keySet());
    }

    @Override
    public boolean isNoop() {
      return this.block.isEmpty();
    }

    public void removeWaitVars(Set<String> closedVars) {
      // In our current implementation, this is equivalent
      tryInline(closedVars);
    }
    
    @Override
    public Block tryInline(Set<String> closedVars) {
      boolean mustWait = false;
      // iterate over wait vars, remove those in list
      ListIterator<Variable> it = waitVars.listIterator();
      while(it.hasNext()) {
        Variable wv = it.next();
        if (closedVars.contains(wv.getName())) {
          it.remove();
        } else {
          mustWait = true;
        }
      }
      if (mustWait) {
        return null;
      } else {
        // if at end we have nothing left, return the inner block for inlining
        return block;
      }
    }

    @Override
    public List<Variable> blockingVars() {
      return Collections.unmodifiableList(this.waitVars);
    }

    @Override
    public boolean variablesPassedInAutomatically() {
      return false;
    }

    @Override
    public Collection<Variable> getPassedInVars() {
      return Collections.unmodifiableList(this.usedVariables);
    }

    @Override
    public void addPassedInVar(Variable variable) {
      this.usedVariables.add(variable);
    }

    @Override
    public void removePassedInVar(Variable variable) {
      ICUtil.removeVarInList(this.usedVariables, variable.getName());
    }

    @Override
    public List<Variable> constructDefinedVars() {
      return null;
    }
  }

}
