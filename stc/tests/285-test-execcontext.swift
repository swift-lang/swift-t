// Regression test for optimizations that might incorrectly execute function
// on wrong process.
// This test updated for merged engine/server

// Check that worker task doesn't run twice in same context
@dispatch=WORKER
(int o) worker_task (int i) "turbine" "0.0.1" [
  "set <<o>> <<i>>; if { $turbine::mode != \"WORK\" } { error $turbine::mode }; if [ info exists __ranhere ] { error \"Already ran here \" } ; set __ranhere 1"
];

@dispatch=CONTROL
(int o) control_task (int i) "turbine" "0.0.1" [
  "set <<o>> <<i>>; if { $turbine::mode != \"WORK\" } { error $turbine::mode }"
];

import assert;

main {
  

  // Check that optimizer doesn't pull up worker task into control context
  int x = worker_task(1);

  // Check that optimizer doesn't pull up control task into worker context
  int y = control_task(x);

  assertEqual(y, 1, "y in main");
  
  // Check that same holds after inlining
  f(2);
}

f (int i) {
  int x = worker_task(i);
  int y = control_task(x);

  assertEqual(y, 2, "y in f");
}
