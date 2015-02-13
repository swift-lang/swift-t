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
 * common.c
 *
 *  Created on: Jun 7, 2012
 *      Author: wozniak
 */

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <mpi.h>

#include <tools.h>

#include "common.h"

#include "checks.h"

char xlb_xfer[ADLB_XFER_SIZE];

int xlb_comm_size;
int xlb_comm_rank = -1;
int xlb_servers;
int xlb_workers;
int xlb_my_server;
bool xlb_am_server;
bool xlb_am_leader;
int xlb_master_server_rank;
int xlb_types_size;
int* xlb_types;
bool xlb_read_refcount_enabled;
double xlb_start_time;
bool xlb_perf_counters_enabled;

MPI_Comm adlb_comm;
MPI_Comm adlb_server_comm;
MPI_Comm adlb_worker_comm;
MPI_Comm adlb_leader_comm;

int
xlb_random_server()
{
  int result = random_between(xlb_master_server_rank, xlb_comm_size);
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

adlb_code xlb_env_long(const char *env_var, long *val)
{
  char *s = getenv(env_var);
  if (s == NULL || strlen(s) == 0)
  {
    // Undefined or empty: leave val untouched
    return ADLB_NOTHING;
  }

  // Try to parse as number
  char *end = NULL;
  long tmp_val = strtol(s, &end, 10);
  CHECK_MSG(end != NULL && end != s && *end == '\0',
        "Invalid env var \"%s\": not a long int", s)

  // Whole string was number
  *val = tmp_val;
  return ADLB_SUCCESS;
}
