
import assert;

// Fibonacci

// fib(0) = 0
// fib(1) = 1
// fib(k) = fib(i=k-1,j=k-2)
(int o) fib(int n)
{
  switch (n) {
    case 0:
      // fib_0 = 0
      o = 0;
    case 1:
      // fib_1 = 1
      o = 1;
    default:
      o = fib(n - 1) + fib(n - 2);
  }
}

// Compute fibonacci(n)
main
{
  trace(fib(8));
  assertEqual(fib(8), 21, "");
}
