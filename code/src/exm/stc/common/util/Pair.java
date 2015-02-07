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
import java.util.List;


/**
 * Represent a pair of data.  Supports equality and hash comparison.
 * @param <T1>
 * @param <T2>
 */
public class Pair<T1, T2> {
  public final T1 val1;
  public final T2 val2;

  public Pair(T1 first, T2 second) {
    super();
    this.val1 = first;
    this.val2 = second;
  }
  
  public static <T1, T2> Pair<T1, T2> create(T1 f, T2 s) {
    return new Pair<T1, T2>(f, s);
  }

  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((val1 == null) ? 0 : val1.hashCode());
    result = prime * result + ((val2 == null) ? 0 : val2.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null) {
      return false;
    }
    assert(obj instanceof Pair) : "Comparing pair with non-pair";
    
    Pair<?, ?> other = (Pair<?, ?>) obj;
    if (val1 == null) {
      if (other.val1 != null)
        return false;
    } else if (other.val1 == null) {
      return false;
    } else if (!val1.equals(other.val1))
      return false;
    if (val2 == null) {
      if (other.val2 != null)
        return false;
    } else if (other.val2 == null) {
      return false;
    } else if (!val2.equals(other.val2))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "(" + val1 + ", " + val2 + ")";
  }
  
  public static <T, S> List<T> extract1(List<Pair<T, S>> list) {
    ArrayList<T> res = new ArrayList<T>(list.size());
    for (Pair<T, S> p: list) {
      res.add(p.val1);
    }
    return res;
  }
  
  public static <T, S> List<S> extract2(List<Pair<T, S>> list) {
    ArrayList<S> res = new ArrayList<S>(list.size());
    for (Pair<T, S> p: list) {
      res.add(p.val2);
    }
    return res;
  }
}
