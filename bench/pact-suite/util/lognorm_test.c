#include <stdio.h>
#include "bench_util.h"
int main () {
  for (int i = 0; i < 100; i++)
  {
    //printf("%f\n", lognorm_sample(-6.905, 1));
  }

  fprintf(stderr, "START\n");
  spin(2);
  fprintf(stderr, "STOP\n");
}
