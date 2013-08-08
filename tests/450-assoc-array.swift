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
}
