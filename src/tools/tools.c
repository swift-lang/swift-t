/*
 * tools.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#include "src/tools/tools.h"

int array_length(void** array)
{
  int result = 0;
  while (*array)
  {
    array++;
    result++;
  }
  return result;
}
