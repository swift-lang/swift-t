package exm.parser.ic;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import exm.parser.util.ParserRuntimeException;

public class HierarchicalSet<T> implements Set<T> {
  private final HierarchicalSet<T> parent;
  private final Set<T> set;
  
  
  private HierarchicalSet(HierarchicalSet<T> parent, Set<T> set) {
    this.parent = parent;
    if (set == null) {
      this.set = new HashSet<T>(); 
    } else {
      this.set = set;
    }
  }

  public HierarchicalSet() {
    this(null, null);
  }
  public HierarchicalSet(Set<T> innerSet) {
    this(null, innerSet);
  }
  
  /**
   * Make a new hierarchical set with this as the parent
   * @return
   */
  public HierarchicalSet<T> makeChild() {
    return new HierarchicalSet<T>(this, null);
  }

  /**
   * Make a new hierarchical set with this as the parent,
   * and innerSet as the backing set
   * @param innerSet
   * @return
   */
  public HierarchicalSet<T> makeChild(Set<T> innerSet) {
    return new HierarchicalSet<T>(this, innerSet);
  }
  
  @Override
  public boolean add(T e) {
    return set.add(e);
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    return set.addAll(c);
  }

  @Override
  public void clear() {
    throw new ParserRuntimeException("not implemented");
  }

  @Override
  public boolean contains(Object o) {
    return set.contains(o) || (parent != null && parent.contains(o));
  }
  

  @Override
  public boolean containsAll(Collection<?> c) {
    throw new ParserRuntimeException("not implemented");
  }

  @Override
  public boolean isEmpty() {
    return set.isEmpty() && (parent == null || parent.isEmpty());
  }

  @Override
  public Iterator<T> iterator() {
    return new Iterator<T>() {
      Iterator<T> parentIterator =
          parent == null ? null : parent.iterator();
      Iterator<T> thisIterator 
          = set.iterator();
      @Override
      public boolean hasNext() {
        return thisIterator.hasNext() ||
              (parentIterator != null && parentIterator.hasNext());
      }

      @Override
      public T next() {
        if (parentIterator == null) {
          // No parent, just use inner iterator
          return thisIterator.next();
        } else if (thisIterator.hasNext()) {
          return thisIterator.next();
        } else {
          return parentIterator.next();
        }
      }
      @Override
      public void remove() {
        throw new ParserRuntimeException("not implemented");
      }
      
    };
  }

  @Override
  public boolean remove(Object o) {
    throw new ParserRuntimeException("not implemented");
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new ParserRuntimeException("not implemented");
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new ParserRuntimeException("not implemented");
  }

  @Override
  public int size() {
    int parentSize = parent == null ? 0 : parent.size();
    return set.size() + parentSize;
  }

  @Override
  public Object[] toArray() {
    throw new ParserRuntimeException("not implemented");
  }

  @Override
  public <S> S[] toArray(S[] a) {
    throw new ParserRuntimeException("not implemented");
  }
  
  @Override
  public String toString() {
    StringBuffer accum = new StringBuffer();
    accum.append("{");
    boolean wrote = false;
    if (parent != null) {
      wrote = parent.writeItems(accum, true);
    }
    wrote = wrote || this.writeItems(accum, !wrote);
    accum.append("}");
    return accum.toString();
  }

  private boolean writeItems(StringBuffer accum, boolean first) {
    for (T i: set) {
      if (first) {
        first = false; 
      } else {
        accum.append(",");
      }
      accum.append(i.toString());
    }
    return set.size() > 0;
  }

}
