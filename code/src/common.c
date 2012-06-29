
/*
 * common.c
 *
 *  Created on: Jun 7, 2012
 *      Author: wozniak
 */

#include "src/common.h"

char xfer[XFER_SIZE];

int world_size;
int world_rank;
int servers;
int workers;
int my_server;
int master_server_rank;
int types_size;
int* types;
double adlb_start_time;

MPI_Comm adlb_all_comm;

MPI_Comm adlb_server_comm;
