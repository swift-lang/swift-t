package exm.stc.ic.refcount;

import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.VarStorage;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.ic.opt.AliasTracker;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;

/**
 * State about variables, etc that gets propagated down from parents
 */
class TopDownInfo {
  final HierarchicalSet<Var> assignedAliasVars;
  final AliasTracker aliases;
  public TopDownInfo() {
    this(new HierarchicalSet<Var>(), new AliasTracker());
  }
  
  private TopDownInfo(HierarchicalSet<Var> assignedAliasVars,
                       AliasTracker aliases) {
    this.assignedAliasVars = assignedAliasVars;
    this.aliases = aliases;
  }
  
  public TopDownInfo makeChild() {
    return new TopDownInfo(this.assignedAliasVars.makeChild(),
                            this.aliases.makeChild());
  }

  /**
   *  Make child state for inside continuation
   */
  public TopDownInfo makeChild(Continuation cont) {
    TopDownInfo child = makeChild();
    for (Var v : cont.constructDefinedVars()) {
      if (v.storage() == VarStorage.ALIAS) {
        assignedAliasVars.add(v);
      }
    }
    return child;
  }

  public void updateForInstruction(Instruction inst) {
    // Track which alias vars are assigned
    for (Var out : inst.getInitialized()) {
      if (out.storage() == VarStorage.ALIAS) {
        assignedAliasVars.add(out);
      }
    }
    
    aliases.update(inst);
  }
  
}