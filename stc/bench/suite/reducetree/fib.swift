#include <builtins.swift>
#include <sys.swift>
#include <io.swift>

// Fibonacci

@dispatch=WORKER
(int v) work(int i, int j, float seconds) "turbine" "0.0.4" [
  "set <<v>> [ expr <<i>> + <<j>> ] ; if { <<seconds>> > 0 } { turbine::spin <<seconds>> }"
];

// fib(0) = 0
// fib(1) = 1
// fib(k) = fib(k-1,k-2)
(int o) fib (int i) {
  if (i == 0) {
    o = 0;
  } else if (i == 1) {
    o = 1;
  } else {
    float sleeptime = tofloat(argv("sleeptime"));
    o = work(fib(i - 1), fib(i - 2), sleeptime);
  }
}

// Compute fibonacci(n)
main {
  argv_accept("n", "sleeptime");
  int n = toint(argv("n"));
  printf("START: n=%i", n);
  printf("DONE: fib(n)=%i", fib(n));
}
