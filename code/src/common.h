/*
 * common.h
 *
 *  Created on: Jun 7, 2012
 *      Author: wozniak
 */

#ifndef COMMON_H
#define COMMON_H

#include <mpi.h>

#include "adlb-defs.h"

/** Number of processes in total */
extern int world_size;

/** My rank in MPI_COMM_WORLD */
extern int world_rank;

/** Number of servers in total */
extern int servers;

/** Number of workers in total */
extern int workers;

/** Server with which this worker is associated */
extern int my_server;

/** Lowest-ranked server */
extern int master_server_rank;

extern int my_workers;

extern int num_types;
extern int* user_types;

extern double max_malloc;

extern MPI_Comm adlb_all_comm, adlb_server_comm;

/** Start time from MPI_Wtime() */
extern double adlb_start_time;

#define  MAX_PUSH_ATTEMPTS                1000

#define XFER_SIZE (ADLB_PAYLOAD_MAX)
/** Reusable transfer buffer */
extern char xfer[];

#endif
