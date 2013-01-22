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
 * Copyright [yyyy] [name of copyright owner]
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
 * limitations under the License..
 */
package exm.stc.common.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
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
    return set.contains(o) || (parent != null && parent.contains(o));
  }
  

  @Override
  public boolean containsAll(Collection<?> c) {
    throw new STCRuntimeError("not implemented");
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
        throw new STCRuntimeError("not implemented");
      }
      
    };
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

}
