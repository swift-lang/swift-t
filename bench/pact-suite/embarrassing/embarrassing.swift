#include <builtins.swift>
#include <io.swift>
#include <sys.swift>

main {
  int bound = toint(argv("bound"));
  float sleepTime = tofloat(argv("sleeptime"));

  // print for debug
  printf("The number of arguments is: %i\n", argc());
  printf("The bound is: %i\n", bound);
  printf("The sleeptime is: %f\n", sleepTime);

  foreach i in [1:bound:1] {
    //TODO: variable duration - randomise?
    sleep(sleepTime);
  }
}
