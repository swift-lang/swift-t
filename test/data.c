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
   type file;
   file d1<"file.txt">;
*/
int
main()
{
  turbine_datum_id d1 = 1;
  turbine_code code;
  code = turbine_init();
  assert(code == TURBINE_SUCCESS);
  code = turbine_datum_file_create(d1, "file.txt");
  assert(code == TURBINE_SUCCESS);
  turbine_finalize();
  puts("DONE");
}
