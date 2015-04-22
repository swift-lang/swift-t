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


/**
 * Implement simple 3-valued logic: {TRUE, FALSE, MAYBE}
 */
public class TernaryLogic {

  public enum Ternary {
    TRUE,
    FALSE,
    MAYBE;
    /**
     * Or operator for ternary logic
     * @param a
     * @param b
     * @return
     */
    public static Ternary or(Ternary a, Ternary b) {
      if (a == Ternary.TRUE || b == Ternary.TRUE)
        return Ternary.TRUE;
      if (a == Ternary.MAYBE || b == Ternary.MAYBE)
        return Ternary.MAYBE;
      return Ternary.FALSE;
    }
    
    /**
     * And operator for ternary logic
     * @param a
     * @param b
     * @return
     */
    public static Ternary and(Ternary a, Ternary b) {
      if (a == Ternary.TRUE && b == Ternary.TRUE)
        return Ternary.TRUE;
      if (a == Ternary.FALSE || b == Ternary.FALSE)
        return Ternary.FALSE;
      return Ternary.MAYBE;
    }
    
    /**
     * An operator for ternary logic: if a and b agree, then we return the
     * consensus value, otherwise they disagree so we return MAYBE.
     */
    public static Ternary consensus(Ternary a, Ternary b) {
      if (a == b) {
        return a;
      } else {
        return Ternary.MAYBE;
      }
    }

    public static Ternary fromBool(boolean val) {
      if (val) {
        return TRUE;
      } else {
        return FALSE;
      }
    }
  }
}
