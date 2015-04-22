package exm.stc.common.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import exm.stc.common.exceptions.STCRuntimeError;

public class Sets {

  private static final int ARRAY_SET_CUTOVER = 32;

  /**
   * Return a set implementation based on the expected size:
   * use an array-based implementation for small sets, and a
   * hashTable for larger sets
   * @param maxSize estimated or actual maximum capacity
   * @return
   */
  public static <T> Set<T> createSet(int maxSize) {
    if (maxSize <= ARRAY_SET_CUTOVER) {
      return new CopyOnWriteSmallSet<T>();
    } else {
      return new HashSet<T>();
    }
  }

  public static class IntersectionIterator<T> implements Iterable<T>,
                                                         Iterator<T> {
    private final List<Set<T>> sets;

    Iterator<T> firstIterator = null;
    T next = null;


    public IntersectionIterator(List<Set<T>> sets) {
      this.sets = sets;
    }

    @Override
    public Iterator<T> iterator() {
      if (firstIterator != null) {
        throw new STCRuntimeError(
            "Can't use IntersectionIterator multiple times");
      }

      if (sets.isEmpty()) {
        // Intersection is null
        firstIterator = Collections.<T>emptySet().iterator();
      } else {
        firstIterator = sets.get(0).iterator();
      }

      return this;
    }

    @Override
    public boolean hasNext() {
      fillNext();
      return next != null;
    }


    /**
     * Fill in the next field if not present
     */
    private void fillNext() {
      if (next != null)
        return;

      while (firstIterator.hasNext()) {
        T candidate = firstIterator.next();
        boolean presentInAll = true;
        for (Set<T> otherSet: sets.subList(1, sets.size())) {
          if (!otherSet.contains(candidate)) {
            presentInAll = false;
            break;
          }
        }
        if (presentInAll) {
          next = candidate;
          return;
        }
      }
    }

    @Override
    public T next() {
      fillNext();
      T res = next;
      next = null;
      return res;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Remove not supported");
    }

  }

  public static <T> Iterable<T> intersectionIter(List<Set<T>> sets) {
    return new IntersectionIterator<T>(sets);
  }

  public static <T> Set<T> intersection(List<Set<T>> sets) {
    if (sets.isEmpty()) {
      return Collections.emptySet();
    }

    HashSet<T> result = new HashSet<T>();

    for (T elemFromFirst: sets.get(0)) {
      boolean presentInAll = true;
      for (Set<T> other: sets.subList(1, sets.size())) {
        if (!other.contains(elemFromFirst)) {
          presentInAll = false;
          break;
        }
      }
      if (presentInAll) {
        result.add(elemFromFirst);
      }
    }

    return result;
  }

  public static <T> Set<T> union(Collection<Set<T>> allSets) {
    Set<T> res = new HashSet<T>();
    for (Set<? extends T> s: allSets) {
      res.addAll(s);
    }
    return res;
  }
}
