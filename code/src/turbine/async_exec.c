/*
 * Copyright 2014 University of Chicago and Argonne National Laboratory
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

#include "src/turbine/async_exec.h"

#include <assert.h>

#include <adlb.h>
#include <table.h>
    

// To be replaced with correct check
#define TMP_ADLB_CHECK(code) assert((code) == ADLB_SUCCESS)
#define TMP_WARN(fmt, args...) fprintf(stderr, fmt "\n", ##args)

#define TMP_EXEC_CHECK(code) assert((code) == TURBINE_EXEC_SUCCESS)
#define TMP_MALLOC_CHECK(p) assert(p != NULL)

static bool executors_init = false;
static struct table executors;

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

  exec->initialize = NULL; 
  exec->shutdown = NULL; // TODO: shutdown
  exec->poll = NULL;
  exec->wait = NULL;
  exec->slots = NULL;
  
  exec->context = NULL; // Any settings, etc 
  exec->state = NULL; // TODO: pointer to coasters client
}

turbine_code
turbine_add_async_exec(turbine_executor executor)
{
  // Initialize table on demand
  if (!executors_init)
  {
    bool ok = table_init(&executors, 16);
    assert(ok); // TODO
    executors_init = true;
  }

  // TODO: ownership of pointers, etc
  // TODO: validate executor
  turbine_executor *exec_ptr = malloc(sizeof(executor));
  TMP_MALLOC_CHECK(exec_ptr);
  *exec_ptr = executor;

  table_add(&executors, executor.name, exec_ptr);

  return TURBINE_SUCCESS;
}


turbine_code
turbine_async_worker_loop(const char *exec_name, void *buffer, int buffer_size)
{
  turbine_exec_code ec;

  assert(exec_name != NULL);
  assert(buffer != NULL);
  assert(buffer_size > 0);
  // TODO: check buffer large enough for work units

  turbine_executor *executor;
  if (!table_search(&executors, exec_name, (void**)&executor)) {
    printf("Could not find executor: \"%s\"\n", exec_name);
    return TURBINE_ERROR_INVALID;
  }
  assert(executor != NULL);

  assert(executor->initialize != NULL);
  ec = executor->initialize(executor->context, &executor->state);
  TMP_EXEC_CHECK(ec);
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

turbine_code
turbine_async_exec_finalize(void)
{
  // TODO: free memory for executors

  executors_init = false;
  return TURBINE_SUCCESS;
}
