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

int
main()
{
  turbine_datum_id d1 = 1;
  turbine_datum_id d2 = 2;
  turbine_datum_id d3 = 3;
  turbine_code code;
  turbine_entry entry;
  code = turbine_init();
  assert(code == TURBINE_SUCCESS);
  code = turbine_datum_container_create(d1);
  assert(code == TURBINE_SUCCESS);
  code = turbine_datum_file_create(d2, "file1.txt");
  assert(code == TURBINE_SUCCESS);
  code = turbine_datum_file_create(d3, "file2.txt");
  assert(code == TURBINE_SUCCESS);
  turbine_entry_set(&entry, "key", "1");
  code = turbine_insert(d1, &entry, d2);
  turbine_entry_set(&entry, "key", "2");
  code = turbine_insert(d1, &entry, d3);
  turbine_finalize();
  puts("DONE");
}
