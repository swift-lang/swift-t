#include <builtins.swift>
#include <io.swift>
#include <sys.swift>

@dispatch=WORKER
(void v) work(int i, int j, float seconds) "turbine" "0.0.4" [
  "set <<v>> [ expr <<i>> + <<j>> ] ; if { <<seconds>> > 0 } { turbine::spin <<seconds>> }"
];

main {
  int N = toint(argv("N"));
  int M = toint(argv("M"));

  float sleepTime = tofloat(argv("sleeptime"));

  // print for debug
  printf("trace: The number of arguments is: %i\n", argc());
  printf("trace: The bounds are: %i, %i\n", N, M);
  printf("trace: The sleeptime is: %f\n", sleepTime);

  foreach i in [1:N] {
    foreach j in [1:M] {
      //TODO: variable duration - randomise?
      work(i, j, sleepTime);
    }
  }
}
