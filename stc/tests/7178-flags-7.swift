// THIS-TEST-SHOULD-NOT-COMPILE
// flags() is only allowed at the top level of the main program

main {
  flags(int a = 0);
  trace(a);
}
