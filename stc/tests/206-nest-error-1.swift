
// THIS-TEST-SHOULD-NOT-COMPILE

main
{
  int a;
  a = 1;

  {
    int b;
    b = 1;
  }

  {
    int b;
    b = 1;
    // This is a double definition in a nested scope:
    int b;
  }
}
