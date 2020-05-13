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

#include "adlb-defs.h"
#include "layout-defs.h"
#include "location.h"

adlb_code
xlb_layout_init(int comm_size, int comm_rank, int nservers,
		const struct xlb_hostnames* hostnames,
		xlb_layout* layout);

void
xlb_layout_finalize(xlb_layout *layout);

// Small and frequently used functions to inline for performance
__attribute__((always_inline))
static inline bool
xlb_is_server(const xlb_layout *layout,int rank)
{
  return (rank >= layout->workers);
}

/**
   @param rank of worker
   @return rank of server for this worker rank
 */
__attribute__((always_inline))
static inline int
xlb_map_to_server(const xlb_layout* layout, int rank)
{
  if (xlb_is_server(layout, rank))
    return rank;
  assert(rank >= 0 && rank < layout->workers);
  int w = rank % layout->servers;
  return w + layout->workers;
}

__attribute__((always_inline))
static inline int
xlb_worker_maps_to_server(const xlb_layout* layout,
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
xlb_my_worker_idx(const xlb_layout* layout, int rank)
{
  return rank / layout->servers;
}

/**
 * Inverse of xlb_my_worker_idx
 */
static inline int
xlb_rank_from_my_worker_idx(const xlb_layout* layout,
                            int my_worker_idx)
{
  int server_num = layout->rank - layout->workers;
  return my_worker_idx * layout->servers + server_num;
}

__attribute__((always_inline))
static inline int host_idx_from_rank(const xlb_layout *layout, int rank)
{
  assert(xlb_worker_maps_to_server(layout, rank, layout->rank));

  return layout->my_worker2host[xlb_my_worker_idx(layout, rank)];
}

#endif // __LAYOUT_H
