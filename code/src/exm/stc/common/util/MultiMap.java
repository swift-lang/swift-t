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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple multi-map implementation that is useful for some algorithms. Lists are
 * used to store values in order.
 *
 * The list is implemented as an ArrayList by default, but a factory can be
 * provided that produces other list types.
 *
 * @param <K>
 * @param <V>
 */
public class MultiMap<K, V> extends GenericMultiMap<List<V>, K, V> {

  public MultiMap() {
    this(new ArrayListFactory<V>());
  }

  public MultiMap(CollectionFactory<List<V>, V> factory) {
    super(factory);
  }

  @Override
  public MultiMap<K, V> clone() {
    MultiMap<K, V> cloned = new MultiMap<K, V>(factory);
    this.copyForClone(cloned);
    return cloned;
  }

  public static class LinkedListFactory<V1> implements
      CollectionFactory<List<V1>, V1> {
    @Override
    public List<V1> make() {
      return new LinkedList<V1>();
    }

    @Override
    public List<V1> empty() {
      return Collections.emptyList();
    }

    @Override
    public List<V1> clone(List<V1> c) {
      return new LinkedList<V1>(c);
    }
  }

  public static class ArrayListFactory<V1> implements
      CollectionFactory<List<V1>, V1> {
    @Override
    public List<V1> make() {
      return new ArrayList<V1>();
    }

    @Override
    public List<V1> empty() {
      return Collections.emptyList();
    }

    @Override
    public List<V1> clone(List<V1> c) {
      return new ArrayList<V1>(c);
    }
  }
}
