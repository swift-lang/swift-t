package exm.stc.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Read only set that is union of multiple sets
 * @param <E>
 */
public class UnionSet<E> implements Set<E> {
  final List<Set<? extends E>> sets;
  
  public UnionSet() {
    this.sets = new ArrayList<Set<? extends E>>();
  }
  
  public UnionSet(Collection<Set<E>> sets) {
    this.sets = new ArrayList<Set<? extends E>>(sets.size());
    for (Set<? extends E> set: sets) {
      this.sets.add(set);
    }
  }
  
  public void addSet(Set<? extends E> set) {
    this.sets.add(set);
  }
  
  @Override
  public int size() {
    int sum = 0;
    for (Set<? extends E> set: sets) {
      sum += set.size();
    }
    return sum;
  }

  @Override
  public boolean isEmpty() {
    for (Set<? extends E> set: sets) {
      if (!set.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean contains(Object o) {
    for (Set<? extends E> set: sets) {
      if (set.contains(o)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Iterator<E> iterator() {
    return new UnionIterator();
  }

  private class UnionIterator implements Iterator<E> {
    
    int pos;
    Iterator<? extends E> curr = null;
    public UnionIterator() {
      this.pos = 0;
      if (sets.size() > 0) {
        this.curr = sets.get(0).iterator();
      }
    }
    
    public boolean hasNext() {
      if (curr == null)
        return false;
      
      if (curr.hasNext()) {
        return true;
      } else {
        advance();
        return hasNext();
      }
    }
    
    /**
     * Called when the current iterator is exhausted to advance to next
     */
    private void advance() {
      while (curr != null && !curr.hasNext()) {
        pos++;
        if (pos < sets.size()) {
          curr = sets.get(pos).iterator();
        } else {
          curr = null;
        }
      }
    }

    public E next() {
      if (curr == null) {
        throw new NoSuchElementException();
      }
      if (curr.hasNext()) {
        return curr.next();
      } else {
        advance();
        return next();
      }
    }
    
    public void remove() {
      if (curr == null)
        throw new IllegalStateException();
      curr.remove();
    }
  }
  
  @Override
  public Object[] toArray() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean add(E e) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(Collection<? extends E> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }
}
