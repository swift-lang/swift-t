
/**
 * ptr-array.c
 *
 * Test ptr-array functionality
 *
 *  Created on: Feb 17, 2015
 *      Author: wozniak
 */


#include <stdio.h>

#include <ptr_array.h>

int
main()
{
  struct ptr_array pa;
  ptr_array_init(&pa, 8);

  printf("DONE\n");
  return 0;
}
