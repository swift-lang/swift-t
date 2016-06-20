/*
  Helper functions for dealing with locations
 */

@pure
(location loc) rank2location(int rank) {
  loc = location(rank, HARD, RANK);
}
@pure
(location loc) locationFromRank(int rank) {
  loc = location(rank, HARD, RANK);
}

/*
  deprecated: old naming scheme
 */
@pure
(location loc) location_from_rank(int rank) {
  loc = locationFromRank(rank);
}

(location loc) randomWorker() {
  loc = locationFromRank(randomWorkerRank());
}

/*
  deprecated: old naming scheme
 */
(location loc) random_worker() {
  loc = randomWorker();
}

(int rank) randomWorkerRank() "turbine" "0.1.1" [
   "set <<rank>> [ ::turbine::random_worker ]"
];

/*
  deprecated: no longer engine/worker distinction
 */
(location loc) random_engine() {
  loc = random_worker();
}

(location loc) hostmapOne(string name) {
  loc = locationFromRank(hostmapOneRank(name));
}

/*
  deprecated: old naming scheme
 */
(location loc) hostmap_one(string name) {
  loc = hostmapOne(name);
}

(int rank) hostmapOneRank(string name) "turbine" "0.0.2" [
    "set <<rank>> [ draw [ adlb::hostmap_lookup <<name>> ] ]"
];

(location loc) hostmapOneWorker(string name) {
  loc = locationFromRank(hostmapOneWorkerRank(name));
}

/*
  deprecated: old naming scheme
 */
(location loc) hostmap_one_worker(string name) {
  loc = hostmapOneWorker(name);
}

(int rank) hostmapOneWorkerRank(string name) "turbine" "0.0.2" [
    "set <<rank>> [ ::turbine::random_rank WORKER [ adlb::hostmap_lookup <<name>> ] ]"
];

/*
  List available hosts
  Pure because it will be same throughout the run
 */
(string results[]) hostmapList() "turbine" "0.7.0" [
  "set <<results>> [ ::turbine::hostmap_list ]"
];

/*
  deprecated: old naming scheme
 */
(string results[]) hostmap_list() {
  results = hostmapList();
}
