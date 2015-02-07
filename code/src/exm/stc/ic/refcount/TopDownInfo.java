package exm.stc.ic.refcount;

import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.ic.aliases.AliasTracker;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;

/**
 * State about variables, etc that gets propagated down from parents
 */
class TopDownInfo {
  final HierarchicalSet<Var> initAliasVars;
  final AliasTracker aliases;
  public TopDownInfo() {
    this(new HierarchicalSet<Var>(), new AliasTracker());
  }

  private TopDownInfo(HierarchicalSet<Var> initAliasVars,
                       AliasTracker aliases) {
    this.initAliasVars = initAliasVars;
    this.aliases = aliases;
  }

  public TopDownInfo makeChild() {
    return new TopDownInfo(this.initAliasVars.makeChild(),
                           this.aliases.makeChild());
  }

  /**
   *  Make child state for inside continuation
   */
  public TopDownInfo makeChild(Continuation cont) {
    TopDownInfo child = makeChild();
    for (Var v : cont.constructDefinedVars()) {
      if (v.storage() == Alloc.ALIAS) {
        initAliasVars.add(v);
      }
    }
    return child;
  }

  public void updateForInstruction(Instruction inst) {
    // Track which alias vars are assigned
    for (Var init: inst.getInitialized()) {
      if (init.storage() == Alloc.ALIAS) {
        initAliasVars.add(init);
      }
    }
    aliases.update(inst);
  }

}