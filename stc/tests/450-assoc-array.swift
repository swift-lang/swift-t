import assert;

main {
  int A[string];

  A["hello world"] = 1;
  A["goodbye"] = 2;

  foreach i, x in A {
    trace(i, x);
  }

  assertEqual(A["hello world"], 1, "key 1");
  assertEqual(A["goodbye"], 2, "key 2");


  // Hybrid array

  int B[string][int][float];

  B["one"][1][1.0] = 1;
  B["  two"][2][2.0] = 2;


  trace("B",B["one"][1][1.0], B["  two"][2][2.0]);
}
