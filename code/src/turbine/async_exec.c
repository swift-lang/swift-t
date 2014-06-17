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
#define _GNU_SOURCE // for asprintf()
#include <stdio.h>

#include "src/turbine/async_exec.h"

#include <assert.h>

#include <adlb.h>
#include <table.h>

#define COMPLETED_BUFFER_SIZE 16

static bool executors_init = false;
static struct table executors;

static turbine_exec_code
get_tasks(Tcl_Interp *interp, turbine_executor *executor,
          void *buffer, size_t buffer_size, bool poll, int max_tasks);

static turbine_exec_code
check_tasks(Tcl_Interp *interp, turbine_executor *executor, bool poll);

static void
shutdown_executors(turbine_executor *executors, int nexecutors);

static void
launch_error(Tcl_Interp* interp, turbine_executor *exec, int tcl_rc,
             const char *command);

static void
callback_error(Tcl_Interp* interp, turbine_executor *exec, int tcl_rc,
               Tcl_Obj *command);

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

static turbine_executor *
get_mutable_async_exec(const char *name)
{
  turbine_executor *executor;
  if (!table_search(&executors, name, (void**)&executor)) {
    printf("Could not find executor: \"%s\"\n", name);
    return NULL;
  }
  return executor;
}

const turbine_executor *
turbine_get_async_exec(const char *name)
{
  return get_mutable_async_exec(name);
}

turbine_code
turbine_async_worker_loop(Tcl_Interp *interp, const char *exec_name,
                          void *buffer, size_t buffer_size)
{
  turbine_exec_code ec;

  assert(exec_name != NULL);
  assert(buffer != NULL);
  assert(buffer_size > 0);
  // TODO: check buffer large enough for work units

  turbine_executor *executor = get_mutable_async_exec(exec_name);
  TMP_CONDITION(executor != NULL);

  assert(executor->initialize != NULL);
  ec = executor->initialize(executor->context, &executor->state);
  EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
               "error initializing executor %s", executor->name);

  while (true)
  {
    turbine_exec_slot_state slots;
    ec = executor->slots(executor->state, &slots);
    EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
               "error getting executor slot count %s", executor->name);

    if (slots.used < slots.total)
    {
      int max_tasks = slots.total - slots.used;

      // Need to do non-blocking get if we're polling executor too
      bool poll = (slots.used != 0);
      
      ec = get_tasks(interp, executor, buffer, buffer_size,
                     poll, max_tasks);
      if (ec == TURBINE_EXEC_SHUTDOWN)
      {
        break;
      }
      EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
               "error getting tasks for executor %s", executor->name);
    }
    // Update count in case work added 
    ec = executor->slots(executor->state, &slots);
    EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
               "error getting executor slot count %s", executor->name);

    if (slots.used > 0)
    {
      // Need to do non-blocking check if we want to request more work
      bool poll = (slots.used < slots.total);
      ec = check_tasks(interp, executor, poll);
      EXEC_CHECK(ec);
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
get_tasks(Tcl_Interp *interp, turbine_executor *executor,
          void *buffer, size_t buffer_size, bool poll, int max_tasks)
{
  adlb_code ac;
  int rc;

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
    if (ac == ADLB_SHUTDOWN)
    {
      return TURBINE_EXEC_SHUTDOWN;
    }
    TMP_ADLB_CHECK(ac);
    
    got_work = true;
  }
  
  if (got_work)
  {
    int cmd_len = work_len - 1;
    rc = Tcl_EvalEx(interp, buffer, cmd_len, 0);
    if (rc != TCL_OK)
    {
      launch_error(interp, executor, rc, buffer);
      return TURBINE_ERROR_EXTERNAL;
    }
  }

  return TURBINE_EXEC_SUCCESS;
}

static void
launch_error(Tcl_Interp* interp, turbine_executor *exec, int tcl_rc,
             const char *command)
{
  if (tcl_rc != TCL_ERROR)
  {
    printf("WARNING: Unexpected return code when running task for "
           "executor %s: %d", exec->name, tcl_rc);
  }

  // Pass error to calling script
  char* msg;
  int rc = asprintf(&msg, "Turbine %s worker task error in: %s",
                           exec->name, command);
  assert(rc != -1);
  Tcl_AddErrorInfo(interp, msg);
  free(msg);
}

static void
callback_error(Tcl_Interp* interp, turbine_executor *exec, int tcl_rc,
               Tcl_Obj *command)
{
  if (tcl_rc != TCL_ERROR)
  {
    printf("WARNING: Unexpected return code when running task for "
           "executor %s: %d", exec->name, tcl_rc);
  }

  // Pass error to calling script
  char* msg;
  int rc = asprintf(&msg, "Turbine %s worker task error in callback: %s",
                           exec->name, Tcl_GetString(command));
  assert(rc != -1);
  Tcl_AddErrorInfo(interp, msg);
  free(msg);
}

static turbine_exec_code
check_tasks(Tcl_Interp *interp, turbine_executor *executor, bool poll)
{
  turbine_exec_code ec;
  
  turbine_completed_task completed[COMPLETED_BUFFER_SIZE];
  int ncompleted = COMPLETED_BUFFER_SIZE; // Pass in size
  if (poll)
  {
    ec = executor->poll(executor->state, completed, &ncompleted);
    EXEC_CHECK(ec);
  }
  else
  {
    ec = executor->wait(executor->state, completed, &ncompleted);
    EXEC_CHECK(ec);
  }

  for (int i = 0; i < ncompleted; i++)
  {
    Tcl_Obj *cb, *succ_cb, *fail_cb;
    succ_cb = completed[i].callbacks.success.code;
    fail_cb = completed[i].callbacks.failure.code;
    cb = (completed[i].success) ? succ_cb : fail_cb;

    if (cb != NULL)
    {
      int rc = Tcl_EvalObjEx(interp, cb, 0);
      if (rc != TCL_OK)
      {
        callback_error(interp, executor, rc, cb);
        return TURBINE_ERROR_EXTERNAL;
      }
    }

    if (succ_cb != NULL)
    {
      Tcl_DecrRefCount(succ_cb);
    }
    
    if (fail_cb != NULL)
    {
      Tcl_DecrRefCount(fail_cb);
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
