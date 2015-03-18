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

#include "layout.h"

#include <table.h>

#include "checks.h"
#include "debug.h"
#include "location.h"

static adlb_code
build_worker2host(const struct xlb_hostnames *hostnames,
      const xlb_layout *layout, int my_workers,
      int **worker2host, int *host_count);
static adlb_code
build_host2workers(const xlb_layout *layout, int worker_count,
      int host_count, const int *worker2host,
      struct dyn_array_i **host2workers);


adlb_code
xlb_layout_init(int comm_size, int comm_rank, int nservers,
                xlb_layout *layout)
{
  layout->size = comm_size;
  layout->rank = comm_rank;

  gdb_spin(layout->rank);

  layout->servers = nservers;
  layout->workers = layout->size - layout->servers;
  layout->master_server_rank = layout->size - layout->servers;

  layout->am_server = (layout->rank >= layout->workers);

  if (layout->am_server)
  {
    // Don't have a server: I am one
    layout->my_server = ADLB_RANK_NULL;
  }
  else
  {
    layout->my_server = xlb_map_to_server(layout, layout->rank);
  }
  DEBUG("my_server_rank: %i", layout->my_server);

  return ADLB_SUCCESS;
}

void
xlb_layout_finalize(xlb_layout *layout)
{
  // Nothing to free at the moment
}

adlb_code
xlb_wkrs_layout_init(const struct xlb_hostnames *hostnames,
                        const xlb_layout *layout,
                        xlb_wkrs_layout *workers)
{
  adlb_code ac;

  if (!layout->am_server)
  {
    workers->count = 0;
    workers->host_count = 0;
    workers->worker2host = NULL;
    workers->host2workers = NULL;
    return ADLB_SUCCESS;
  }

  workers->count = xlb_workers_count_compute(layout);

  ac = build_worker2host(hostnames, layout, workers->count,
                &workers->worker2host, &workers->host_count);
  ADLB_CHECK(ac);

  ac = build_host2workers(layout, workers->count, workers->host_count,
                          workers->worker2host, &workers->host2workers);
  ADLB_CHECK(ac);

  return ADLB_SUCCESS;
}

void
xlb_wkrs_layout_finalize(xlb_wkrs_layout *workers)
{
  if (workers->worker2host != NULL)
  {
    free(workers->worker2host);
    xlb_s.workers.worker2host = NULL;
  }

  if (workers->host2workers != NULL)
  {
    for (int i = 0; i < workers->host_count; i++)
      dyn_array_i_release(&workers->host2workers[i]);
    free(workers->host2workers);
    workers->host2workers = NULL;
  }
}

static adlb_code
build_worker2host(const struct xlb_hostnames *hostnames,
      const xlb_layout *layout, int my_workers,
      int **worker2host, int *host_count)
{
  *worker2host = malloc(sizeof((*worker2host)[0]) *
                              (size_t)my_workers);
  ADLB_MALLOC_CHECK(*worker2host);

  struct table host_name_idx_map;
  bool ok = table_init(&host_name_idx_map, 128);
  CHECK_MSG(ok, "Table init failed");

  *host_count = 0;
  for (int i = 0; i < my_workers; i++)
  {
    int rank = xlb_rank_from_my_worker_idx(layout, i);
    const char *host_name = xlb_hostnames_lookup(hostnames, rank);
    CHECK_MSG(host_name != NULL, "Unexpected error looking up host for "
              "rank %i", rank);

    unsigned long host_idx;
    if (!table_search(&host_name_idx_map, host_name, (void**)&host_idx))
    {
      host_idx = (unsigned long)(*host_count)++;
      ok = table_add(&host_name_idx_map, host_name, (void*)host_idx);
      CHECK_MSG(ok, "Table add failed");
    }
    (*worker2host)[i] = (int)host_idx;
    DEBUG("host_name_idx_map: my worker %i (rank %i) -> host %i (%s)",
          i, xlb_rank_from_my_worker_idx(layout, i), (int)host_idx,
          host_name);
  }

  table_free_callback(&host_name_idx_map, false, NULL);

  return ADLB_SUCCESS;
}

static adlb_code
build_host2workers(const xlb_layout *layout, int worker_count,
      int host_count, const int *worker2host,
      struct dyn_array_i **host2workers)
{
  bool ok;

  /* Build inverse map */
  *host2workers = malloc(sizeof((*host2workers)[0]) *
                               (size_t)host_count);
  ADLB_MALLOC_CHECK(*host2workers);

  for (int i = 0; i < host_count; i++)
  {
    ok = dyn_array_i_init(&(*host2workers)[i], 4);
    CHECK_MSG(ok, "dyn_array init failed");
  }

  for (int i = 0; i < worker_count; i++)
  {
    int host_idx = worker2host[i];
    ok = dyn_array_i_add(&(*host2workers)[host_idx], i);
    CHECK_MSG(ok, "dyn_array add failed");
  }

  return ADLB_SUCCESS;
}
