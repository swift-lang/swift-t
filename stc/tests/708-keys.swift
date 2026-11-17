
// Test keys_string() and keys_integer().
// Key order is undetermined, so each check accepts either order.

import assert;

main
{
  // String keys
  string D[string] = { "a": "x", "b": "y" };
  string K[] = keys_string(D);
  assertEqual(size(K), 2, "size(K)");
  assert((K[0] == "a" && K[1] == "b") ||
         (K[0] == "b" && K[1] == "a"), "keys_string(D)");

  // The element type does not matter
  float F[string] = { "pi": 3.14 };
  assertEqual(keys_string(F)[0], "pi", "keys_string(F)");

  // Integer keys, returned as strings
  string L[] = keys_string([10, 20]);
  assert((L[0] == "0" && L[1] == "1") ||
         (L[0] == "1" && L[1] == "0"), "keys_string(int[])");

  int M[] = keys_integer([10, 20]);
  assert((M[0] == 0 && M[1] == 1) ||
         (M[0] == 1 && M[1] == 0), "keys_integer");

  string E[string];
  assertEqual(size(keys_string(E)), 0, "keys_string(empty)");
}
