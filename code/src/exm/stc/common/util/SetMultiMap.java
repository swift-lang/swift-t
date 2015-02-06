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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Simple multi-map implementation that is useful for some algorithms. Sets are
 * used for unique values in order. Duplicate entries are stored
 *
 * The list is implemented as an HashSet by default, but a factory can be
 * provided that produces other set types.
 *
 * @param <K>
 * @param <V>
 */
public class SetMultiMap<K, V> extends GenericMultiMap<Set<V>, K, V> {

  public SetMultiMap() {
    this(new HashSetFactory<V>());
  }

  public SetMultiMap(CollectionFactory<Set<V>, V> factory) {
    super(factory);
  }

  @Override
  public SetMultiMap<K, V> clone() {
    SetMultiMap<K, V> cloned = new SetMultiMap<K, V>(factory);
    this.copyForClone(cloned);
    return cloned;
  }

  public static class HashSetFactory<V1> implements
      CollectionFactory<Set<V1>, V1> {
    @Override
    public Set<V1> make() {
      return new HashSet<V1>();
    }

    @Override
    public Set<V1> empty() {
      return Collections.emptySet();
    }

    @Override
    public Set<V1> clone(Set<V1> c) {
      return new HashSet<V1>(c);
    }
  }

  public static class TreeSetFactory<V1> implements
      CollectionFactory<Set<V1>, V1> {
    @Override
    public Set<V1> make() {
      return new TreeSet<V1>();
    }

    @Override
    public Set<V1> empty() {
      return Collections.emptySet();
    }

    @Override
    public Set<V1> clone(Set<V1> c) {
      return new TreeSet<V1>(c);
    }
  }
}
