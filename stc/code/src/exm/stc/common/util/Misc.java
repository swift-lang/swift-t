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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.Multimap;

import exm.stc.common.exceptions.STCRuntimeError;
import exm.stc.common.lang.Types.Type;

/**
 * Miscellaneous utility functions
 * @author wozniak
 * */
public class Misc {
  static DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

  /**
     @return Current time formatted as human-readable String
   */
  public static String timestamp() {
    return df.format(new Date());
  }

  public static String stackTrace(Throwable e) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    return sw.toString();
  }

  /**
   * Invert mapping, assuming surjective (i.e. each key maps to unique value)
   * @param m
   * @return
   */
  public static <K, V> Map<V, K> invertSurjective(Map<K, V> m) {
    Map<V, K> result = new HashMap<V, K>();
    for (Map.Entry<K, V> e: m.entrySet()) {
      K k = e.getKey();
      V v = e.getValue();
      K prev = result.put(v, k);
      if (prev != null) {
        throw new STCRuntimeError("Duplicate value when inverting: "
                             + v + " for keys " + prev + ", " + k);
      }
    }

    return result;
  }

  public static void putAllMultimap(Multimap<String, Type> dst, Map<String, Type> src) {
    for (Entry<String, Type> e: src.entrySet()) {
      dst.put(e.getKey(), e.getValue());
    }
  }
}
