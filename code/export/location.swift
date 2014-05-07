/*
  Helper functions for dealing with locations
 */

@pure
(location loc) location_from_rank(int rank) "turbine" "0.1.1" [
  // Currently location is represented as an integer rank,
  // so this function just down-casts the type.
  // This may change in future.
  "set <<loc>> <<rank>>"
];

(location loc) random_worker() "turbine" "0.1.1" [
   "set <<loc>> [ ::turbine::random_worker ]"
];

/*
  deprecated: no longer engine/worker distinction
 */
(location loc) random_engine() "turbine" "0.1.1" [
   "set <<loc>> [ ::turbine::random_worker ]"
];

(location rank) hostmap_one(string name) "turbine" "0.0.2" [
    "set <<rank>> [ draw [ adlb::hostmap_lookup <<name>> ] ]"
];

(location rank) hostmap_one_worker(string name) "turbine" "0.0.2" [
    "set <<rank>> [ ::turbine::random_rank WORKER [ adlb::hostmap_lookup <<name>> ] ]"
];

(string results[]) hostmap_list() "turbine" "0.4.0" "hostmap_list";
