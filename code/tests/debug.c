/**
 * debug.c
 *
 *  Created on: Nov 16, 2011
 *      Author: wozniak
 * */

#include "src/util/debug.h"

int
main()
{
  turbine_debug_init();
  turbine_debug("TOKEN", "%s\n", "hi");
  turbine_debug_finalize();
}
