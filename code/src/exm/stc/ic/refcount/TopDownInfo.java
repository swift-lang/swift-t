package exm.stc.ic.refcount;

import exm.stc.common.lang.Var;
import exm.stc.common.lang.Var.Alloc;
import exm.stc.common.util.HierarchicalSet;
import exm.stc.common.util.Pair;
import exm.stc.ic.aliases.AliasTracker;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICInstructions.Instruction;
import exm.stc.ic.tree.ICInstructions.Instruction.InitType;

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
    for (Pair<Var, InitType> out: inst.getInitialized()) {
      if (out.val1.storage() == Alloc.ALIAS) {
        assert(out.val2 == InitType.FULL); // Can't handle otherwise
        initAliasVars.add(out.val1);
      }
    }
    aliases.update(inst);
  }
  
}