
/*
 * common.c
 *
 *  Created on: Jun 7, 2012
 *      Author: wozniak
 */

#include <mpi.h>

#include <tools.h>

#include "common.h"

char xfer[XFER_SIZE];

int xlb_world_size;
int xlb_world_rank;
int xlb_servers;
int xlb_workers;
int xlb_my_server;
int xlb_master_server_rank;
int types_size;
int* types;
double xlb_start_time;

MPI_Comm adlb_all_comm;

MPI_Comm adlb_server_comm;

int
random_server()
{
  int result = random_between(xlb_master_server_rank, xlb_world_size);
  return result;
}

double
xlb_wtime(void)
{
  return MPI_Wtime() - xlb_start_time;
}
