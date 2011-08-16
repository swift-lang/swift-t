/**
 * log.c
 *
 *  Created on: Aug 16, 2011
 *      Author: wozniak
 */

#include <unistd.h>

#include "src/util/log.h"

int
main()
{
  log_init();

  log_printf("hi");

  log_normalize();

  log_printf("ok");
  sleep(2);
  log_printf("bye");

  log_finalize();
}

