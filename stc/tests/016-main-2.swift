


(int output) func_a(int a, int b)
{
  // UNSET-VARIABLE-EXPECTED
  int i;
  output = 0;
}

(int output) rules(int a, int b)
{
  int j;
  output = 0;
}

main
{
  int k;
  k = rules(1,2);
}
