/*
  Code sketch for generic async executor interface

  Created by Tim Armstrong, Nov 2013

Architecture
------------
* Servers + regular workers + async work managers.
* Each async work manager has multiple async tasks executing
  simultaneously
* We want to be able to execute arbitrary code before and after each
  task on the worker node (to fetch data, store data, etc).
  - When starting a task, the async work manager must execute
    compiler-generated code.  This code is responsible for launching
    the async task.  I.e. async worker gets task, async worker executes
    task, hands control to task code, task code calls function to
    launch async task, thereby returning control to async work manager
    temporarily.
  - Each async work task has two callbacks associated that are called
    back in case of success/failure.


Assumptions
-----------
* 1 ADLB worker per work-type, with N slots
* Compiler generates code with 1 work unit per task, plus optionally a
  chain of callbacks that may also contain 1 work unit of that type each
* Add ability to request multiple tasks


Implications of Assumptions
---------------------------
* 1 ADLB Get per slot to fill
* ADLB get only needs to get one work type
* Can do blocking ADLB get if no work
* Have to check slots after executing each task or callback:
  async worker code doesn't know if task code added work.

 */

#include <assert.h>

#include <adlb.h>
#include "src/turbine/turbine-defs.h"
    

// To be replaced with correct check
#define TMP_ADLB_CHECK(code) assert((code) == ADLB_SUCCESS)
#define TMP_WARN(fmt, args...) fprintf(stderr, fmt "\n", ##args)

#define TMP_EXEC_CHECK(code) assert((code) == TURBINE_EXEC_SUCCESS)

/*
  Represent state of a completed task
 */
typedef struct {
  bool success;
  // TODO: callbacks
} turbine_completed_task;

typedef enum {
  TURBINE_EXEC_SUCCESS,
  TURBINE_EXEC_ERROR,
  TURBINE_EXEC_SHUTDOWN,
  // TODO: more info - e.g. if bad arg, or invalid state
} turbine_exec_code;

/*
  Info about available/used slots in executor
 */
typedef struct {
  int used;
  int total;
} turbine_exec_slot_state;

// Function pointer types.  All are passed void state pointer for
// any state needed
typedef turbine_exec_code (*turbine_exec_shutdown)(void *state);

// Polling: periodically called on an executor with active tasks.
//          updates completed with completed task info
typedef turbine_exec_code (*turbine_exec_poll)(void *state,
          turbine_completed_task **completed, int *ncompleted);

// Waiting: called on an executor with active tasks.
//          updates completed with completed task info
typedef turbine_exec_code (*turbine_exec_wait)(void *state,
          turbine_completed_task **completed, int *ncompleted);

// Slots: return count of slots
typedef turbine_exec_code (*turbine_exec_slots)(void *state,
                                  turbine_exec_slot_state *slots);

// Executor notification model
// TODO: only polling based currently used
typedef enum
{
  EXEC_POLLING, /* We have to periodically poll for status */
  //EXEC_BG_THREAD, /* Executor has background thread */
} async_exec_notif;

typedef struct {
  const char *name;
  int adlb_work_type; // Type to request from adlb
  async_exec_notif notif_mode;

  void *state; // Internal state to pass to executor functions

  /*
    Function pointers for executors
   */
  turbine_exec_shutdown shutdown;
  turbine_exec_wait wait;
  turbine_exec_poll poll;
  turbine_exec_slots slots;
} turbine_executor;

static turbine_exec_code
get_tasks(turbine_executor *executor, void *buffer, int buffer_size,
          bool poll, int max_tasks);

static turbine_exec_code
check_tasks(turbine_executor *executor, bool poll);

static void
shutdown_executors(turbine_executor *executors, int nexecutors);

void init_coasters_executor(turbine_executor *exec /*TODO: coasters params */)
{
  exec->name = "Coasters";
  exec->adlb_work_type = 2;
  // TODO: could make use of background thread
  exec->notif_mode = EXEC_POLLING;

  exec->state = NULL; // TODO: pointer to coasters client
  exec->shutdown = NULL; // TODO: shutdown
  exec->poll = NULL;
  exec->wait = NULL;
  exec->slots = NULL;
}


turbine_code async_worker_loop(turbine_executor *executor,
                      void *buffer, int buffer_size) {
  // Check we were initialised properly
  assert(executor != NULL);
  // TODO: check buffer large enough for work units

  turbine_exec_code ec;
  while (true)
  {
    turbine_exec_slot_state slots;
    ec = executor->slots(executor->state, &slots);
    TMP_EXEC_CHECK(ec);

    if (slots.used < slots.total)
    {
      int max_tasks = slots.total - slots.used;

      // Need to do non-blocking get if we're polling executor too
      bool poll = (slots.used != 0);
      
      ec = get_tasks(executor, buffer, buffer_size, poll, max_tasks);
      if (ec == TURBINE_EXEC_SHUTDOWN)
      {
        break;
      }
      TMP_EXEC_CHECK(ec);
    }

    // Update count in case work added 
    ec = executor->slots(executor->state, &slots);
    TMP_EXEC_CHECK(ec);

    if (slots.used > 0)
    {
      // Need to do non-blocking check if we want to request more work
      bool poll = (slots.used < slots.total);
      ec = check_tasks(executor, poll);
      TMP_EXEC_CHECK(ec);
    }
  }

  shutdown_executors(executor, 1);

  return TURBINE_SUCCESS;
}

/*
 * Get tasks from adlb and execute them.
 * TODO: currently only executes one task, but could do multiple
 */
static turbine_exec_code
get_tasks(turbine_executor *executor, void *buffer, int buffer_size,
          bool poll, int max_tasks)
{
  adlb_code ac;

  int work_len, answer_rank, type_recved;
  bool got_work;
  if (poll)
  {
    ac = ADLB_Iget(executor->adlb_work_type, buffer, &work_len,
                    &answer_rank, &type_recved);
    TMP_ADLB_CHECK(ac);

    got_work = (ac != ADLB_NOTHING);
  }
  else
  {
    MPI_Comm tmp_comm;
    ac = ADLB_Get(executor->adlb_work_type, buffer, &work_len,
                    &answer_rank, &type_recved, &tmp_comm);
    TMP_ADLB_CHECK(ac);
    
    if (ac == ADLB_SHUTDOWN)
    {
      return TURBINE_EXEC_SHUTDOWN;
    }
    got_work = true;
  }
  
  if (got_work)
  {
    // TODO: tcl_eval task
  }

  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
check_tasks(turbine_executor *executor, bool poll)
{
  turbine_exec_code ec;
  int ncompleted;
  turbine_completed_task *completed;
  if (poll)
  {
    ec = executor->poll(executor->state, &completed, &ncompleted);
    TMP_EXEC_CHECK(ec);
  }
  else
  {
    ec = executor->wait(executor->state, &completed, &ncompleted);
    TMP_EXEC_CHECK(ec);
  }

  for (int i = 0; i < ncompleted; i++)
  {
    if (completed[i].success)
    {
      // TODO: eval success callback
    }
    else
    {
      // TODO: eval fail callback
    }
  }

  // TODO: free completed
  return TURBINE_EXEC_SUCCESS;
}


static void
shutdown_executors(turbine_executor *executors, int nexecutors)
{
  for (int i = 0; i < nexecutors; i++) {
    turbine_executor *exec = &executors[i];
    turbine_exec_code ec = exec->shutdown(exec->state);
    if (ec != TURBINE_EXEC_SUCCESS)
    {
      // TODO: warn about error 
    }
  }
}
