/*
 * xlb_time.c
 *
 *  Created on: May 22, 2019
 *      Author: wozniak
 */

#include "common.h"

double
xlb_wtime(void)
{
  return MPI_Wtime() - xlb_s.start_time;
}

