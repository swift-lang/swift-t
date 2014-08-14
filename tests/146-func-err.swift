// Duplicate argument names
// THIS-TEST-SHOULD-NOT-COMPILE

// Reuse same arg name
(int o) f (int i1, int i1) "turbine" "0.0" [
  "set <<o>> 0"
];

main {
 trace(f(1, 2));
}
