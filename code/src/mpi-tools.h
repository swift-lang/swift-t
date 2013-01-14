
/*
 * mpi-tools.h
 *
 *  Created on: Jul 10, 2012
 *      Author: wozniak
 */

#ifndef MPI_TOOLS_H
#define MPI_TOOLS_H

#include <mpi.h>

#ifndef NDEBUG
void mpi_recv_sanity(MPI_Status* status, MPI_Datatype type,
                     int expected);
#else
// User may make this a noop
#define mpi_recv_sanity(s,t,e) (void) 0;
#endif

#endif
