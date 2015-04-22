import string;

// THIS-TEST-SHOULD-NOT-RUN
// Regression test for array lookup not failing correctly

main {
  string toks[];

  toks = split("a,b,c", ",");

  trace(toks[3]); // Doesn't exist, so we should get nice error
}
