/*
 * data.c
 *
 * Test elemental data operations
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#include <assert.h>
#include <stdio.h>

#include "src/turbine/turbine.h"

/**
   SwiftScript:
   file[] d1;
   file d2<"file1.txt">;
   file d3<"file2.txt">;
   d1[0] = d2;
   d2[1] = d3;
*/
int
main()
{
  turbine_datum_id d1 = 1;
  turbine_datum_id d2 = 2;
  turbine_datum_id d3 = 3;
  turbine_code code;
  code = turbine_init();
  assert(code == TURBINE_SUCCESS);
  code = turbine_datum_container_create(d1, TURBINE_ENTRY_KEY);
  assert(code == TURBINE_SUCCESS);
  code = turbine_datum_file_create(d2, "file1.txt");
  assert(code == TURBINE_SUCCESS);
  code = turbine_datum_file_create(d3, "file2.txt");
  assert(code == TURBINE_SUCCESS);
  code = turbine_insert(d1, "0", d2);
  assert(code == TURBINE_SUCCESS);
  code = turbine_insert(d1, "1", d3);
  assert(code == TURBINE_SUCCESS);
  int count = 4;
  char* keys[count];
  code = turbine_container_get(d1, keys, &count);
  assert(code == TURBINE_SUCCESS);
  printf("keys: %i\n", count);
  for (int i = 0; i < count; i++)
  {
    printf("key: %s\n", keys[i]);
    turbine_datum_id d;
    code = turbine_lookup(d1, keys[i], &d);
    assert(code == TURBINE_SUCCESS);
    printf("member: %li\n", d);
    char filename[64];
    code = turbine_filename(d, filename);
    assert(code == TURBINE_SUCCESS);
    printf("filename: %s\n", filename);
  }

  turbine_finalize();
  puts("DONE");
}
