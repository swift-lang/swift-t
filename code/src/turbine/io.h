
/*
 * io.h
 *
 *  Created on: Sep 10, 2014
 *      Author: wozniak
 *
 *  Routines for parallel I/O
 */

#ifndef IO_H
#define IO_H

#include <stdbool.h>

#include <mpi.h>

/**
   Broadcast *s
   Note: receivers must free *s
 */
bool turbine_io_bcast(MPI_Comm comm, char** s);

bool turbine_io_copy_to(MPI_Comm comm,
                        const char* name_in, const char* name_out);

#endif
