
/*
 * common.c
 *
 *  Created on: Jun 7, 2012
 *      Author: wozniak
 */

#include <stdio.h>

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
int xlb_types_size;
int* xlb_types;
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

int
xlb_type_index(int work_type)
{
  for (int i = 0; i < xlb_types_size; i++)
    if (xlb_types[i] == work_type)
      return i;
  printf("get_type_idx: INVALID type %d\n", work_type);
  return -1;
}
