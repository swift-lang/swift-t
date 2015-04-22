import assert;
// Test contains function
// This is intended to exercise the actual function, as well as type inference
main {
  // Check autodeclare for string subscripts  
  A["hello world"] = 1;
  A["goodbye"] = 2;

  assert(contains(A, "hello world"), "contains1");
  // Check for possible whitespace issues
  assert(!contains(A, "hello world "), "contains2");
}
