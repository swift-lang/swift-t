import location;

@dispatch=CONTROL
f(int i) "turbine" "0.0.1" [
  "puts \"HELLO <<i>>\""
];


@dispatch=WORKER
g(int i) "turbine" "0.0.1" [
  "puts \"HELLO <<i>>\""
];

main {
  @location=location_from_rank(random_engine())
  f(0);

  f(1);

  @location=location_from_rank(random_worker())
  g(2);

  g(3);
}
