package exm.stc.ic.refcount;

import java.util.Map.Entry;

import exm.stc.common.Settings;
import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.RefCounting.RefCountType;
import exm.stc.common.lang.Var;
import exm.stc.ic.aliases.AliasKey;
import exm.stc.ic.tree.ICContinuations.Continuation;
import exm.stc.ic.tree.ICContinuations.ContinuationType;
import exm.stc.ic.tree.ICTree.Block;
import exm.stc.ic.tree.TurbineOp.RefCountOp.RCDir;

public class RCUtil {

  static boolean isAsyncForeachLoop(Continuation cont) {
    return cont.isAsync() && isForeachLoop(cont);
  }

  static boolean isForeachLoop(Continuation cont) {
    return (cont.getType() == ContinuationType.FOREACH_LOOP ||
            cont.getType() == ContinuationType.RANGE_LOOP);
  }

  /**
   *
   * @param cont
   * @param block
   * @param var
   *          a var that is in scope within block
   * @return true if var is accessible outside continuation
   */
  static boolean definedOutsideCont(Continuation cont, Block block, Var var) {
    assert (block.getParentCont() == cont);
    return !block.variables().contains(var)
        && !cont.constructDefinedVars().contains(var);
  }

  static boolean mergeEnabled() {
    return Settings.getBooleanUnchecked(Settings.OPT_MERGE_REFCOUNTS);
  }

  static boolean cancelEnabled() {
    return Settings.getBooleanUnchecked(Settings.OPT_CANCEL_REFCOUNTS);
  }

  static boolean piggybackEnabled() {
    return Settings.getBooleanUnchecked(Settings.OPT_PIGGYBACK_REFCOUNTS);
  }

  static boolean batchEnabled() {
    return Settings.getBooleanUnchecked(Settings.OPT_BATCH_REFCOUNTS);
  }

  static boolean hoistEnabled() {
    return Settings.getBooleanUnchecked(Settings.OPT_HOIST_REFCOUNTS);
  }

  /**
   * Check reference counts are all set to zero
   * @param block
   * @param increments
   * @param rcType
   * @param noIncrements if true, check that they are <= 0
   * @param noDecrements if true, check that they are >= 0
   */
  static void checkRCZero(Block block, RCTracker increments,
                         RefCountType rcType,
                         boolean noIncrements, boolean noDecrements) {
    // Check that all refcounts are zero
    if (noIncrements) {
      for (Entry<AliasKey, Long> e : increments.rcIter(rcType, RCDir.INCR)) {
        if (e.getValue() != 0) {
          throw new STCRuntimeError("Refcount " + rcType  + " incr not 0 "
                + "after pass " + e.toString() + " in block " + block);
        }
      }
    }
    if (noDecrements) {
      for (Entry<AliasKey, Long> e : increments.rcIter(rcType, RCDir.DECR)) {
        if (e.getValue() != 0) {
          throw new STCRuntimeError("Refcount " + rcType  + " decr not 0 "
                + "after pass " + e.toString() + " in block " + block);
        }
      }
    }
  }
}
