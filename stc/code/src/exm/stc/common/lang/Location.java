package exm.stc.common.lang;

/**
 * Locations
 *
 */
public class Location {
  // This is Turbine's code for ANY_LOCATION
  public static final int ANY_LOCATION_VAL = -100;
  public static final Arg ANY_LOCATION = Arg.newInt(ANY_LOCATION_VAL);

  /**
   * Check if it is an "any location" value
   * @param loc
   * @param nullIsAny interpret nulls as "any"
   * @return
   */
  public static boolean isAnyLocation(Arg loc, boolean nullIsAny) {
    if (loc == null) {
      return nullIsAny;
    } else {
      return loc.equals(ANY_LOCATION);
    }
  }



}
