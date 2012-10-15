
/**
 * test/version.c
 *
 *  Created on: Dec 31, 2011
 *      Author: wozniak
 * */

#include <stdio.h>

#include "src/version.h"

int
main()
{
  version v1, v2, v3;
  char c[64];

  version_init(&v1, 1, 2, 6);
  version_parse(&v2, "2.1.6");
  version_init(&v3, 1, 2, 5);

  int count = version_to_string(&c[0], &v1);
  printf("version: %s\n", c);
  printf("count: %i\n", count);
  int b;
  b = version_cmp(&v1, &v2);
  printf("b1: %i\n", b);
  b = version_cmp(&v1, &v3);
  printf("b2: %i\n", b);
  b = version_cmp(&v2, &v3);
  printf("b3: %i\n", b);
}
