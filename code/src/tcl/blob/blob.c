
/*
  BLOB.C
 */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "src/tcl/blob/blob.h"

SwiftBlob*
SwiftBlob_make_test(void)
{
  SwiftBlob* d = (SwiftBlob*) malloc(sizeof(SwiftBlob));
  char* t = (char*) malloc(64);
  sprintf(t, "howdy");
  d->pointer = t;
  d->length = strlen(t)+1;
  return d;
}

SwiftBlob*
SwiftBlob_create(long pointer, int length)
{
  SwiftBlob* result = malloc(sizeof(SwiftBlob));
  result->pointer = (void*) pointer;
  result->length = length;
  return result;
}

void*
SwiftBlob_malloc(int bytes)
{
  void* result = malloc(bytes);
  assert(result);
  return result;
}

int
SwiftBlob_sizeof_float(void)
{
  return sizeof(double);
}

void*
SwiftBlob_cast_to_ptr(int i)
{
  return (void*) i;
}

int
SwiftBlob_cast_to_int(void* p)
{
  int result = (long) p;
  return result;
}

double*
SwiftBlob_cast_int_to_dbl_ptr(int i)
{
  return (double*) (size_t) i;
}

double*
SwiftBlob_cast_to_dbl_ptr(void* p)
{
  return (double*) p;
}

double
SwiftBlob_double_get(double* pointer, int index)
{
  return pointer[index];
}

char
SwiftBlob_char_get(SwiftBlob* data, int index)
{
  char* d = (char*) data->pointer;
  return d[index];
}

/** Set p[i] = d */
void
SwiftBlob_store_double(void* p, int i, double d)
{
  double* A = (double*) p;
  A[i] = d;
}

void
SwiftBlob_free(SwiftBlob* data)
{
  free(data->pointer);
  free(data);
}
