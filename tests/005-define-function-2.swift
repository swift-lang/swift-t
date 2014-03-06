
(int output) func_a(int a, int b)
{
  // UNSET-VARIABLE-EXPECTED
  int i;
  output = 0;
}

(int output) func_b(int a, int b)
{
  int j;
  output = 0;
}

main
{
  int p;
}
