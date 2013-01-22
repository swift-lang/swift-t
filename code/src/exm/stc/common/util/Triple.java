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

public class Triple<T1, T2, T3> {
  public final T1 val1;
  public final T2 val2;
  public final T3 val3;

  public Triple(T1 val1, T2 val2, T3 val3) {
    super();
    this.val1 = val1;
    this.val2 = val2;
    this.val3 = val3;
  }
  
  public static <T1, T2, T3> Triple<T1, T2, T3> create(T1 v1, T2 v2, T3 v3) {
    return new Triple<T1, T2, T3>(v1, v2, v3);
  }
}
