
#include <stdio.h>
#include <unistd.h>

#include "g.h"

int g(int i1, int i2)
{
  int sum = i1+i2;
  printf("g: %i+%i=%i\n", i1, i2, sum);
  printf("sleeping for %i seconds...\n", sum);
  sleep(sum);
  return sum;
}
