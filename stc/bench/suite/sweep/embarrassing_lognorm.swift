#include <builtins.swift>
#include <io.swift>
#include <sys.swift>

@dispatch=WORKER
(float v) lognorm_work(int i, int j, float mu, float sigma) "lognorm_task" "0" "lognorm_task" [
  "set <<v>> [ lognorm_task::lognorm_task_impl <<i>> <<j>> <<mu>> <<sigma>> ] "
];

main {
  int N = toint(argv("N"));
  int M = toint(argv("M"));

  //float sleepTime = tofloat(argv("sleeptime"));
  float mu = tofloat(argv("mu"));
  float sigma = tofloat(argv("sigma"));

  // print for debug
  printf("trace: The number of arguments is: %i\n", argc());
  printf("trace: The bounds are: %i, %i\n", N, M);
  printf("trace: mu is: %f\n", mu);
  printf("trace: sigma is: %f\n", sigma);

  foreach i in [1:N] {
    foreach j in [1:M] {
      lognorm_work(i, j, mu, sigma);
    }
  }
}
