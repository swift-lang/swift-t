import location;

@dispatch=CONTROL
f(int i) "turbine" "0.0.1" [
  "puts \"HELLO <<i>>\""
];


@dispatch=WORKER
g(int i) "turbine" "0.0.1" [
  "puts \"HELLO <<i>>\""
];

// TODO: for now we just hardcode a rank based on our
// knowledge of the layout of a Turbine cluster.
// This is hacky: would be better to have tehse function
// implemented at lower level.
(int o) random_worker() {
  o = turbine_engines();
}

(int o) random_engine() {
  o = 0;
}
main {
  @location=location_from_rank(random_engine())
  f(0);

  f(1);

  @location=location_from_rank(random_worker())
  g(2);

  g(3);
}
