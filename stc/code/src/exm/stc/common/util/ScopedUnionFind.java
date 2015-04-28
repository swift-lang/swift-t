package exm.stc.common.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
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
  private final SetMultimap<Pair<T, Boolean>, UnionFindSubscriber<T>> subscribed;

  private final Subscriber subscriber = new Subscriber();

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


    // Already same set, do nothing
    if (loserCanon.equals(winnerCanon)) {
      return Collections.emptySet();
    }

    // Notify before change
    notifyChanged(true, winnerCanon, loserCanon);

    Set<T> affectedMembers = members(loserCanon);

    for (T affectedMember: affectedMembers) {
      canonical.put(affectedMember, winnerCanon);
    }

    subscribeToParentUpdates(winnerCanon);
    subscribeToParentUpdates(loserCanon);

    // Notify after change
    notifyChanged(false, winnerCanon, loserCanon);

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
      members.addAll(curr.canonical.getByValue(canon));
      curr = curr.parent;
    }

    return members;
  }

  /**
   * Subscribe to canonical updates
   * @param winnerCanon
   */
  private void subscribeToParentUpdates(T x) {
    if (parent != null) {
      parent.subscribe(x, true, subscriber);
    }
  }

  /**
   * Subscribe to get notifications when a value x is merged into
   * another.
   * @param x
   * @param before if true, notify before change applied
   * @param subscriber
   */
  public void subscribe(T x, boolean before, UnionFindSubscriber<T> subscriber) {
    ScopedUnionFind<T> curr = this;
    while (curr != null) {
      curr.subscribed.put(Pair.create(x, before), subscriber);
      curr = curr.parent;
    }
  }

  private void notifyChanged(boolean before, T winnerCanon, T loserCanon) {
    Pair<T, Boolean> key = Pair.create(loserCanon, before);
    for (UnionFindSubscriber<T> subscriber: subscribed.get(key)) {
      subscriber.notifyMerge(winnerCanon, loserCanon);
    }
  }

  public Collection<Entry<T, T>> entries() {
    return Collections.unmodifiableMap(canonical).entrySet();
  }

  /**
   * Build a multimap with all set members
   * @return
   */
  public SetMultimap<T, T> sets() {
    SetMultimap<T, T> result = HashMultimap.create();

    ScopedUnionFind<T> curr = this;
    while (curr != null) {
      result.putAll(curr.canonical.inverse());
      curr = curr.parent;
    }

    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    ScopedUnionFind<T> curr = this;
    while (curr != null) {
      if (sb.length() > 0) {
        sb.append(" => \n");
      }
      sb.append(curr.canonical.toString());
      curr = curr.parent;
    }
    return super.toString();
  }

  public Iterable<T> keys() {
    return Collections.unmodifiableSet(canonical.keySet());
  }

  public static interface UnionFindSubscriber<T> {
    public void notifyMerge(T winner, T loser);
  }

  private class Subscriber implements UnionFindSubscriber<T> {
    @Override
    public void notifyMerge(T winner, T loser) {
      merge(winner, loser);
    }

  }
}
