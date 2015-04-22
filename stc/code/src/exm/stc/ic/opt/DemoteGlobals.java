package exm.stc.ic.opt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.UserException;
import exm.stc.common.lang.Arg;
import exm.stc.common.lang.FnID;
import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.lang.Var.DefType;
import exm.stc.ic.opt.TreeWalk.TreeWalker;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.ICTree.CleanupAction;
import exm.stc.ic.tree.ICTree.Function;
import exm.stc.ic.tree.ICTree.Program;
import exm.stc.ic.tree.ICTree.RenameMode;


/**
 * Identify global variables that are only used in entry function and convert to locals.
 *
 */
public class DemoteGlobals implements OptimizerPass {

  @Override
  public String getPassName() {
    return "demote globals";
  }

  @Override
  public String getConfigEnabledKey() {
    return Settings.OPT_DEMOTE_GLOBALS;
  }

  @Override
  public void optimize(Logger logger, Program program) throws UserException {
    Set<Var> globals = findGlobalsToDemote(logger, program);

    demoteGlobals(logger, program, globals);
  }

  private Set<Var> findGlobalsToDemote(Logger logger, Program program) {
    Set<Var> candidates = new HashSet<Var>(program.globalVars().variables());

    for (Function f: program.functions()) {
      if (!f.id().equals(FnID.ENTRY_FUNCTION)) {
        TreeWalk.walk(logger, f, new DemoteGlobalsWalker(candidates));
      }
    }
    return candidates;
  }

  private static class DemoteGlobalsWalker extends TreeWalker {

    private final Set<Var> candidates;

    @Override
    public void visit(Logger logger, Function functionContext, Block block) {
      /*
       * Remove any variables referenced.
       */
      candidates.removeAll(block.variables());
    }

    @Override
    public void visit(Logger logger, Function functionContext, Continuation cont) {
      candidates.removeAll(cont.requiredVars(false));
    }

    @Override
    public void visit(Logger logger, Function functionContext, Instruction inst) {
      candidates.removeAll(inst.getOutputs());
      for (Arg in: inst.getInputs()) {
        if (in.isVar()) {
          candidates.remove(in.getVar());
        }
      }
    }

    @Override
    public void visit(Logger logger, Function functionContext,
        CleanupAction cleanup) {
      visit(logger, functionContext, cleanup.action());
    }

    public DemoteGlobalsWalker(Set<Var> candidates) {
      this.candidates = candidates;
    }

  }

  private void demoteGlobals(Logger logger, Program program, Set<Var> globals) {
    Function entry = program.lookupFunction(FnID.ENTRY_FUNCTION);
    Map<Var, Arg> replacements = new HashMap<Var, Arg>();

    // Do one-for-one replacement of global with local
    for (Var global: globals) {
      Var local = new Var(global.type(), global.name(), Alloc.STACK,
                DefType.LOCAL_USER, global.provenance(), global.mappedDecl());

      // Replace global with declaration in main block of entry function
      program.globalVars().removeVariable(global);
      entry.mainBlock().addVariable(local);

      replacements.put(global, local.asArg());
    }

    // Need to remove all imports of global var, then replace references
    TreeWalk.walk(logger, entry, new RemoveVarsWalker(globals));
    entry.renameVars(replacements, RenameMode.REPLACE_VAR, true);
  }

  private static class RemoveVarsWalker extends TreeWalker {

    private Set<Var> globals;

    public RemoveVarsWalker(Set<Var> globals) {
      this.globals = globals;
    }

    @Override
    public void visit(Logger logger, Function functionContext, Block block) {
      block.removeVarDeclarations(globals);
    }

  }

}
