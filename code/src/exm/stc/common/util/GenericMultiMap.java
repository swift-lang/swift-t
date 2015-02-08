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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Simple multi-map implementation that is useful for some algorithms. Generic
 * collections are used to store values in order.
 *
 * Collection must support add, isEmpty, size, iteration
 *
 * @param <C>
 * @param <K>
 * @param <V>
 */
public class GenericMultiMap<C extends Collection<V>, K, V> {

  public GenericMultiMap(CollectionFactory<C, V> f) {
    this.factory = f;
  }

  protected final CollectionFactory<C, V> factory;

  private HashMap<K, C> map = new HashMap<K, C>();

  /**
   * Returns the value associated with a key. The list is allowed to be mutated
   * and changes will be reflected in the map
   *
   * @param key
   * @return a list (never null - empty list if no keys!)
   */
  public C get(K key) {
    C val = map.get(key);
    if (val == null) {
      return factory.empty();
    } else {
      return val;
    }
  }

  public Set<Entry<K, C>> entrySet() {
    return map.entrySet();
  }

  public Set<K> keySet() {
    return map.keySet();
  }

  public Collection<C> values() {
    return map.values();
  }

  public void put(K key, V val) {
    C coll = map.get(key);
    if (coll == null) {
      coll = factory.make();
      map.put(key, coll);
    }
    coll.add(val);
  }

  public void putAll(K key, Collection<? extends V> vals) {
    C coll = map.get(key);
    if (coll == null) {
      coll = factory.make();
      map.put(key, coll);
    }
    coll.addAll(vals);
  }

  public void putAll(Map<? extends K, ? extends V> map) {
    for (Entry<? extends K, ? extends V> e : map.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  public boolean containsKey(K key) {
    return map.containsKey(key) && map.get(key).size() > 0;
  }

  public C remove(K key) {
    C res = map.remove(key);
    if (res == null) {
      return factory.empty();
    } else {
      return res;
    }
  }

  public boolean isEmpty() {
    if (map.isEmpty()) {
      return true;
    }
    Iterator<Entry<K, C>> it = map.entrySet().iterator();
    while (it.hasNext()) {
      Entry<K, C> e = it.next();
      if (e.getValue().size() == 0) {
        // Clear out empty entries
        it.remove();
      } else {
        return false;
      }
    }
    return true;
  }

  /**
   * Can't tell always if definitely empty, since it is possible that a caller
   * mutated one of the lists and made it empty. This will be correct if
   * remove() is called whenever a stored list has all its elements removed
   *
   * @return
   */
  public boolean isDefinitelyEmpty() {
    return map.isEmpty();
  }

  @Override
  /**
   * Return a shallow copy that clones map structure but doesn't clone
   * keys and values
   */
  public GenericMultiMap<C, K, V> clone() {
    GenericMultiMap<C, K, V> clone = new GenericMultiMap<C, K, V>(factory);
    copyForClone(clone);
    return clone;
  }

  /**
   * Helper for implementing clone methods
   * @param clone
   */
  protected void copyForClone(GenericMultiMap<C, K, V> clone) {
    for (Entry<K, C> e : map.entrySet()) {
      C clonedColl = factory.clone(e.getValue());
      clone.map.put(e.getKey(), clonedColl);
    }
  }

  @Override
  public String toString() {
    return map.toString();
  }

  public static interface CollectionFactory<C1 extends Collection<V1>, V1> {
    public C1 make();

    /**
     * @return empty immutable list
     */
    public C1 empty();

    public C1 clone(C1 c);
  }
}
