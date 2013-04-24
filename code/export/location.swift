/*
  Helper functions for dealing with locations
 */

(location loc) location_from_rank(int rank) "turbine" "0.1.1" [
  // Currently location is represented as an integer rank,
  // so this function just down-casts the type.
  // This may change in future.
  "set <<loc>> <<rank>>"
];
