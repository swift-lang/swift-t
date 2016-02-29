
#include <stdio.h>
#include <stdlib.h>

#include "b.h"

double* b(double* v, int length) {
  int i;
  double sum = 0.0;
  printf("length: %i\n", length);
  for (i = 0; i < length; i++) {
    sum += v[i];
  }
  printf("sum: %f\n", sum);
  double* result = malloc(sizeof(double));
  result[0] = sum;
  return result;
}
