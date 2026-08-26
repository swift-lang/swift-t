// THIS-TEST-SHOULD-NOT-COMPILE
// arguments() binds top-level variables, so it is only allowed at the
// top level of the program, not inside a function body.

main {
  arguments(int v);
  trace(v);
}
