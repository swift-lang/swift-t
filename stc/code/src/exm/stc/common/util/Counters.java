package exm.stc.common.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Maintain counts for objects
 *
 * @param <K> must implement equals() and hashcode()
 */
public class Counters<K> {
  private final HashMap<K, Long> map;

  public Counters() {
    this(new HashMap<K, Long>());
  }
  public Counters(HashMap<K, Long> cloned) {
    this.map = cloned;
  }

  public long increment(K key) {
    return add(key, 1);
  }

  public long decrement(K key) {
    return add(key, -1);
  }

  public long decrement(K key, long amount) {
    return add(key, -1 * amount);
  }

  public void reset(K key) {
    map.remove(key);
  }

  public void resetAll(Collection<K> keys) {
    for (K key: keys) {
      reset(key);
    }
  }

  public void resetAll() {
    map.clear();
  }

  public void merge(Counters<K> other) {
    for (Entry<K, Long> e: other.entries()) {
      add(e.getKey(), e.getValue());
    }
  }

  public long add(K key, long incr) {
    Long count = map.get(key);
    if (count == null) {
      count = incr;
    } else {
      count += incr;
    }
    if (count == 0) {
      map.remove(key);
    } else {
      map.put(key, count);
    }

    return count;
  }

  public long getCount(K key) {
    Long res = map.get(key);
    return res == null ? 0 : res;
  }

  public Map<K, Long> getCountMap() {
    return map;
  }

  /**
   * Return set of counters maintained in map.  Removals from
   * here reset the counter
   * @return
   */
  public Set<K> keySet() {
    return map.keySet();
  }

  public Set<Entry<K, Long>> entries() {
    return map.entrySet();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Counters<K> clone() {
    return new Counters<K>((HashMap<K, Long>)this.map.clone());
  }

  @Override
  public String toString() {
    return map.toString();
  }
}
