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
 * server.h
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#ifndef SERVER_H
#define SERVER_H

#include "engine.h"

/** Time of last activity: used to determine shutdown */
extern double xlb_time_last_action;

extern int64_t xlb_idle_check_attempt;

/** Are we currently trying to sync with another server?
    Prevents nested syncs, which we do not support */
extern bool xlb_server_sync_in_progress;

/** Did we just get rejected when attempting to server sync? */
extern bool server_sync_retry;

/** Number of workers associated with this server */
extern int xlb_my_workers;

/** Ready task queue for server */
extern xlb_engine_work_array xlb_server_ready_work;

/**
   Server-local mapping of my_worker_idx to host_idx.

   Maps value of xlb_my_worker_idx(rank) to a unique numeric index for
   host for all workers for this server.

   Indices are only applicable on this server.

   Useful for accuracy=NODE tasks
 */
int* xlb_worker_host_map;

adlb_code xlb_server_init(void);

// ADLB_Server prototype is in adlb.h

/**
   This process has accepted a sync from a calling server
   Handle the actual RPC here
 */
adlb_code xlb_serve_server(int source);

adlb_code xlb_shutdown_worker(int worker);

/*
  master: if we're the master server
  check_attempt: if not master, the check_attempt number received
 */
bool xlb_server_check_idle_local(bool master, int64_t check_attempt);

extern bool xlb_server_shutting_down;

adlb_code xlb_server_shutdown(void);

adlb_code xlb_server_fail(int code);

/**
   Did we fail?  If so, obtain fail code.
   Given code may be NULL if caller does not require the code
 */
adlb_code xlb_server_failed(bool* aborted, int* code);

/** Get approximate time, updated frequently by server loop */
double xlb_approx_time(void);

/*
  Try to initiate a steal
 */
adlb_code xlb_try_steal(void);

/**
   @param rank rank of worker belonging to this server
   @return unique number for each of my workers, e.g. to use in array.
           Does not validate that rank is valid
 */
static inline int
xlb_my_worker_idx(int rank)
{
  return rank / xlb_servers;
}

/**
 * Inverse of xlb_my_worker_idx
 */
static inline int
xlb_rank_from_my_worker_idx(int my_worker_idx)
{
  int server_num = xlb_comm_rank - xlb_workers;
  return my_worker_idx * xlb_servers + server_num;
}

static inline int
xlb_my_workers_compute(void)
{
  int count = xlb_workers / xlb_servers;
  int server_num = xlb_comm_rank - xlb_workers;
  // Lower numbered servers may get remainder
  if (server_num < xlb_workers % xlb_servers)
  {
    count++;
  }
  return count;
}

#endif
