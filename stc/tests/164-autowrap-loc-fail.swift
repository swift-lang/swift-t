import location;
//THIS-TEST-SHOULD-NOT-COMPILE
// Check that we can't give location to local task


// Should be local by default
f(int i) "turbine" "0.0.1" [
  "puts \"HELLO <<i>>\""
];

main {
  @location=location_from_rank(0)
  f(0);

  f(1);

}
