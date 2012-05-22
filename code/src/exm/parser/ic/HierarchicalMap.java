package exm.parser.ic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import exm.parser.util.STCRuntimeError;

/**
 * An implementation of the Map interface that allows cheap creation of
 * nested maps.  If a lookup fails in the current map, then it sees if 
 * the key is in the parent map.  Attempts to remove items that appear in 
 * the parent result in a copy of the parent map.
 * 
 */
public class HierarchicalMap<K, V> implements Map<K, V> {
  private final HashMap<K, V> map;
  private HierarchicalMap<K, V> parent;
  
  public HierarchicalMap() {
    this(null);
  }
  
  private void copyOnWrite() {
    HierarchicalMap<K, V> newParent = new HierarchicalMap<K,V>();
    newParent.putAll(parent);
    parent = newParent;
  }
  
  private HierarchicalMap(HierarchicalMap<K, V> parent) {
    this.map = new HashMap<K, V>();
    this.parent = parent;
  }

  public HierarchicalMap<K, V> makeChildMap() {
    return new HierarchicalMap<K,V>(this);
  }
  public boolean containsKey(Object key) {
    return map.containsKey(key) 
        || (parent != null && parent.containsKey(key));
  }

  public V get(Object key) {
    if (map.containsKey(key)) {
      return map.get(key);
    } else if (parent != null) {
      return parent.get(key);
    } else {
      return null;
    }
  }

  public V put(K key, V value) {
    return map.put(key, value);
  }

  public void putAll(Map<? extends K, ? extends V> m) {
    map.putAll(m);
  }

  @Override
  public void clear() {
    map.clear();
    parent = null; // Don't modify parent
  }

  @Override
  public boolean containsValue(Object val) {
    throw new STCRuntimeError("not implemented");
  }

  @Override
  public HierarchicalSet<Entry<K, V>> entrySet() {
    if (parent != null) {
      return parent.entrySet().makeChild(this.map.entrySet());
    } else {
      return new HierarchicalSet<Entry<K,V>>(this.map.entrySet());
    }
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty() && (parent == null || parent.isEmpty()); 
  }

  @Override
  public HierarchicalSet<K> keySet() {
    if (parent != null) {
      return parent.keySet().makeChild(this.map.keySet());
    } else {
      return new HierarchicalSet<K>(this.map.keySet());
    }
  }
  
  @Override
  public V remove(Object key) {
    V removed = map.remove(key);
    if (parent != null) {
      if (parent.containsKey(key)) {
        copyOnWrite();
        V parentRemoved = parent.remove(key);
        if (removed == null) {
          removed = parentRemoved;
        }
      }
    }
    return removed;
  }

  @Override
  public int size() {
    int parentSize = parent == null ? 0 : parent.size();
    return parentSize + map.size();
  }

  @Override
  public Collection<V> values() {
    throw new STCRuntimeError("not implemented");
  }
  
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    int written = writeContents(sb, true);
    if (parent != null) {
      parent.writeContents(sb, written == 0);
    }
    sb.append("}");
    return sb.toString();
  }
  
  private int writeContents(StringBuilder sb, boolean first) {
    for (Entry<K, V> e: this.map.entrySet()) {
      if (first) {
        first = false;
      } else {
        sb.append(",");
      }
      sb.append(e.getKey());
      sb.append(":");
      sb.append(e.getValue());
    }
    return map.size();
  }
  
}