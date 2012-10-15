
#include <stdio.h>

#include "src/tools.h"

int
main()
{
  int N = 10;
  for (int i = 0; i < N; i++)
  {
    double l = i;
    double h = i+0.1;
    double d = random_between_double(l, h);
    printf("random [%0.3f,%0.3f) : %0.5f\n", l, h, d);
  }
  return 0;
}
