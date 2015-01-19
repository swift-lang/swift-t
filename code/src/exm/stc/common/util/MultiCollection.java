package exm.stc.common.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class MultiCollection<T> implements Collection<T> {

  private final Collection<? extends Collection<? extends T>> collections;

  public MultiCollection(Collection<? extends Collection<? extends T>> collections) {
    this.collections = collections;
  }

  @Override
  public Iterator<T> iterator() {
    return new MultiIterator();
  }

  private class MultiIterator implements Iterator<T> {
    private final Iterator<? extends Iterable<? extends T>> itIt;
    private Iterator<? extends T> it;
    T curr;

    public MultiIterator() {
      this.itIt = collections.iterator();
      this.it = null;
      this.curr = null;
    }

    @Override
    public boolean hasNext() {
      if (curr != null) {
        return true;
      }

      while (it == null || !it.hasNext()) {
        if (itIt.hasNext()) {
          it = itIt.next().iterator();
        } else {
          return false;
        }
      }

      curr = it.next();

      return true;
    }

    @Override
    public T next() {
      if (hasNext()) {
        T result = curr;
        curr = null;
        return result;
      } else {
        throw new NoSuchElementException();
      }
    }

    @Override
    public void remove() {
      // TODO Auto-generated method stub

    }

  }

  @Override
  public int size() {
    int size = 0;
    for (Collection<? extends T> coll: collections) {
      size += coll.size();
    }
    return size;
  }

  @Override
  public boolean isEmpty() {
    for (Collection<? extends T> coll: collections) {
      if (!coll.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean contains(Object o) {
    for (Collection<? extends T> coll: collections) {
      if (coll.contains(o)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Object[] toArray() {
    throw new UnsupportedOperationException("toArray not supported");
  }

  @Override
  public <S> S[] toArray(S[] a) {
    throw new UnsupportedOperationException("toArray not supported");
  }

  @Override
  public boolean add(T e) {
    throw new UnsupportedOperationException("modifications not supported");
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException("modifications not supported");
  }

  @Override
  public boolean containsAll(Collection<?> objColl) {
    for (Collection<? extends T> coll: collections) {
      if (coll.containsAll(objColl)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    throw new UnsupportedOperationException("modifications not supported");
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException("modifications not supported");
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException("modifications not supported");
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException("modifications not supported");
  }
}
