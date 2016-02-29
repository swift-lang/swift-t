
#include <stdio.h>
#include <unistd.h>

#include "g.h"

static int state = 0;

int g(int i1, int i2)
{
  int sum = i1+i2+state;
  printf("g: %i+%i+%i=%i\n", i1, i2, state, sum);
  printf("sleeping for %i seconds...\n", sum);
  // sleep(sum);
  state++;
  return sum;
}
