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
  Generic async executor interface

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

#include "src/util/debug.h"
#include "src/turbine/turbine-checks.h"
#include "src/turbine/async_exec.h"
#include "src/turbine/executors/exec_interface.h"
#include "src/turbine/services.h"

#include <assert.h>
#include <sched.h>

#include <adlb.h>
#include <table.h>

#define COMPLETED_BUFFER_SIZE 16

/*
 * State of asynchronous get requests
 */
typedef struct
{
  // Array of buffers
  adlb_payload_buf *buffers;
  // Request handles corresponding to buffers
  adlb_get_req *requests;

  int max_reqs; // Max requests outstanding (size of arrays)
  int nreqs; // Number of requests outstanding
  int head; // Index of next request/buffer
  int tail; // Index of last request/buffer pending
} get_req_state;

/* Initialization of module */
static bool executors_init = false;

/* Lazy initialization of executors table - may register executor at
   any time  */
static bool executors_table_init = false;
static struct table executors;

static turbine_code init_exec_table(void);

static turbine_exec_code
get_tasks(Tcl_Interp *interp, turbine_executor *executor,
          int adlb_work_type, get_req_state *reqs,
          bool poll, int max_tasks, bool *got_tasks);

static turbine_exec_code
check_tasks(turbine_context tcx, turbine_executor *executor, bool poll,
            bool *task_completed);

static turbine_code
run_callback(turbine_context tcx, turbine_executor *executor,
             turbine_completed_task *task, Tcl_Obj *cb);

static void
stop_executors(turbine_context tcx, turbine_executor *executors,
               int nexecutors);

static void
launch_error(Tcl_Interp* interp, turbine_executor *exec, int tcl_rc,
             const char *command);

static void
callback_error(Tcl_Interp* interp, turbine_executor *exec, int tcl_rc,
               Tcl_Obj *command);

turbine_code
turbine_async_exec_initialize(void)
{
  turbine_condition(!executors_init, TURBINE_ERROR_INVALID,
                    "Executors already init");

  executors_init = true;
  return TURBINE_SUCCESS;
}

static turbine_code
init_exec_table(void)
{
  assert(!executors_table_init);
  bool ok = table_init(&executors, 16);
  turbine_condition(ok, TURBINE_ERROR_OOM, "Error initializing table");

  executors_table_init = true;
  return TURBINE_SUCCESS;
}


turbine_code
turbine_add_async_exec(turbine_executor executor)
{
  if (!executors_table_init)
  {
    turbine_code tc = init_exec_table();
    turbine_check(tc);
  }

  turbine_condition(executors.size < TURBINE_ASYNC_EXEC_LIMIT,
        TURBINE_ERROR_INVALID,
        "Adding %s would exceed limit of %i async executors",
        executor.name, TURBINE_ASYNC_EXEC_LIMIT);

  // Validate functoin pointers
  assert(executor.name != NULL);
  assert(executor.configure != NULL);
  assert(executor.start != NULL);
  assert(executor.stop != NULL);
  assert(executor.wait != NULL);
  assert(executor.poll != NULL);
  assert(executor.slots != NULL);

  turbine_executor *exec_ptr = malloc(sizeof(executor));
  TURBINE_MALLOC_CHECK(exec_ptr);
  *exec_ptr = executor;
  exec_ptr->name = strdup(executor.name);

  table_add(&executors, executor.name, exec_ptr);

  return TURBINE_SUCCESS;
}

turbine_code
turbine_async_exec_names(const char **names, int size, int *count)
{
  int n = 0;

  if (!executors_table_init) {
    *count = 0;
    return TURBINE_SUCCESS;
  }

  TABLE_FOREACH(&executors, entry)
  {
    if (n == size) {
      break;
    }

    names[n++] = entry->key;
  }

  *count = n;
  return TURBINE_SUCCESS;
}

turbine_executor *
turbine_get_async_exec(const char *name, bool *started)
{
  if (!executors_table_init)
  {
    return NULL;
  }

  turbine_executor *executor;
  if (!table_search(&executors, name, (void**)&executor)) {
    printf("Could not find executor: \"%s\"\n", name);
    return NULL;
  }

  if (started != NULL) {
    *started = executor->started;
  }
  return executor;
}

turbine_code
turbine_configure_exec(Tcl_Interp *interp, turbine_executor *exec,
                       const char *config, size_t config_len)
{
  assert(exec != NULL);
  turbine_exec_code ec;

  turbine_context tcx = { interp };

  assert(exec->configure != NULL);
  ec = exec->configure(tcx, &exec->context, config, config_len);
  TURBINE_EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
               "error configuring executor %s", exec->name);

  return TURBINE_SUCCESS;
}

turbine_code
turbine_async_exec_max_slots(Tcl_Interp *interp,
            const turbine_executor *exec, int *max)
{
  assert(exec->max_slots != NULL);
  turbine_exec_code ec;

  turbine_context tcx = { interp };

  ec = exec->max_slots(tcx, exec->context, max);
  TURBINE_EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
           "error determining max slots for executor %s", exec->name);

  return TURBINE_SUCCESS;
}

turbine_code
turbine_async_worker_loop(Tcl_Interp *interp, turbine_executor *exec,
                int adlb_work_type, adlb_payload_buf *payload_buffers,
                int nbuffers)
{
  assert(exec != NULL);
  assert(payload_buffers != NULL);
  assert(nbuffers >= 1);
  if (nbuffers > TURBINE_ASYNC_EXEC_MAX_REQS) {
    DEBUG_EXECUTOR("More buffers than can use: %i vs. %i", nbuffers,
          TURBINE_ASYNC_EXEC_MAX_REQS);
    nbuffers = TURBINE_ASYNC_EXEC_MAX_REQS;
  }

  turbine_exec_code ec;
  turbine_code tc;

  turbine_context tcx = { interp };

  tc = turbine_service_init();
  turbine_check(tc);

  // Allocate on heap to avoid
  adlb_get_req req_storage[nbuffers];

  get_req_state reqs = {
    .buffers = payload_buffers,
    .requests = req_storage,
    .max_reqs = nbuffers,
    .nreqs = 0,
    .head = 0,
    .tail = 0,
  };

  assert(exec->start != NULL);
  bool must_start = !exec->started;
  if (must_start) {
    ec = exec->start(tcx, exec->context, &exec->state);
    TURBINE_EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
                 "error starting executor %s", exec->name);

    exec->started = true;
  }

  while (true)
  {
    turbine_exec_slot_state slots;
    bool something_happened = false;
    ec = exec->slots(tcx, exec->state, &slots);
    TURBINE_EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
               "error getting executor slot count %s", exec->name);

    if (slots.used < slots.total)
    {
      int max_tasks = slots.total - slots.used;

      // Need to do non-blocking get if we're polling executor too
      bool poll = (slots.used != 0);

      ec = get_tasks(interp, exec, adlb_work_type, &reqs,
                     poll, max_tasks, &something_happened);
      if (ec == TURBINE_EXEC_SHUTDOWN)
      {
        DEBUG_EXECUTOR("Shutdown: terminating async executor loop for %s",
                      exec->name);
        break;
      }
      TURBINE_EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
               "error getting tasks for executor %s", exec->name);
    }
    // Update count in case work added
    ec = exec->slots(tcx, exec->state, &slots);
    TURBINE_EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
               "error getting executor slot count %s", exec->name);

    if (slots.used > 0)
    {
      // Need to do non-blocking check if we want to request more work
      bool poll = (slots.used < slots.total);
      ec = check_tasks(tcx, exec, poll, &something_happened);
      TURBINE_EXEC_CHECK(ec, TURBINE_ERROR_EXTERNAL);
    }

    if (!something_happened)
    {
      // yield to scheduler if nothing happened to allow background
      // threads to run ASAP
      sched_yield();
    }
  }

  if (must_start)
  {
    stop_executors(tcx, exec, 1);
  }

  turbine_service_finalize();

  return TURBINE_SUCCESS;
}

/*
 * Get tasks from adlb and execute them.
 * got_tasks: set to true if got at least one task, unmodified otherwise
 */
static turbine_exec_code
get_tasks(Tcl_Interp *interp, turbine_executor *executor,
          int adlb_work_type, get_req_state *reqs,
          bool poll, int max_tasks, bool *got_tasks)
{
  adlb_code ac;
  int rc;
  int desired_reqs = (max_tasks < reqs->max_reqs) ?
                      max_tasks : reqs->max_reqs;
  int extra_reqs = desired_reqs - reqs->nreqs;

  DEBUG_EXECUTOR("get_tasks: executor=%s adlb_work_type=%i poll=%s "
                "max_tasks=%i max_reqs=%i nreqs=%i extra_reqs=%i head=%i",
                executor->name, adlb_work_type, poll ? "true" : "false",
                max_tasks, reqs->max_reqs, reqs->nreqs, extra_reqs, reqs->head);

  if (extra_reqs > 0)
  {
    DEBUG_EXECUTOR("Issuing %i more requests for work type %i",
                  extra_reqs, adlb_work_type);

    // Use temporary arrays to get contiguous items
    adlb_get_req tmp_reqs[extra_reqs];
    adlb_payload_buf tmp_bufs[extra_reqs];
    for (int i = 0; i < extra_reqs; i++)
    {
      tmp_bufs[i] = reqs->buffers[(reqs->head + i) % reqs->max_reqs];
    }

    // Block if not polling and new request is head
    bool block = !poll && reqs->nreqs == 0;
    ac = ADLB_Amget(adlb_work_type, extra_reqs, block, tmp_bufs, tmp_reqs);
    EXEC_ADLB_CHECK_MSG(ac, TURBINE_EXEC_OTHER,
                          "Error getting work from ADLB");

    for (int i = 0; i < extra_reqs; i++)
    {
      reqs->requests[reqs->head] = tmp_reqs[i];
      reqs->head = (reqs->head + 1) % reqs->max_reqs;
    }
    reqs->nreqs += extra_reqs;
  }

  *got_tasks = false;

  DEBUG_EXECUTOR("reqs->tail=%i reqs->head=%i", reqs->tail, reqs->head);
  while (reqs->nreqs > 0)
  {
    MPI_Comm tmp_comm;
    adlb_get_req *req = &reqs->requests[reqs->tail];
    DEBUG_EXECUTOR("Check request %i", reqs->tail);
    int work_len, answer_rank, type_recved;
    if (poll)
    {
      ac = ADLB_Aget_test(req, &work_len, &answer_rank, &type_recved,
                          &tmp_comm);
      EXEC_ADLB_CHECK_MSG(ac, TURBINE_EXEC_OTHER,
                          "Error getting work from ADLB");

      if (ac == ADLB_SHUTDOWN)
      {
        // Unlikely to get this, but handle correctly anyway
        DEBUG_EXECUTOR("Unexpected shutdown after test");
        return TURBINE_EXEC_SHUTDOWN;
      }
      else if (ac == ADLB_NOTHING)
      {
        DEBUG_EXECUTOR("Poll tasks: no work received");
        return TURBINE_EXEC_SUCCESS;
      }
      assert(ac == ADLB_SUCCESS);
    }
    else
    {
      ac = ADLB_Aget_wait(req, &work_len, &answer_rank, &type_recved,
                          &tmp_comm);
      if (ac == ADLB_SHUTDOWN)
      {
        DEBUG_EXECUTOR("Async executor loop for %s got shutdown signal",
                      executor->name);
        return TURBINE_EXEC_SHUTDOWN;
      }
      EXEC_ADLB_CHECK_MSG(ac, TURBINE_EXEC_OTHER,
                          "Error getting work from ADLB");

      assert(ac == ADLB_SUCCESS);
    }

    int cmd_len = work_len - 1;
    void *work = reqs->buffers[reqs->tail].payload;
    DEBUG_EXECUTOR("RUN (buffer %i): {%s} (length %i)\n",
                  reqs->tail, (char*)work, cmd_len);
    rc = Tcl_EvalEx(interp, work, cmd_len, 0);
    if (rc != TCL_OK)
    {
      launch_error(interp, executor, rc, work);
      return TURBINE_EXEC_TASK;
    }

    *got_tasks = true;
    reqs->tail = (reqs->tail + 1) % reqs->max_reqs;
    reqs->nreqs--;

    // We got at least one result: future checks should not block
    poll = true;
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

/*
  task_completed: set to true if at least one task completed,
                  unmodified otherwise
 */
static turbine_exec_code
check_tasks(turbine_context tcx, turbine_executor *executor, bool poll,
            bool *task_completed)
{
  turbine_code tc;
  turbine_exec_code ec;
  DEBUG_EXECUTOR("check_tasks: executor=%s poll=%s",
                executor->name, poll ? "true" : "false");

  turbine_completed_task completed[COMPLETED_BUFFER_SIZE];
  int ncompleted = COMPLETED_BUFFER_SIZE; // Pass in size
  if (poll)
  {
    ec = executor->poll(tcx, executor->state, completed, &ncompleted);
    EXEC_CHECK(ec);
  }
  else
  {
    ec = executor->wait(tcx, executor->state, completed, &ncompleted);
    EXEC_CHECK(ec);
  }

  DEBUG_EXECUTOR("check_tasks: executor=%s poll=%s ncompleted=%i",
                executor->name, poll ? "true" : "false", ncompleted);

  for (int i = 0; i < ncompleted; i++)
  {
    Tcl_Obj *cb, *succ_cb, *fail_cb;
    succ_cb = completed[i].callbacks.success.code;
    fail_cb = completed[i].callbacks.failure.code;
    cb = (completed[i].success) ? succ_cb : fail_cb;

    tc = run_callback(tcx, executor, &completed[i], cb);
    if (tc != TURBINE_SUCCESS)
      return TURBINE_EXEC_TASK;

    if (succ_cb != NULL)
    {
      Tcl_DecrRefCount(succ_cb);
    }

    if (fail_cb != NULL)
    {
      Tcl_DecrRefCount(fail_cb);
    }
  }

  return TURBINE_EXEC_SUCCESS;
}

static turbine_code
run_callback(turbine_context tcx, turbine_executor *executor,
             turbine_completed_task *task, Tcl_Obj *cb)
{
  int rc;
  turbine_code tc;
  Tcl_Interp *interp = tcx.interp;

  if (cb != NULL)
  {
    for (int i = 0; i < task->vars_len; i++)
    {
      char *name = task->vars[i].name;
      Tcl_Obj *val = task->vars[i].val;
      assert(name != NULL);
      assert(val != NULL);

      Tcl_Obj *name_obj = Tcl_NewStringObj(name, -1);
      Tcl_Obj *obj = Tcl_ObjSetVar2(interp, name_obj, NULL, val, 0);
      turbine_condition(obj != NULL, TURBINE_ERROR_UNKNOWN,
          "Error setting variable: %s = %s", name, Tcl_GetString(val));

      task->vars[i].val = NULL; // Can't use elsewhere
    }

    rc = Tcl_EvalObjEx(interp, cb, 0);
    if (rc != TCL_OK)
    {
      callback_error(interp, executor, rc, cb);
      tc = TURBINE_ERROR_EXTERNAL;
      goto cleanup;
    }
  }

  tc = TURBINE_SUCCESS;
cleanup:
  for (int i = 0; i < task->vars_len; i++)
  {
    char *name = task->vars[i].name;

    if (cb != NULL)
    {
      rc = Tcl_UnsetVar(interp, name, 0);
      turbine_condition(rc == TCL_OK, TURBINE_ERROR_UNKNOWN,
          "Error unsetting variable: %s", name);
    }

    if (task->vars[i].free_name)
    {
      free(name);
      task->vars[i].name = 0;
    }
  }

  if (task->vars != NULL)
  {
    free(task->vars);
    task->vars = NULL;
    task->vars_len = 0;
  }

  return tc;
}

static void
stop_executors(turbine_context tcx, turbine_executor *executors,
               int nexecutors)
{
  for (int i = 0; i < nexecutors; i++) {
    turbine_executor *exec = &executors[i];
    assert(exec->stop != NULL);

    turbine_exec_code ec = exec->stop(tcx, exec->state);
    if (ec != TURBINE_EXEC_SUCCESS)
    {
      // Only warn about error
      TURBINE_ERR_PRINTF("Error while shutting down %s executor\n",
                         exec->name);
    }
    exec->started = false;
  }
}

turbine_code
turbine_async_exec_finalize(Tcl_Interp *interp)
{
  turbine_context tcx = { interp };

  if (executors_table_init)
  {
    // Free all executors
    TABLE_FOREACH(&executors, item)
    {
      turbine_executor* executor = item->data;
      assert(executor->do_free != NULL);
      executor->do_free(tcx, executor->context);
      // Can safely cast since we allocated memory
      free((char*)executor->name);
      free(executor);
      item->data = NULL;
    }

    // Free table entries
    table_free_callback(&executors, false, NULL);

    executors_table_init = false;
  }

  executors_init = false;

  return TURBINE_SUCCESS;
}
