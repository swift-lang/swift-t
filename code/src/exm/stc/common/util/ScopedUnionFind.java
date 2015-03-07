package exm.stc.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;


/**
 * Union-find data structure that supports scoping
 * @param <T>
 */
public class ScopedUnionFind<T> {

  /**
   * Parent of the union find, null if this is root
   */
  private final ScopedUnionFind<T> parent;

  /**
   * Internal mapping from member to canonical
   */
  private final TwoWayMap<T, T> canonical;


  /**
   * Children that we might need to propagate changes to
   */
  private final SetMultimap<T, ScopedUnionFind<T>> subscribed;

  private ScopedUnionFind(ScopedUnionFind<T> parent) {
    this.parent = parent;
    this.canonical = TwoWayMap.create();
    this.subscribed = HashMultimap.create();
  }

  public static <T1> ScopedUnionFind<T1> createRoot() {
    return new ScopedUnionFind<T1>(null);
  }

  public ScopedUnionFind<T> newScope() {
    return new ScopedUnionFind<T>(this);
  }

  public T lookup(T x) {
    ScopedUnionFind<T> curr = this;
    while (curr != null) {
      T canon = curr.canonical.get(x);
      if (canon != null) {
        return canon;
      }

      curr = curr.parent;
    }

    // x is on its own
    return x;
  }

  /**
   * Merge loser into winner.
   * Note: if they are already in same set, no changes, even if loser
   *  is canonical
   * @param winner
   * @param loser
   * @return unmodifiable collection of values that changed their canonical member
   */
  public Collection<T> merge(T winner, T loser) {
    T winnerCanon = lookup(winner);
    T loserCanon = lookup(loser);

    List<T> affectedMembers = findAllMembers(loserCanon);

    // TODO: add self link?

    // Already same set, do nothing
    if (affectedMembers.contains(winnerCanon)) {
      return Collections.emptyList();
    }

    for (T affectedMember: affectedMembers) {
      canonical.put(affectedMember, winnerCanon);
    }

    subscribeToUpdates(winnerCanon);

    notifyChanged(winnerCanon, loserCanon);

    return Collections.unmodifiableList(affectedMembers);
  }

  /**
   * Find all member of set associated with canonical value
   * @param val
   * @return
   */
  private List<T> findAllMembers(T canon) {
    // Search up to find all members
    List<T> affected = new ArrayList<T>();
    affected.add(canon);

    ScopedUnionFind<T> curr = this;
    while (curr != null) {
      // TODO: does this lead to duplicates?
      affected.addAll(curr.canonical.getByValue(canon));
      curr = curr.parent;
    }

    return affected;
  }

  /**
   * Subscribe to canonical updates
   * @param winnerCanon
   */
  private void subscribeToUpdates(T x) {
    ScopedUnionFind<T> curr = this.parent;
    while (curr != null) {
      System.err.println("Subscribe to " + x);
      curr.subscribed.put(x, this);
      curr = curr.parent;
    }
  }

  private void notifyChanged(T winnerCanon, T loserCanon) {
    for (ScopedUnionFind<T> subscriber: subscribed.get(loserCanon)) {
      subscriber.merge(winnerCanon, loserCanon);
    }
  }
}
