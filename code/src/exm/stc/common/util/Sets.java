package exm.stc.common.util;

import java.util.HashSet;
import java.util.Set;

public class Sets {
  /**
   * Return a set implementation based on the expected size:
   * use an array-based implementation for small sets, and a
   * hashTable for larger sets
   * @param maxSize estimated or actual maximum capacity
   * @return
   */
  private static final int ARRAY_SET_CUTOVER = 32;
  public static <T> Set<T> createSet(int maxSize) {
    if (maxSize <= ARRAY_SET_CUTOVER) {
      return new CopyOnWriteSmallSet<T>();
    } else {
      return new HashSet<T>();
    }
  }
}
