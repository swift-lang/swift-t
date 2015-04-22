
// Test exists(A,i)

import io;

main
{
  int A[];
  b = exists(A, 0);
  trace("initial: ", b);
  wait (b)
  {
    A[0] = 3;
  }
  wait (A)
  {
    trace("after wait A: ", exists(A, 0));
  }
}
