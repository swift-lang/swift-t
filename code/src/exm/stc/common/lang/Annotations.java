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
  public static final Object FN_BUILTIN_OP = "builtin_op";
  public static final Object FN_DISPATCH = "dispatch";

  public static final String LOOP_UNROLL = "unroll";
  public static final String LOOP_SPLIT_DEGREE = "splitdegree";
  public static final String LOOP_SYNC = "sync";
  public static final String LOOP_ASYNC = "async";
  public static final Object LOOP_NOSPLIT = "nosplit";
  
}
