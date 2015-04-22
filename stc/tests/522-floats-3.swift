
// Check that promotion of function args works
() printfloat (float x) {
  trace(x);
}

main () {
  printfloat(1.0);
  printfloat(1); // Should be promoted
  printfloat(1+1); // Should be promoted
  float x = 1 + 1 * 2;
  printfloat(x);
}
