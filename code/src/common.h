/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */


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
#include "adlb_types.h"

/** Number of processes in total */
extern int xlb_comm_size;

/** My rank in MPI_COMM_WORLD */
extern int xlb_comm_rank;

/** Number of servers in total */
extern int xlb_servers;

/** Number of workers in total */
extern int xlb_workers;

/** Server with which this worker is associated */
extern int xlb_my_server;

/** True if this rank is a server */
extern bool xlb_am_server;

/** Lowest-ranked server */
extern int xlb_master_server_rank;

/** Number of work unit types */
extern int xlb_types_size;

/** Array of allowed work unit types */
extern int* xlb_types;

/** Whether read refcounting and memory freeing is enabled */
extern bool xlb_read_refcount_enabled;

/** Whether to maintain performance counters */
extern bool xlb_perf_counters_enabled;

extern double max_malloc;

extern MPI_Comm adlb_comm, adlb_server_comm, adlb_worker_comm;

/**
   Start time from MPI_Wtime()
   Note: this is used by debugging output
 */
extern double xlb_start_time;

#define  MAX_PUSH_ATTEMPTS                1000

#define XLB_XFER_SIZE (ADLB_PAYLOAD_MAX)
/** Reusable transfer buffer */
extern char xlb_xfer[];
static const adlb_buffer xlb_xfer_buf =
            { .data = xlb_xfer, .length = XLB_XFER_SIZE };

int xlb_random_server(void);

/**
   Time since XLB was initialized
   Note: this is used by debugging output
 */
double xlb_wtime(void);

/**
   Given a work_type, obtain its index in xlb_types
 */
int xlb_type_index(int work_type);

/**
    Get long int from env var.  If not present, val is unmodified
 */
adlb_code xlb_env_long(const char *env_var, long *val);

#endif
