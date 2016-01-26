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
 * mpi-tools.c
 *
 *  Created on: Jul 10, 2012
 *      Author: wozniak
 */

#include <stdio.h>

#include <tools.h>

#include "mpi-tools.h"

// User may make this a noop
#ifndef NDEBUG

/**
   Assert that the actual count in given status object
   equals the expected count
   Checking the error status does not work - it is commented
   This is disabled by NDEBUG
 */
void
xlb_mpi_recv_sanity(MPI_Status* status, MPI_Datatype type, int expected)
{
  int actual;
  // printf("status: %i\n", status->MPI_ERROR);
  MPI_Get_count(status, MPI_BYTE, &actual);
  //  valgrind_assert_msg(status->MPI_ERROR == MPI_SUCCESS,
  //                      "xlb_mpi_recv_sanity: status is error!");
  valgrind_assert_msg(expected == actual,
                      "xlb_mpi_recv_sanity: expected=%i actual=%i");
}

#endif
