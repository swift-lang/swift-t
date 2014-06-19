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
 * mpe-tools.c
 *
 *  Created on: Aug 29, 2012
 *      Author: wozniak
 */

#include <mpi.h>

#include "common.h"
#include "mpe-tools.h"

#ifdef ENABLE_MPE

/**
   Glue component and function tokens together to make MPE event pair,
   which is just a pair of integers
 */
#define declare_pair(component, function) \
  int xlb_mpe_##component##_##function##_start, \
  xlb_mpe_##component##_##function##_end;

declare_pair(all, init);
declare_pair(all, finalize);

declare_pair(svr, busy);
declare_pair(svr, put);
declare_pair(svr, get);
declare_pair(svr, iget);
declare_pair(svr, amget);
declare_pair(svr, sync);
declare_pair(svr, steal);
declare_pair(svr, create);
declare_pair(svr, multicreate);
declare_pair(svr, subscribe);
declare_pair(svr, store);
declare_pair(svr, retrieve);

declare_pair(svr, shutdown);

declare_pair(dmn, steal);
declare_pair(dmn, sync);
declare_pair(dmn, shutdown);

declare_pair(wkr, put);
declare_pair(wkr, put_rule);
declare_pair(wkr, get);
declare_pair(wkr, iget);
declare_pair(wkr, aget);
declare_pair(wkr, amget);
declare_pair(wkr, aget_test);
declare_pair(wkr, aget_wait);
declare_pair(wkr, unique);
declare_pair(wkr, create);
declare_pair(wkr, subscribe);
declare_pair(wkr, store);
declare_pair(wkr, retrieve);
declare_pair(wkr, close);

int xlb_mpe_svr_info;

/**
   Automate MPE_Log_get_state_eventIDs calls
 */
#define make_pair(component, token) \
  MPE_Log_get_state_eventIDs(&xlb_mpe_##component##_##token##_start,\
                             &xlb_mpe_##component##_##token##_end);

/**
   Automate MPE_Log_get_solo_eventIDs calls
 */
#define make_solo(component, token) \
    MPE_Log_get_solo_eventID(&xlb_mpe_##component##_##token);


/**
  Automate MPE_Describe_state calls
 */
#define describe_pair(component, token) \
  MPE_Describe_state(xlb_mpe_##component##_##token##_start, \
                     xlb_mpe_##component##_##token##_end, \
                     "ADLB_" #component "_" #token, \
                     "MPE_CHOOSE_COLOR")

#define describe_solo(component, token) \
   MPE_Describe_event(xlb_mpe_##component##_##token, \
                      "ADLB_" #component "_" #token, \
                      "MPE_CHOOSE_COLOR");

void
xlb_mpe_setup()
{
  /* MPE_Init_log() & MPE_Finish_log() are NOT needed when liblmpe.a
        is linked because MPI_Init() would have called MPE_Init_log()
        already. */
  MPE_Init_log();

  make_pair(all, init);
  make_pair(all, finalize);

  make_pair(svr, busy);
  make_pair(svr, put);
  make_pair(svr, put_rule);
  make_pair(svr, get);

  make_pair(svr, create);
  make_pair(svr, multicreate);
  make_pair(svr, subscribe);
  make_pair(svr, store);
  make_pair(svr, retrieve);

  make_pair(svr, sync);
  make_pair(svr, steal);
  make_pair(svr, shutdown);


  make_pair(dmn, steal);
  make_pair(dmn, sync);
  make_pair(dmn, shutdown);

  make_pair(wkr, put);
  make_pair(wkr, get);

  make_pair(wkr, unique);
  make_pair(wkr, create);
  make_pair(wkr, subscribe);
  make_pair(wkr, store);
  make_pair(wkr, close);
  make_pair(wkr, retrieve);

  make_solo(svr, info);

  int rank;
  MPI_Comm_rank(MPI_COMM_WORLD, &rank);
  if (rank == 0)
  {
    describe_pair(all, init);
    describe_pair(all, finalize);

    describe_pair(svr, busy);
    describe_pair(svr, get);
    describe_pair(svr, put);
    describe_pair(svr, create);
    // describe_pair(svr, multicreate);
    describe_pair(svr, subscribe);
    describe_pair(svr, store);
    describe_pair(svr, retrieve);

    describe_pair(svr, sync);
    describe_pair(svr, steal);
    describe_pair(svr, shutdown);

    describe_pair(dmn, steal);
    describe_pair(dmn, sync);
    describe_pair(dmn, shutdown);

    describe_pair(wkr, put);
    describe_pair(wkr, get);
    describe_pair(wkr, create);
    describe_pair(wkr, store);
    describe_pair(wkr, retrieve);
    describe_pair(wkr, subscribe);
    describe_pair(wkr, close);
    describe_pair(wkr, unique);
    describe_solo(svr, info);
  }
}
#endif
