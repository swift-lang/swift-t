/*
 * data.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#include <stdio.h>

#include "src/turbine/turbine.h"

int
main()
{
  turbine_datum_id d1;
  turbine_code code;
  code = turbine_init();
  code = turbine_datum_file_create(&d1, "file.txt");
}
