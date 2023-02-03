/*
 * Copyright 2015 University of Chicago and Argonne National Laboratory
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

#pragma once

#include <stdbool.h>

/**
   Struct that encapsulates MPI rank layout info
*/
typedef struct {
  /** Number of processes in total */
  int size;

  /** My rank in ADLB comm */
  int rank;

  /** Number of servers in total */
  int servers;

  /** Number of workers in total */
  int workers;

  /** Server with which this worker is associated */
  int my_server;

  /** True if this rank is a server */
  bool am_server;

  /** True if this rank is a worker leader */
  bool am_leader;

  /** Lowest-ranked server */
  int master_server_rank;

  /** Number of workers associated with this server */
  int my_workers;

  /** Number of unique hosts for my workers */
  int my_worker_hosts;

  /**
     Server-local mapping of my_worker_idx to host_idx.

     Maps value of xlb_my_worker_idx(rank) to a unique numeric index for
     host for all workers for this server.

     Indices are only applicable on this server.

     Useful for accuracy=NODE tasks
   */
  int* my_worker2host;

  /**
     Server-local mapping of host_idx to list of my_worker_idx.
     Entries are [0..my_worker_hosts - 1]
     Workers are in ascending order.
   */
  struct dyn_array_i *my_host2workers;
} xlb_layout;
