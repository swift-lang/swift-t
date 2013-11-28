
#include <stdio.h>
#include <stdlib.h>

double* malloc_double()
{
  return (double*) malloc(sizeof(double));
}

void print_double(double* d)
{
  printf("%f\n", *d);
}

void free_double(double* d)
{
  free(d);
}

