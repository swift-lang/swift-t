/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package exm.stc.common.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import exm.stc.common.exceptions.STCRuntimeError;

/**
 * An implementation of the set interface that allows cheap
 * creation of child maps.
 */
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
    throw new STCRuntimeError("not implemented");
  }

  @Override
  public boolean contains(Object o) {
    HierarchicalSet<T> curr = this;
    while (curr != null) {
      if (curr.set.contains(o)) {
        return true;
      }
      curr = curr.parent;
    }
    return false;
  }


  @Override
  public boolean containsAll(Collection<?> c) {
    throw new STCRuntimeError("not implemented");
  }

  @Override
  public boolean isEmpty() {
    HierarchicalSet<T> curr = this;
    while (curr != null) {
      if (!curr.set.isEmpty()) {
        return false;
      }
      curr = curr.parent;
    }
    return true;
  }

  @Override
  public Iterator<T> iterator() {
    return new HSIt();
  }

  @Override
  public boolean remove(Object o) {
    throw new STCRuntimeError("not implemented");
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new STCRuntimeError("not implemented");
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new STCRuntimeError("not implemented");
  }

  @Override
  public int size() {
    int parentSize = parent == null ? 0 : parent.size();
    return set.size() + parentSize;
  }

  @Override
  public Object[] toArray() {
    throw new STCRuntimeError("not implemented");
  }

  @Override
  public <S> S[] toArray(S[] a) {
    throw new STCRuntimeError("not implemented");
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

  private final class HSIt implements Iterator<T> {

    private HSIt() {
      curr = HierarchicalSet.this;
      currIt = curr.set.iterator();
    }

    HierarchicalSet<T> curr;
    Iterator<T> currIt;

    @Override
    public boolean hasNext() {
      if (currIt != null && currIt.hasNext()) {
        return true;
      }
      return advance();
    }

    private boolean advance() {
      if (currIt == null) {
        return false;
      }
      while (!currIt.hasNext()) {
        curr = curr.parent;
        if (curr == null) {
          currIt = null;
          return false;
        } else {
          currIt = curr.set.iterator();
        }
      }
      return true;
    }

    @Override
    public T next() {
      if (advance()) {
        return currIt.next();
      }
      throw new NoSuchElementException();
    }

    @Override
    public void remove() {
      throw new STCRuntimeError("not implemented");
    }
  }

}
