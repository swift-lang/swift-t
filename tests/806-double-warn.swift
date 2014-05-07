
// Tests error handling: make sure warning output is clean

main {
  // UNSET-VARIABLE-EXPECTED
  // This should only cause a single unused variable warning
  int i;
}
