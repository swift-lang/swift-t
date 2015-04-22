// Test calling functions with non-integer array keys
import io;


main {
  string test[string][float];

  f(test);

  string hello = "hello";
  string bye1 = "bye bye";
  test[hello][0.1] = "foo";
  test["hello"][0.2] = "bar";
  test[bye1][3.14] = "baz";
  test["bye bye"][-1] = "santa";
  test["bye bye "][2.71] = "rudolph";

}


f (string A[string][float]) {
    foreach B, i in A {
        foreach s, j in B {
            printf("[%s][%.2f]=%s", i, j, s);
        }
    }
}
