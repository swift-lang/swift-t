package exm.stc.ic.opt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.Types;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.Pair;
import exm.stc.ic.opt.InitVariables.InitState;
import exm.stc.ic.opt.OptimizerPass.FunctionOptimizerPass;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Opcode;
import exm.stc.ic.tree.ICInstructions.TurbineOp;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.BlockType;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Statement;
import exm.stc.ic.tree.ICTree.StatementType;

/**
 * Try to merge multiple array inserts into a single build instruction
 */
public class ArrayBuild extends FunctionOptimizerPass {

  @Override
  public String getPassName() {
    return "Array build";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_ARRAY_BUILD;
  }

  @Override
  public void optimize(Logger logger, Function f) throws UserException {
    InfoMap info = buildInfo(logger, f);
    optimize(logger, f, info);
  }

  private static class InfoMap extends HashMap<Pair<Var, Block>, BlockVarInfo> {

    /** Prevent warnings by providing version */
    private static final long serialVersionUID = 1L;

    BlockVarInfo getEntry(Block block, Var arr) {
      Pair<Var, Block> key = Pair.create(arr, block);
      BlockVarInfo entry = this.get(key);
      if (entry == null) {
        entry = new BlockVarInfo();
        this.put(key, entry);
      }
      return entry;
    }
  }
  
  static class BlockVarInfo {
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
  }

  /**
   * Traverse and build up info about how array is modified in all
   * blocks of function
   * @param logger
   * @param f
   * @return
   */
  private InfoMap buildInfo(Logger logger, Function f) {
    // Set to track candidates in scope 
    HierarchicalSet<Var> candidates = new HierarchicalSet<Var>();
    InfoMap info = new InfoMap();
    buildInfoRec(logger, f, f.mainBlock(), info, candidates);
    return info;
  }


  private boolean isValidCandidate(Var var) {
    return Types.isArray(var) && var.storage() != Alloc.ALIAS;
  }

  private void addValidCandidates(Collection<Var> candidates,
                                  Collection<Var> vars) {
    for (Var var: vars) {
      if (isValidCandidate(var)){
        candidates.add(var);
      }
    }
  }

  private void buildInfoRec(Logger logger, Function f,
      Block block, InfoMap info, HierarchicalSet<Var> candidates) {
    addBlockCandidates(f, block, candidates);
    
    for (Statement stmt: block.getStatements()) {
      switch (stmt.type()) {
        case INSTRUCTION:
          updateInfo(block, info, stmt.instruction(), candidates);
          break;
        default:
          // Do nothing: handle conditionals below
          break;
      }
    }
    
    for (Continuation c: block.allComplexStatements()) {
      for (Block inner: c.getBlocks()) {
        buildInfoRec(logger, f, inner, info, candidates.makeChild());
      }
    }
    
    // Compute bottom-up properties
    updateInfoBottomUp(logger, block, info, candidates);
  }

  /**
   * Add new candidates declared in block
   * @param f
   * @param block
   * @param candidates
   */
  private void addBlockCandidates(Function f, Block block, Set<Var> candidates) {
    if (block.getType() ==  BlockType.MAIN_BLOCK) {
      addValidCandidates(candidates, f.getOutputList());
    }
    addValidCandidates(candidates, block.getVariables());
  }

  private void updateInfo(Block block, InfoMap info, Instruction inst,
                          Set<Var> candidates) {
    if (inst.op == Opcode.ARRAY_INSERT_IMM) {
      Var arr = inst.getOutput(0);
      if (candidates.contains(arr)) {
        BlockVarInfo entry = info.getEntry(block, arr);
        entry.insertImmHere = true;
      }
    } else {
      for (Var out: inst.getOutputs()) {
        if (isValidCandidate(out) && candidates.contains(out)) {
          // Can't optimize variables that are modified by other instructions
          BlockVarInfo entry = info.getEntry(block, out);
          entry.otherModHere = true;
        }
      }
    }
  }


  /**
   * Update recursively defined properties
   * @param logger
   * @param block
   * @param info
   * @param candidates update info for these vars
   */
  private void updateInfoBottomUp(Logger logger, Block block,
          InfoMap info, Set<Var> candidates) {
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

  private void optimize(Logger logger, Function f, InfoMap info) {
    InitState init = InitState.enterFunction(f);
    
    optRecurseOnBlock(logger, f, f.mainBlock(), info, init,
                new HierarchicalSet<Var>(), new HierarchicalSet<Var>());
  }

  private void optRecurseOnBlock(Logger logger, Function f, Block block,
      InfoMap info, InitState init, 
      HierarchicalSet<Var> cands, HierarchicalSet<Var> invalid) {
    addBlockCandidates(f, block, cands);
    
    for (Var cand: cands) {
      if (!invalid.contains(cand)) {
        BlockVarInfo vi = info.getEntry(block, cand);
        if (vi.otherModRec) {
          invalid.add(cand);
        } else if (vi.insertImmOnce && vi.insertImmHere) {
          // Optimize here
          replaceInserts(logger, block, init, cand);
        } else if (vi.insertImmOnce) {
          // Do nothing: handle in child block
        } else {
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
      Continuation cont, InfoMap info, InitState init,
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
   * @param arr
   * @param init initialized state from outside.  Not modified
   */
  private void replaceInserts(Logger logger, Block block,
                              InitState init, Var arr) {
    
    // First remove the old instructions and gather keys and vals
    Pair<List<Arg>, List<Arg>> keyVals = removeOldInserts(block, arr);
    List<Arg> keys = keyVals.val1;
    List<Arg> vals = keyVals.val2;
    
    ListIterator<Statement> insertPos;
    insertPos = findArrayBuildPos(logger, block, init, arr, keys, vals);
    
    insertPos.add(TurbineOp.arrayBuild(arr, keys, vals));
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
      assert(val.isVar());
      if (InitVariables.varMustBeInitialized(val.getVar(), false)) {
        // Must init alias
        if (!outerInit.initVars.contains(val.getVar())) {
          needsInit.add(val.getVar());
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

  private Pair<List<Arg>, List<Arg>> removeOldInserts(Block block, Var arr) {
    List<Arg> keys = new ArrayList<Arg>();
    List<Arg> vals = new ArrayList<Arg>();
    ListIterator<Statement> it = block.statementIterator();
    while (it.hasNext()) {
      Statement stmt = it.next();
      if (stmt.type() == StatementType.INSTRUCTION) {
        Instruction inst = stmt.instruction();
        if (inst.op == Opcode.ARRAY_INSERT_IMM) {
          if (inst.getOutput(0).equals(arr)) {
            it.remove();
            Arg key = inst.getInput(0);
            Arg val = inst.getInput(1);
            assert(Types.isArrayKeyVal(arr, key));
            assert(Types.isMemberType(arr, val.getVar()));
            keys.add(key);
            vals.add(val);
          }
        }
      }
    }
    return Pair.create(keys, vals);
  }

}
