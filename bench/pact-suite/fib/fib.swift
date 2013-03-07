#include <builtins.swift>
#include <sys.swift>
#include <io.swift>

// Fibonacci

// fib(0) = 0
// fib(1) = 1
// fib(k) = fib(k-1,k-2)
(int o) fib (int i) {
  if (i == 0) {
    o = 0;
  } else if (i == 1) {
    o = 1;
  } else {
    o = fib(i - 1) + fib(i - 2);
  }
}

// Compute fibonacci(n)
main {
  argv_accept("n");
  int n = toint(argv("n"));
  printf("START: n=%i", n);
  printf("DONE: fib(n)=%i", fib(n));
}
