package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Logging;
import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.VarCount;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.Pair;
import exm.stc.ic.aliases.AliasKey;
import exm.stc.ic.aliases.AliasTracker;
import exm.stc.ic.opt.InitVariables.InitState;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;
import exm.stc.ic.tree.Opcode;
import exm.stc.ic.tree.TurbineOp;

/**
 * Try to merge multiple array inserts into a single build instruction.
 * TODO: optimise multisets
 */
public class ArrayBuild implements OptimizerPass {

  @Override
  public String getPassName() {
    return "Array build";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_ARRAY_BUILD;
  }

  @Override
  public void optimize(Logger logger, Program prog) throws UserException {
    Map<String, Function> funcMap = prog.getFunctionMap();
    for (Function f: prog.functions()) {
      ArrayInfo info = buildInfo(logger, funcMap, f);
      optimize(logger, f, info);
    }
  }

  private static class ArrayInfo {

    private final Map<Block, AliasTracker> aliasMap
                      = new HashMap<Block, AliasTracker>();

    private final Map<Pair<AliasKey, Block>, BlockVarInfo> varMap
                      = new HashMap<Pair<AliasKey, Block>, BlockVarInfo>();

    /**
     * Mark correspondence between block and aliases
     * @param block
     * @param aliases
     */
    void addAliasTracker(Block block, AliasTracker aliases) {
      aliasMap.put(block, aliases);
    }

    BlockVarInfo getEntry(Block block, Var arr) {
      AliasTracker aliases = aliasMap.get(block);
      assert(aliases != null) : "Alias map must be init";
      return getEntry(block, aliases.getCanonical(arr));
    }

    BlockVarInfo getEntry(Block block, AliasKey arr) {
      Pair<AliasKey, Block> key = Pair.create(arr, block);
      BlockVarInfo entry = varMap.get(key);
      if (entry == null) {
        entry = new BlockVarInfo();
        varMap.put(key, entry);
      }

      return entry;
    }

    /**
     * Get aliases for block, raises error if not present
     * @param block
     * @return
     */
    public AliasTracker getAliases(Block block) {
      AliasTracker aliases = aliasMap.get(block);
      assert(aliases != null) : "Alias map must be init";
      return aliases;
    }
  }

  static class BlockVarInfo {
    /** If var was declared in this block */
    boolean declaredHere = false;

    /** If immediate insert instruction found in this block */
    boolean insertImmHere = false;

    /** If another modification instruction found in this block */
    boolean otherModHere = false;

    /** If insert imm was used in this block or descendants */
    boolean insertImmRec = false;

    /** If another mod was used in this block or descendants */
    boolean otherModRec = false;

    /** If, for any possible execution of this block, insertImm is
     * called at most once on this variable in a single block in
     * this subtree.  Also false if insertImm never used in subtree */
    boolean insertImmOnce = false;

    public boolean isModifiedHere() {
      return insertImmHere || otherModHere;
    }

    public boolean isModifiedInSubtree() {
      return otherModRec || insertImmRec;
    }

    @Override
    public String toString() {
      return "declaredHere: " + declaredHere + ", " +
              "insertImmHere: " + insertImmHere + ", " +
             "otherModHere: " + otherModHere + ", " +
             "insertImmRec: " + insertImmRec + ", " +
             "otherModRec: " + otherModRec + ", " +
             "insertImmOnce: " + insertImmOnce;
    }

    public boolean noInserts() {
      return !insertImmHere && !otherModHere &&
             !insertImmRec && !otherModRec;
    }
  }

  /**
   * Traverse and build up info about how array is modified in all
   * blocks of function
   * @param logger
   * @param f
   * @param funcMap
   * @return
   */
  private ArrayInfo buildInfo(Logger logger, Map<String, Function> funcMap,
                              Function f) {
    // Set to track candidates in scope
    HierarchicalSet<Var> candidates = new HierarchicalSet<Var>();
    ArrayInfo info = new ArrayInfo();

    // First build up complete alias info for each block, to avoid
    // complications with alias info being incrementally refined
    buildAliasInfoRec(logger, f, f.mainBlock(), info, new AliasTracker());

    buildInfoRec(logger, funcMap,f, f.mainBlock(), info, candidates);
    return info;
  }


  /**
   * Build up alias info for all blocks and subblocks, adding them
   * to info
   * @param logger
   * @param f
   * @param block
   * @param info
   * @param aliases
   */
  private void buildAliasInfoRec(Logger logger, Function f, Block block,
          ArrayInfo info, AliasTracker aliases) {
    info.addAliasTracker(block, aliases);

    for (Statement stmt: block.getStatements()) {
      switch(stmt.type()) {
        case INSTRUCTION:
          aliases.update(stmt.instruction());
          break;
        case CONDITIONAL:
          for (Block child: stmt.conditional().getBlocks()) {
            buildAliasInfoRec(logger, f, child, info, aliases.makeChild());
          }
          break;
        default:
          throw new STCRuntimeError("Unexpected " + stmt.type());
      }
    }

    for (Continuation c: block.getContinuations()) {
      for (Block child: c.getBlocks()) {
        buildAliasInfoRec(logger, f, child, info, aliases.makeChild());
      }
    }
  }

  /**
   * Check if this is a valid candidate for optimisation
   * @param var
   * @param canonicalOnly if true, return false if not canonical
   * @return
   */
  private boolean isValidCandidate(Var var, boolean canonicalOnly) {
    /*
     * Only attempt to optimise non-alias arrays. Optimising
     * some alias arrays, e.g. nested ones, would require further
     * analysis since there is not a single canonical non-alias
     * variable for the nested array.
     */
    return Types.isArray(var) &&
            !(canonicalOnly && var.storage() == Alloc.ALIAS);
  }

  private void addBlockCandidates(Block block,
      ArrayInfo info, Collection<Var> candidates,
      Collection<Var> vars) {
    for (Var var: vars) {
      if (isValidCandidate(var, true)) {
        candidates.add(var);
        info.getEntry(block, var).declaredHere = true;
        Logging.getSTCLogger().trace(var + " declared here! " +
                                System.identityHashCode(block));
      }
    }
  }

  private void buildInfoRec(Logger logger, Map<String, Function> funcMap,
      Function f, Block block, ArrayInfo info,
      HierarchicalSet<Var> candidates) {
    addBlockCandidates(f, block, info, candidates);

    for (Statement stmt: block.getStatements()) {
      switch (stmt.type()) {
        case INSTRUCTION:
          updateInfo(logger, funcMap, block, info, stmt.instruction(),
                      candidates);
          break;
        default:
          // Do nothing: handle conditionals below
          break;
      }
    }

    for (Continuation c: block.allComplexStatements()) {
      for (Block inner: c.getBlocks()) {
        buildInfoRec(logger, funcMap, f, inner, info, candidates.makeChild());
      }
    }

    // Compute bottom-up properties
    updateInfoBottomUp(logger, block, info, candidates);

    if (logger.isTraceEnabled()) {
      logger.trace("Collected info on block: " +
                  System.identityHashCode(block) + " " + block.getType());
      for (Var candidate: candidates) {
        logger.trace(candidate + " => " + info.getEntry(block, candidate));
      }
    }
  }

  /**
   * Add new candidates declared in block
   * @param f
   * @param block
   * @param candidates
   */
  private void addBlockCandidates(Function f, Block block, ArrayInfo info,
                                  Set<Var> candidates) {
    if (block.getType() ==  BlockType.MAIN_BLOCK) {
      addBlockCandidates(block, info, candidates, f.getOutputList());
    }
    addBlockCandidates(block, info, candidates, block.variables());
  }

  private void updateInfo(Logger logger, Map<String, Function> funcMap,
      Block block, ArrayInfo info, Instruction inst, Set<Var> candidates) {

    if (inst.op == Opcode.ARR_STORE) {
      Var arr = inst.getOutput(0);
      if (candidates.contains(arr)) {
        BlockVarInfo entry = info.getEntry(block, arr);
        entry.insertImmHere = true;
      }
    } else {
      for (Var out: inst.getOutputs()) {
        if (isValidCandidate(out, false) &&
             !initsAlias(inst, out)) {
          // Can't optimize variables that are modified by other instructions
          // It's ok if the instruction initialises aliases
          BlockVarInfo entry = info.getEntry(block, out);
          entry.otherModHere = true;
          if (logger.isTraceEnabled()) {
            logger.trace("Modified " + out + " due to " + inst);
          }
        }
      }
      Pair<List<VarCount>, List<VarCount>> inRC = inst.inRefCounts(funcMap);
      List<VarCount> inWriteRC = inRC.val2;
      for (VarCount written: inWriteRC) {
        if (written.count != 0 && isValidCandidate(written.var, false)) {
          // Write reference count transferred somewhere
          BlockVarInfo entry = info.getEntry(block, written.var);
          entry.otherModHere = true;
          if (logger.isTraceEnabled()) {
            logger.trace("Write RC " + written + " taken by " + inst);
          }
        }
      }
    }
  }

  private boolean initsAlias(Instruction inst, Var out) {
    if (out.storage() == Alloc.ALIAS) {
      for (Var init: inst.getInitialized()) {
        if (out.equals(init)) {
          return true;
        }
      }
    }

    return false;
  }


  /**
   * Update recursively defined properties
   * @param logger
   * @param block
   * @param info
   * @param candidates update info for these vars
   */
  private void updateInfoBottomUp(Logger logger, Block block,
          ArrayInfo info, Set<Var> candidates) {
    for (Var candidate: candidates) {
      BlockVarInfo ci = info.getEntry(block, candidate);
      ci.insertImmRec = ci.insertImmHere;
      ci.otherModRec = ci.otherModHere;

      // Count number of blocks in subtree that are "valid"
      int insertImmOnceCounter = ci.insertImmHere ? 1 : 0;
      // True if insert immediate might happen on multiple blocks
      boolean insertImmOnceInvalid = false;

      for (Continuation cont: block.allComplexStatements()) {
        int iiBlockCount = 0; // Count subblocks with insertImm
        for (Block contBlock: cont.getBlocks()) {
          BlockVarInfo ciInner = info.getEntry(contBlock, candidate);
          ci.insertImmRec = ci.insertImmRec || ciInner.insertImmRec;
          ci.otherModRec = ci.otherModRec || ciInner.otherModRec;
          if (ciInner.insertImmOnce) {
            iiBlockCount++;
          } else if (ciInner.insertImmRec) {
            insertImmOnceInvalid = true;
          }
        }
        if (cont.isLoop() && iiBlockCount > 0) {
          // Invalid: insert within loop
          insertImmOnceInvalid = true;
        } else if (cont.isConditional() && iiBlockCount > 0) {
          // We might be able to optimize if it only happens on branches
          // of this conditional
          insertImmOnceCounter++;
        } else {
          // Assume each block executes once
          if (iiBlockCount == 1) {
            insertImmOnceCounter++;
          } else if (iiBlockCount > 1) {
            insertImmOnceInvalid = true;
          }
        }
      }

      // If we determined that there was once place (this block or subtree)
      // where insertImmOnce was valid, and there were no disqualifiers
      ci.insertImmOnce = (insertImmOnceCounter == 1) && !insertImmOnceInvalid;
    }
  }

  private void optimize(Logger logger, Function f, ArrayInfo info) {
    InitState init = InitState.enterFunction(f);

    optRecurseOnBlock(logger, f, f.mainBlock(), info, init,
                new HierarchicalSet<Var>(), new HierarchicalSet<Var>());
  }

  private void optRecurseOnBlock(Logger logger, Function f, Block block,
      ArrayInfo info, InitState init,
      HierarchicalSet<Var> cands, HierarchicalSet<Var> invalid) {
    addBlockCandidates(f, block, info, cands);

    for (Var cand: cands) {
      if (!invalid.contains(cand)) {
        AliasTracker blockAliases = info.getAliases(block);
        AliasKey candKey = blockAliases.getCanonical(cand);

        BlockVarInfo vi = info.getEntry(block, cand);
        if (logger.isTraceEnabled()) {
          logger.trace("Candidate: " + cand + " in block " +
                  System.identityHashCode(block) + " " + block.getType());
          logger.trace(vi);
        }
        if (vi.otherModRec) {
          logger.trace("Can't optimize due to other inserts!");
          invalid.add(cand);
        } else if ((vi.insertImmOnce && vi.insertImmHere) ||
                    (vi.noInserts() && vi.declaredHere)) {
          // Criteria 1: declared here && no inserts here or in children
          // TODO
          // Criteria 2: declared in ancestor && not modified on any
          //        non-mutually-exclusive path

          // Optimize here: cases where only inserted in this block,
          // or no inserts at all
          logger.trace("Can optimize!");
          replaceInserts(logger, block, blockAliases, init, cand, candKey);
          invalid.add(cand); // Don't try to opt in descendants
        } else if (vi.insertImmOnce) {
          logger.trace("Try to optimize in descendant block!");
          // Do nothing: handle in child block
        } else {
          logger.trace("Optimization not valid!");
          // Invalid: can't do optimization anywhere
          invalid.add(cand);
        }
      }
    }

    for (Statement stmt: block.getStatements()) {
      switch (stmt.type()) {
        case INSTRUCTION:
          // Update which variables are initialized
          InitVariables.updateInitVars(logger, stmt, init, false);
          break;
        case CONDITIONAL:
          // Recurse and optimize, plus also update init vars
          optRecurseOnCont(logger, f, stmt.conditional(), info, init,
                           cands, invalid);
          break;
      }
    }

    for (Continuation cont: block.getContinuations()) {
      optRecurseOnCont(logger, f, cont, info, init, cands, invalid);
    }
  }

  private void optRecurseOnCont(Logger logger, Function f,
      Continuation cont, ArrayInfo info, InitState init,
      HierarchicalSet<Var> cands, HierarchicalSet<Var> invalid) {
    InitState contInit = init.enterContinuation(cont);

    List<InitState> blockInits = new ArrayList<InitState>();
    for (Block inner: cont.getBlocks()) {
      InitState blockInit = contInit.enterBlock(inner);
      optRecurseOnBlock(logger, f, inner, info, blockInit, cands.makeChild(),
                     invalid.makeChild());
      blockInits.add(blockInit);
    }

    if (InitState.canUnifyBranches(cont)) {
      init.unifyBranches(cont, blockInits);
    }
  }

  /**
   * Replace arrayInsertImm instructions with an arrayBuild
   * @param block
   * @param candKey
   * @param init initialized state from outside.  Not modified
   */
  private void replaceInserts(Logger logger, Block block,
          AliasTracker blockAliases, InitState init,
          Var cand, AliasKey candKey) {

    // First remove the old instructions and gather keys and vals
    Pair<List<Arg>, List<Arg>> keyVals =
            removeOldInserts(block, blockAliases, candKey);
    List<Arg> keys = keyVals.val1;
    List<Arg> vals = keyVals.val2;

    ListIterator<Statement> insertPos;
    insertPos = findArrayBuildPos(logger, block, init, cand, keys, vals);

    insertPos.add(TurbineOp.arrayBuild(cand, keys, vals));
  }

  /**
   * Select the location to insert the array build instruction
   * @param block
   * @param array
   * @param keys
   * @param vals
   * @return
   */
  private ListIterator<Statement> findArrayBuildPos(Logger logger,
      Block block, InitState outerInit,
      Var array, List<Arg> keys, List<Arg> vals) {

    // Place the array build instruction as early as possible, once all
    // inputs are initialized
    Set<Var> needsInit = new HashSet<Var>();

    // array variable may need to be initialized
    if (InitVariables.varMustBeInitialized(array, true)) {
      if (!outerInit.initVars.contains(array)) {
        needsInit.add(array);
      }
    }

    for (Arg key: keys) {
      if (key.isVar()) {
        // Assert to check assumptions match init var analysis
        assert (InitVariables.assignBeforeRead(key.getVar()));
        // Key must be assigned
        if (!outerInit.assignedVals.contains(key.getVar())) {
          needsInit.add(key.getVar());
        }
      }
    }
    for (Arg val: vals) {
      if (val.isVar()) {
        Var var = val.getVar();
       if (InitVariables.assignBeforeRead(var) &&
           !outerInit.assignedVals.contains(var)) {
         // Must assign value
         needsInit.add(var);
       } else if (InitVariables.varMustBeInitialized(var, false) &&
           !outerInit.initVars.contains(var)) {
          // Must init alias
          needsInit.add(var);
        }
      }
    }

    InitState blockInit = outerInit.enterBlock(block);

    // Move forward until all variables are initialized
    ListIterator<Statement> insertPos = block.statementIterator();
    while (insertPos.hasNext() && !needsInit.isEmpty()) {
      Statement stmt = insertPos.next();
      InitVariables.updateInitVars(logger, stmt, blockInit, false);
      // Check to see if everything is ready now
      // TODO: iterating over this every time is inefficient, but probably
      //       good enough
      Iterator<Var> it = needsInit.iterator();
      while (it.hasNext()) {
        Var v = it.next();
        if (InitVariables.assignBeforeRead(v)) {
          if (blockInit.assignedVals.contains(v)) {
            it.remove();
          }
        } else if (InitVariables.varMustBeInitialized(v, false)) {
          if (blockInit.initVars.contains(v)) {
            it.remove();
          }
        }
      }
    }
    if (!needsInit.isEmpty()) {
      logger.warn("STC internal warning: wasn't able to determine that "
          + needsInit + " were initialized");
    }
    return insertPos;
  }

  private Pair<List<Arg>, List<Arg>> removeOldInserts(Block block,
                  AliasTracker blockAliases, AliasKey candKey) {
    List<Arg> keys = new ArrayList<Arg>();
    List<Arg> vals = new ArrayList<Arg>();
    ListIterator<Statement> it = block.statementIterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      if (stmt.type() == StatementType.INSTRUCTION) {
        Instruction inst = stmt.instruction();
        if (inst.op == Opcode.ARR_STORE) {
          Var arrVar = inst.getOutput(0);
          AliasKey arrKey = blockAliases.getCanonical(arrVar);
          if (arrKey.equals(candKey)) {
            it.remove();
            Arg key = inst.getInput(0);
            Arg val = inst.getInput(1);
            assert(Types.isArrayKeyVal(candKey, key));
            assert(Types.isElemValType(candKey, val));
            keys.add(key);
            vals.add(val);
          }
        }
      }
    }
    return Pair.create(keys, vals);
  }

}
