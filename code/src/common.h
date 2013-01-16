
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
extern int xlb_world_size;

/** My rank in MPI_COMM_WORLD */
extern int xlb_world_rank;

/** Number of servers in total */
extern int xlb_servers;

/** Number of workers in total */
extern int xlb_workers;

/** Server with which this worker is associated */
extern int xlb_my_server;

/** Lowest-ranked server */
extern int xlb_master_server_rank;

/** Number of work unit types */
extern int xlb_types_size;

/** Array of allowed work unit types */
extern int* xlb_types;

extern double max_malloc;

extern MPI_Comm adlb_all_comm, adlb_server_comm;

/**
   Start time from MPI_Wtime()
   Note: this is used by debugging output
 */
extern double xlb_start_time;

#define  MAX_PUSH_ATTEMPTS                1000

#define XFER_SIZE (ADLB_PAYLOAD_MAX)
/** Reusable transfer buffer */
extern char xfer[];

int random_server(void);

/**
   Time since XLB was initialized
   Note: this is used by debugging output
 */
double xlb_wtime(void);

/**
   Given a work_type, obtain its index in xlb_types
 */
int xlb_type_index(int work_type);

#endif
