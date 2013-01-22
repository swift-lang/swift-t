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
