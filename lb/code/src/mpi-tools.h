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
 * mpi-tools.h
 *
 *  Created on: Jul 10, 2012
 *      Author: wozniak
 */

#pragma once

#include <mpi.h>

#ifndef NDEBUG
void xlb_mpi_recv_sanity(MPI_Status* status, MPI_Datatype type,
                     int expected);
#else
// NDEBUG makes this a noop
#define xlb_mpi_recv_sanity(s,t,e) (void) 0;
#endif
