
// SKIP-THIS-TEST #136

pragma worktypedef t0;

@dispatch=t0
  app f() { "echo" "hello" ; }

f();
