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
  }
}
