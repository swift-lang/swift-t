
main {
  // Check that int->float promotion works ok in for loop
  for (float i = 0; i < 10; i = i + 1)
  {
    trace(i + 1.0);
  }
}
