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

#ifndef __LAYOUT_H
#define __LAYOUT_H

#include <assert.h>
#include <stdbool.h>

#include "adlb-defs.h"
#include "location.h"

/**
  Struct that encapsulates MPI rank layout info
 */
struct xlb_layout {
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
};

struct xlb_workers_layout {
  /** Number of workers associated with this server */
  int count;

  /** Number of unique hosts for my workers */
  int host_count;

  /**
     Server-local mapping of my_worker_idx to host_idx.

     Maps value of xlb_my_worker_idx(rank) to a unique numeric index for
     host for all workers for this server.

     Indices are only applicable on this server.

     Useful for accuracy=NODE tasks
   */
  int* worker2host;

  /**
     Server-local mapping of host_idx to list of my_worker_idx.
     Entries are [0..xlb_my_worker_hosts - 1]
     Workers are in ascending order.
   */
  struct dyn_array_i *host2workers;
};

adlb_code
xlb_layout_init(MPI_Comm comm, int nservers, struct xlb_layout *layout);

void
xlb_layout_finalize(struct xlb_layout *layout);

/**
  Setup layout of workers for this server
 */
adlb_code
xlb_workers_layout_init(const struct xlb_hostnames *hostnames,
                        const struct xlb_layout *layout,
                        struct xlb_workers_layout *workers);

void
xlb_workers_layout_finalize(struct xlb_workers_layout *workers);


// Small and frequently used functions to inline for performance
__attribute__((always_inline))
static inline bool
xlb_is_server(const struct xlb_layout *layout,int rank)
{
  return (rank >= layout->workers);
}

/**
   @param rank of worker
   @return rank of server for this worker rank
 */
__attribute__((always_inline))
static inline int
xlb_map_to_server(const struct xlb_layout* layout, int rank)
{
  if (xlb_is_server(layout, rank))
    return rank;
  assert(rank >= 0 && rank < layout->workers);
  int w = rank % layout->servers;
  return w + layout->workers;
}

__attribute__((always_inline))
static inline int
xlb_worker_maps_to_server(const struct xlb_layout* layout,
                         int worker_rank, int server_rank) {
  return (worker_rank % layout->servers) +
          layout->workers == server_rank;
}

/**
   @param rank rank of worker belonging to this server
   @return unique number for each of my workers, e.g. to use in array.
           Does not validate that rank is valid
 */
static inline int
xlb_my_worker_idx(const struct xlb_layout* layout, int rank)
{
  return rank / layout->servers;
}

/**
 * Inverse of xlb_my_worker_idx
 */
static inline int
xlb_rank_from_my_worker_idx(const struct xlb_layout* layout,
                            int my_worker_idx)
{
  int server_num = layout->rank - layout->workers;
  return my_worker_idx * layout->servers + server_num;
}

static inline int
xlb_workers_count_compute(const struct xlb_layout* layout)
{
  int count = layout->workers / layout->servers;
  int server_num = layout->rank - layout->workers;
  // Lower numbered servers may get remainder
  if (server_num < layout->workers % layout->servers)
  {
    count++;
  }
  return count;
}

#endif // __LAYOUT_H
