// THIS-TEST-SHOULD-NOT-COMPILE
// arguments() and main() parameters bind the same positional command line
// arguments, so a program may not use both.

arguments(int a);

main(int b) {
  trace(a, b);
}
