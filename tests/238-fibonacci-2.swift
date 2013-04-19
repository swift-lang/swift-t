
import assert;

// Fibonacci

// fib(0) = 0
// fib(1) = 1
// fib(k) = fib(i=k-1,j=k-2)
(int o) fib(int n)
{
  if (n) {
    if (n - 1) {
      o = fib(n - 1) + fib(n - 2);
    } else {
      // fib_1 = 1
      o = 1;
    }
  } else {
    // fib_0 = 0
    o = 0;
  }
}

// Compute fibonacci(n)
main
{
  trace(fib(8));
  assertEqual(fib(8), 21, "");
}
