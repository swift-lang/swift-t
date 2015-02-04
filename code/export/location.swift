/*
  Helper functions for dealing with locations
 */

@pure
(location loc) location_from_rank(int rank) {
  loc = location(rank, HARD, RANK);
}

(location loc) random_worker() {
  loc = location_from_rank(random_worker_rank());
}

(int rank) random_worker_rank() "turbine" "0.1.1" [
   "set <<loc>> [ ::turbine::random_worker ]"
];

/*
  deprecated: no longer engine/worker distinction
 */
(location loc) random_engine() {
  loc = random_worker();
}

// TODO: convert
(location rank) hostmap_one(string name) "turbine" "0.0.2" [
    "set <<rank>> [ draw [ adlb::hostmap_lookup <<name>> ] ]"
];

// TODO: convert
(location rank) hostmap_one_worker(string name) "turbine" "0.0.2" [
    "set <<rank>> [ ::turbine::random_rank WORKER [ adlb::hostmap_lookup <<name>> ] ]"
];

(string results[]) hostmap_list() "turbine" "0.4.0" "hostmap_list";
