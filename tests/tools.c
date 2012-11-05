
/*
 * tools.c
 *
 *  Created on: Nov 5, 2012
 *      Author: wozniak
 *
 * Test miscellaneous tools
 */

#include <stdio.h>

#include "src/tools.h"

int
main()
{
  int n = 10;
  long A[10];
  for (int i = 0; i < n; i++)
    A[i] = i;
  print_longs(A, n);
  printf("\n");
  shuffle(A, n);
  print_longs(A, n);
  printf("\n");
  printf("DONE\n");
}
