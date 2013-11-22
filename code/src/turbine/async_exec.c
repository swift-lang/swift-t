/*
  Code sketch for generic async executor interface

  Created by Tim Armstrong, Nov 2013
 */

#include <assert.h>

#include <adlb.h>
    

// To be replaced with correct check
#define TMP_ADLB_CHECK(code) assert((code) == ADLB_SUCCESS)
#define TMP_WARN(fmt, args...) fprintf(stderr, fmt "\n", ##args)

// Function pointer types.  All are passed void state pointer for
// any state needed
typedef void (*exec_shutdown)(void *state);

// Polling: periodically called on an executor with active tasks.
//          updates ncompleted with number completed
// TODO: more info about which completed
typedef void (*exec_poll)(void *state, int *ncompleted);

// Executor notification model
typedef enum
{
  EXEC_POLLING, /* We have to periodically poll for status */
  EXEC_BG_THREAD, /* Executor has background thread */
} async_exec_notif;

typedef struct {
  const char *name;
  int adlb_work_type; // Type to request from adlb
  async_exec_notif notif; 
  int total_slots; // Total slots supported
  
  void *state; // Internal state to pass to executor functions

  /*
    Function pointers for executors
   */
  exec_shutdown shutdown;
  exec_poll poll; // Optional, for polling-based executors
} executor_t;


static adlb_code try_get_work(executor_t *executors, int nexecutors,
        void *buffer, int buffer_size, int *free_slots, int *busy_slots,
        int *total_busy_slots);

static void launch_task(executor_t *exec, const void *data, int length);
static void shutdown_executors(executor_t *executors, int nexecutors);

void init_coasters_executor(executor_t *exec /*TODO: coasters params */)
{
  exec->name = "Coasters";
  exec->adlb_work_type = 2;
  // TODO: currently polling based, but acutally has bg thread
  exec->notif = EXEC_POLLING;
  exec->total_slots = 4;

  exec->state = NULL; // TODO: pointer to coasters client
  exec->shutdown = NULL; // TODO: shutdown
  exec->poll = NULL;
}


void async_worker_loop(executor_t *executors, int nexecutors,
                      void *buffer, int buffer_size) {
  // Check we were initialised properly
  assert(executors != NULL);
  assert(nexecutors > 0);

  // Track slots per executor and overall
  int free_slots[nexecutors];
  int busy_slots[nexecutors];
  int total_busy_slots = 0;
  int total_slots = 0; // Total across all executors
  for (int i = 0; i < nexecutors; i++) {
    free_slots[i] = executors[i].total_slots;
    busy_slots[i] = 0;
    total_slots += free_slots[i];
  }

  // TODO: need to track callbacks for each slot?

  adlb_code code;

  while (true) {
    if (total_busy_slots != total_slots)
    {
      code = try_get_work(executors, nexecutors, buffer, buffer_size,
                   free_slots, busy_slots, &total_busy_slots);
      if (code == ADLB_SHUTDOWN)
      {
        if (total_busy_slots != 0)
        {
          TMP_WARN("Shutting down but %i slots still busy\n",
                   total_busy_slots);
        }
        shutdown_executors(executors, nexecutors);
        break;
      }
    }

    if (total_busy_slots > 0)
    {
      // TODO: if total_busy_slots == total_slots, ideally want to
      //       suspend this thread (e.g. block on condition variable)
      //       until we have work.
      for (int i = 0; i < nexecutors; i++)
      {
        executor_t *exec = &executors[i];
        if (busy_slots[i] > 0)
        {
          // check for task completions
          if (exec->notif == EXEC_POLLING)
          {
            int completions;
            exec->poll(exec->state, &completions);
            assert(completions >= 0);
            assert(completions <= busy_slots[i]);
            if (completions > 0)
            {
              busy_slots[i] -= completions;
              free_slots[i] += completions;
              total_busy_slots -= completions;
              // TODO: run callbacks?
            }
          }
          else
          {
            // TODO: check for state updates?
          }

        }
        // TODO: for executors with background threads, should we have
        //       them update data somewhere?
      }

    }
  }
}

/** Try to get work for executors */
static adlb_code try_get_work(executor_t *executors, int nexecutors,
        void *buffer, int buffer_size, int *free_slots, int *busy_slots,
        int *total_busy_slots)
{
  adlb_code code;
  int work_len, answer_rank, type_recved;
  MPI_Comm task_comm;

  if (*total_busy_slots == 0)
  {
    // Do blocking get if we're idle
    //TODO: can only support single work type because of blocking ADLB_Get
    int exec_num = 0;
    code = ADLB_Get(executors[exec_num].adlb_work_type, buffer, &work_len,
                    &answer_rank, &type_recved, &task_comm);
    TMP_ADLB_CHECK(code);

    if (code == ADLB_SHUTDOWN)
    {
      return ADLB_SHUTDOWN;
    }
    else
    {
      assert(code == ADLB_SUCCESS);
      launch_task(&executors[exec_num], buffer, work_len);
      free_slots[exec_num]--;
      busy_slots[exec_num]++;
      (*total_busy_slots)++;
    }
  }
  else
  {
    for (int i = 0; i < nexecutors; i++)
    {
      // Check if this executor needs work
      if (free_slots[i] > 0)
      { 
        // TODO: non-polling IGet?
        code = ADLB_Iget(executors[i].adlb_work_type, buffer, &work_len,
                      &answer_rank, &type_recved);
        TMP_ADLB_CHECK(code);
        
        if (code == ADLB_SUCCESS)
        {
          launch_task(&executors[i], buffer, work_len);
          free_slots[i]--;
          busy_slots[i]++;
          (*total_busy_slots)++;
        } 
        else if (code == ADLB_SHUTDOWN)
        {
          return ADLB_SHUTDOWN;
        }
      }
    }
  }
  return ADLB_SUCCESS;
}

/** Launch a received task */
static void launch_task(executor_t *exec, const void *data, int length)
{
  // TODO: eval the tcl code
  // TODO: is it reasonable to assume that always consumes one executor slot?
}

static void shutdown_executors(executor_t *executors, int nexecutors)
{
  for (int i = 0; i < nexecutors; i++) {
    executor_t *exec = &executors[i];
    exec->shutdown(exec->state);
  }
}
