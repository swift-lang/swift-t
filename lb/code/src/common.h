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

#pragma once

#include <mpi.h>

#include <dyn_array_i.h>

#include "adlb-defs.h"
#include "adlb_types.h"
#include "layout-defs.h"

/**
   Struct that encapsulates xlb system state.
 */
struct xlb_state
{
  /*
    MPI communicators for everything and subgroups
   */
  MPI_Comm comm;
  MPI_Comm server_comm;
  MPI_Comm worker_comm;
  MPI_Comm leader_comm;

  char* my_name;

  /**
     Start time from MPI_Wtime()
     Note: this is used by debugging output
   */
  double start_time;

  /**
     General layout info
   */
  xlb_layout layout;

  /**
     Map host to rank-list.
     Ranks in the list are ordered lowest->highest
   */
  struct xlb_hostmap hostmap;

  /** Number of work unit types */
  int types_size;

  /** Whether read refcounting and memory freeing is enabled */
  bool read_refc_enabled;

  /** Whether to maintain performance counters */
  bool perfc_enabled;

  double max_malloc;

  /** Default placement policy to use for new data */
  adlb_placement placement;

  /** If not 1, use par_mod mode where parallel tasks start on
      ranks r such that r % par_mod == 0
   */
  int par_mod;

  /** Overall status */
  adlb_status status;
};

/** Global system state */
extern struct xlb_state xlb_s;

#define  MAX_PUSH_ATTEMPTS                1000

/** Reusable transfer buffer */
extern char xlb_xfer[];
static const adlb_buffer xlb_xfer_buf =
            { .data = xlb_xfer, .length = ADLB_XFER_SIZE };

int xlb_random_server(void);

/**
    Get long int from env var.  If not present, val is unmodified
    TODO: Replace with use of tools.h
 */
adlb_code xlb_env_long(const char *env_var, long *val);

/**
    Get placement policy setting from environment.
 */
adlb_code xlb_env_placement(adlb_placement *placement);
