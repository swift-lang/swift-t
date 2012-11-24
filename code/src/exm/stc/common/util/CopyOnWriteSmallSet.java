package exm.stc.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class CopyOnWriteSmallSet<T> implements Set<T> {
  private ArrayList<T> data;
  private boolean exclusive; 
  
  public CopyOnWriteSmallSet() {
    this.data = new ArrayList<T>();
    this.exclusive = true;
  }
  
  public CopyOnWriteSmallSet(ArrayList<T> data) {
    this.data = data;
    this.exclusive = false;
  }
  
  public CopyOnWriteSmallSet(CopyOnWriteSmallSet<T> other) {
    this.data = other.data;
    this.exclusive = false;
  }
  
  private void copyOnWrite() {
    if (!exclusive) {
      data = new ArrayList<T>(data);
      exclusive = true;
    }
  }

  @Override
  public boolean add(T e) {
    copyOnWrite();
    return data.add(e);
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    copyOnWrite();
    return data.addAll(c);
  }

  @Override
  public void clear() {
    copyOnWrite();
    data.clear();
  }

  @Override
  public boolean contains(Object o) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return data.containsAll(c);
  }

  @Override
  public boolean isEmpty() {
    return data.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    return data.listIterator();
  }

  @Override
  public boolean remove(Object o) {
    copyOnWrite();
    return data.remove(o);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    copyOnWrite();
    return data.removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new RuntimeException("retainAll not implemented");
  }

  @Override
  public int size() {
    return data.size();
  }

  @Override
  public Object[] toArray() {
    return data.toArray();
  }

  @Override
  public <S> S[] toArray(S[] a) {
    return data.toArray(a);
  }
}
