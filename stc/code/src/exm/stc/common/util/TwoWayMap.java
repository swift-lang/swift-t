package exm.stc.common.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ForwardingMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

/**
 * Map that also stores the inverse mapping to allow for efficient lookup.
 * @author tim
 */
public class TwoWayMap<K, V> extends ForwardingMap<K, V> implements Map<K, V> {

  private Map<K, V> map;
  private Multimap<V, K> mapInv;

  public TwoWayMap() {
    this(new HashMap<K, V>(), ArrayListMultimap.<V, K>create());
  }

  public TwoWayMap(Map<K, V> map, Multimap<V, K> mapInv) {
    super();
    this.map = map;
    this.mapInv = mapInv;
  }

  public static <K1, V1> TwoWayMap<K1, V1> create() {
    return new TwoWayMap<K1, V1>();
  }

  @Override
  protected Map<K, V> delegate() {
    return map;
  }

  @Override
  public V remove(Object k) {
    V v = map.remove(k);
    mapInv.remove(k, v);
    return v;
  }

  @Override
  public boolean containsValue(Object value) {
    return mapInv.containsKey(value);
  }

  @Override
  public V put(K key, V value) {
    V prev = map.put(key, value);
    mapInv.remove(prev, key);
    mapInv.put(value, key);
    return prev;
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    standardPutAll(map);
  }

  public Collection<K> getByValue(V v) {
    return mapInv.get(v);
  }

  /**
   * @return an unmodifiable inverse view that will stay in sync
   */
  public Multimap<V, K> inverse() {
    return Multimaps.unmodifiableMultimap(mapInv);
  }

}
