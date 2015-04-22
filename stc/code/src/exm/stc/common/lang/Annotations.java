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
package exm.stc.common.lang;

/**
 * Keep track of strings for annotations here
 */
public class Annotations {
  public static final String FN_PURE = "pure";
  public static final String FN_SIDE_EFFECT_FREE = "sideeffectfree";
  public static final String FN_DETERMINISTIC = "deterministic";
  public static final String FN_ASSERTION = "assertion";
  public static final String FN_COPY = "copy";
  public static final String FN_MINMAX = "minmax";
  public static final String FN_COMMUTATIVE = "commutative";
  public static final String FN_SYNC = "sync";
  public static final String FN_BUILTIN_OP = "builtin_op";
  public static final String FN_STC_INTRINSIC = "stc_intrinsic";
  public static final String FN_IMPLEMENTS = "implements";
  public static final String FN_DISPATCH = "dispatch";
  public static final String FN_PAR = "par";
  public static final String FN_DEPRECATED = "deprecated";
  public static final String FN_CHECKPOINT = "checkpoint";
  public static final String FN_SUPPRESS = "suppress";

  // Options for @suppress
  public static enum Suppression {
    UNUSED_OUTPUT,
    ;
    static public Suppression fromUserString(String s)
          throws IllegalArgumentException {
      return valueOf(s.toUpperCase());
    }
  }

  public static final String FNCALL_PAR = "par";
  public static final String FNCALL_LOCATION = "location";
  public static final String FNCALL_SOFT_LOCATION = "soft_location";
  public static final String FNCALL_PRIO = "prio";

  public static final String LOOP_UNROLL = "unroll";
  public static final String LOOP_SPLIT_DEGREE = "splitdegree";
  public static final String LOOP_LEAF_DEGREE = "leafdegree";
  public static final String LOOP_SYNC = "sync";
  public static final String LOOP_ASYNC = "async";
  public static final Object LOOP_NOSPLIT = "nosplit";
}
