/*
 * xlb_time.c
 *
 *  Created on: May 22, 2019
 *      Author: wozniak
 */

#include "mpi.h"

#include "xlb-time.h"

double xlb_time_start = 0;

double
xlb_wtime(void)
{
  return MPI_Wtime() - xlb_time_start;
}

