
/**
 * ADLB SERVER
 *
 **/

#include <assert.h>

#include <mpi.h>

#include <memory.h>

#include "adlb.h"
#include "adlb-mpe.h"
#include "checks.h"
#include "common.h"
#include "data.h"
#include "debug.h"
#include "handlers.h"
#include "messaging.h"
#include "server.h"

/** Number of workers linked to this server */
int my_workers;

/** Time of last activity: used to determine shutdown */
double time_last_action;

/** Time after which to shutdown because idle */
double time_max_idle;

adlb_code
adlb_server_init()
{
  my_workers = 0;

  printf("SERVER for ranks: ");
  for (int i = 0; i < workers; i++)
  {
    if (adlb_map_to_server(i) == world_rank)
    {
      my_workers++;
      printf("%i ", i);
    }
  }
  return ADLB_SUCCESS;
}

/**
   @param rank of worker
   @return rank of server for this worker rank
 */
int
adlb_map_to_server(int worker)
{
  assert(worker > 0);
  assert(worker < world_size);
  int w = worker % servers;
  return w + workers;
}

static adlb_code setup_idle_time(void);

bool check_idle(void);

adlb_code
ADLBP_Server(long max_memory)
{
  bool done = false;
  int from_rank, tag;

  /** Returns from ADLB */
  adlb_code code;

  MPI_Request *temp_req;

  data_init(servers, world_rank);
  code = setup_idle_time();
  ADLB_CHECK(code)
  mm_set_max(mm_default, max_memory);
  handlers_init();

  // Default: May be overridden below:
  time_max_idle = 5.0;

  code = setup_idle_time();
  assert(code == ADLB_SUCCESS);

  done = 0;
  while (! done)
  {
    if (check_idle())
      break;

    int new_message;
    MPI_Status status;
    // May want to switch to PMPI call for speed
    int rc = MPI_Iprobe(MPI_ANY_SOURCE, MPI_ANY_TAG, adlb_all_comm,
                        &new_message, &status);
    MPI_CHECK(rc);
    if (!new_message)
      continue;

    from_rank = status.MPI_SOURCE;
    tag = status.MPI_TAG;

    CHECK_MSG(handler_valid(tag) ,
              "ADLB_Server: invalid tag: %i\n", tag);

    // Call appropriate RPC handler:
    code = handle(tag, from_rank);
    ADLB_CHECK(code);
  }
  DEBUG("ADLB_Server(): DONE\n");
  return ADLB_SUCCESS;
}

adlb_code
send_work(int requester, int type, int priority, int answer,
          int target, int length, void* payload)
{
  int rc = MPI_Send(&length, 1, MPI_INT, requester,
                    ADLB_TAG_WORKUNIT, adlb_all_comm);
  MPI_CHECK(rc);
  return ADLB_SUCCESS;
}

/**
    Allow user to override ADLB exhaustion interval
 */
adlb_code
setup_idle_time()
{
   char *s = getenv("ADLB_EXHAUST_TIME");
   if (s != NULL &&
       strlen(s) > 0)
   {
     int c = sscanf(s, "%lf", &time_max_idle);
     if (c != 1 || time_max_idle <= 0)
     {
       printf("Illegal value of ADLB_EXHAUST_TIME!\n");
       return ADLB_ERROR;
     }
   }
   time_last_action = MPI_Wtime();
   return ADLB_SUCCESS;
}

/**
   Am I the master server? (server with lowest rank)
 */
static bool
master_server()
{
  return (world_rank == workers);
}

/**
   @return true when idle
 */
bool
check_idle()
{
  if (! master_server())
    return false;

  // Current time
  double t = MPI_Wtime();
  // Time idle
  double idle = t - time_last_action;
  if (idle < time_max_idle)
    // Not idle
    return false;

  // Issue idle check RPCs...
  return true;
}
