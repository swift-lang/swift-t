package exm.stc.common.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;


/**
 * Union-find data structure that supports scoping.
 *
 * The data-structure is structured as a tree.  Every merge of two sets
 * affects the current node and any descendants, but has no effect in the
 * parent or other ancestors.
 *
 * Merging in a leaf node of the tree is straightforward: only local data
 * structures need to be updated.
 *
 * Merging in a non-leaf node is more
 *
 * @param <T>
 */
public class ScopedUnionFind<T> {

  /**
   * Parent of the union find, null if this is root
   */
  private final ScopedUnionFind<T> parent;

  /**
   * Internal mapping from member to canonical.
   *
   * We keep this up-to-date so every entry directly links an entry to its
   * canonical.
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
  public Set<T> merge(T winner, T loser) {
    T winnerCanon = lookup(winner);
    T loserCanon = lookup(loser);

    Set<T> affectedMembers = members(loserCanon);

    // TODO: add self link?

    // Already same set, do nothing
    if (affectedMembers.contains(winnerCanon)) {
      return Collections.emptySet();
    }

    // Allow children to update before modifying here
    notifyChanged(winnerCanon, loserCanon);

    for (T affectedMember: affectedMembers) {
      canonical.put(affectedMember, winnerCanon);
    }

    subscribeToUpdates(winnerCanon);

    return Collections.unmodifiableSet(affectedMembers);
  }

  /**
   * Find all member of set associated with canonical value, including
   * the value itself
   * @param val
   * @return
   */
  public Set<T> members(T canon) {
    // Search up to find all members
    Set<T> members = new HashSet<T>();
    members.add(canon);

    ScopedUnionFind<T> curr = this;
    while (curr != null) {
      // TODO: can we avoid duplicates here?
      members.addAll(curr.canonical.getByValue(canon));
      curr = curr.parent;
    }

    return members;
  }

  /**
   * Subscribe to canonical updates
   * @param winnerCanon
   */
  private void subscribeToUpdates(T x) {
    ScopedUnionFind<T> curr = this.parent;
    while (curr != null) {
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
