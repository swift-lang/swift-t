
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
 */
void
mpi_recv_sanity(MPI_Status* status, MPI_Datatype type, int expected)
{
  int actual;
  printf("status: %p\n", status);
  // printf("status: %i\n", status->MPI_ERROR);
  MPI_Get_count(status, MPI_BYTE, &actual);
//  valgrind_assert_msg(status->MPI_ERROR == MPI_SUCCESS,
//                      "mpi_recv_sanity: status is error!");
  valgrind_assert_msg(expected == actual,
                      "mpi_recv_sanity: expected=%i actual=%i");
}

#endif
