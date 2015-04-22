// THIS-TEST-SHOULD-NOT-COMPILE
// Use invalid type as array key in constructor expression.

main {
  int k[] = [1,2,3];

  trace({ k: 1 }[k]);
}
