
/*
 * steal.c
 *
 *  Created on: Aug 20, 2012
 *      Author: wozniak
 */

#include <mpi.h>

#include <tools.h>

#include "common.h"
#include "debug.h"
#include "messaging.h"
#include "server.h"
#include "sync.h"
#include "steal.h"

bool
steal(void)
{
  TRACE_START;

  // Record the time of this steal attempt
  xlb_steal_last = MPI_Wtime();

  if (xlb_servers == 1)
    return false;

  // Target: another server
  int target;
  do
  {
    target = random_server();
  } while (target == xlb_world_rank);

  MPI_Request request;
  MPI_Status status;

  int rc = xlb_sync(target);
  if (rc == ADLB_SHUTDOWN)
    return false;

  int count = 0;
  IRECV(&count, 1, MPI_INT, target, ADLB_TAG_RESPONSE_STEAL_COUNT);
  int max_memory = 1;
  SEND(&max_memory, 1, MPI_INT, target, ADLB_TAG_STEAL);

  WAIT(&request, &status);
  STATS("STOLE: %i", count);

  if (count == 0)
  {
    TRACE_END;
    return false;
  }

  int length = count * sizeof(struct packed_put);
  struct packed_put* wus = malloc(length);
  RECV(wus, length, MPI_BYTE, target, ADLB_TAG_RESPONSE_STEAL);

  for (int i = 0; i < count; i++)
  {
    RECV(xfer, wus[i].length, MPI_BYTE, target,
         ADLB_TAG_RESPONSE_STEAL);
    workqueue_add(wus[i].type, wus[i].putter, wus[i].priority,
                  wus[i].answer, wus[i].target, wus[i].length, xfer);
  }
  free(wus);
  TRACE_END;
  return true;
}
