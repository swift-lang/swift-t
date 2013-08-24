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
  public boolean equals(Object obj) {
    if (!(obj instanceof Pair<?, ?>)) {
      return false;
    }
    Pair<?, ?> p2 = (Pair<?, ?>)obj;
    return val1.equals(p2.val1) && val2.equals(p2.val2);
  }

  @Override
  public int hashCode() {
    return (17 * val1.hashCode()) ^ val2.hashCode();
  }
  
  @Override
  public String toString() {
    return "(" + val1.toString() + ", " + val2.toString() + ")";
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
