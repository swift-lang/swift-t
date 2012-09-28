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
