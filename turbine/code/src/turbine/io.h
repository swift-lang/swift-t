
/*
 * io.h
 *
 *  Created on: Sep 10, 2014
 *      Author: wozniak
 *
 *  Routines for parallel I/O
 */

#pragma once

#include <stdbool.h>

#include <mpi.h>

/**
   Broadcast *s
   Note: receivers must free *s
   @param s: The string to send/recv: in on rank 0, else out
   @param length: The length of s: out
 */
bool turbine_io_bcast(MPI_Comm comm, char** s, int* length);

bool turbine_io_copy_to(MPI_Comm comm,
                        const char* name_in, const char* name_out);
