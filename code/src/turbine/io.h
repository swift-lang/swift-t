
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

bool turbine_io_copy_to(MPI_Comm comm,
                        const char* name_in, const char* name_out);

#endif
