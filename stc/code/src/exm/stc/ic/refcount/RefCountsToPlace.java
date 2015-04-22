package exm.stc.ic.refcount;

import exm.stc.common.lang.Var;

/**
 * Interface allowing us to query current refcounts
 */
public interface RefCountsToPlace {
  /**
   * @return the current refcount amount that can be piggybacked for this
   *         var.  Note that some vars may resolve to the same structure,
   *         so some counts may be shared betwene multiple vars
   */
  public long getCount(Var var);
}