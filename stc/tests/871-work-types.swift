
// THIS-TEST-SHOULD-NOT-RUN

pragma worktypedef t0;

@dispatch=t0
  f() "turbine" "1.0"
  [ "puts hello" ];

f();
