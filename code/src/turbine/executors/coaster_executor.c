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
#include "src/turbine/executors/coaster_executor.h"

#include "src/turbine/executors/exec_interface.h"

#include "src/turbine/turbine-checks.h"
#include "src/util/debug.h"

#include <assert.h>
#include <limits.h>
#include <unistd.h>

#include <table_lp.h>

/* Check coaster_rc, if failed print message and return return code. */
#define COASTER_CHECK(crc, err_rc) {                                  \
  coaster_rc __crc = (crc);                                           \
  turbine_condition(__crc == COASTER_SUCCESS, (err_rc),               \
        "Error in Coaster execution: %s (%s)",                        \
        coaster_last_err_info(), coaster_rc_string(__crc)); }

/* Environment variable names
   TODO: final config mechanism */
#define COASTER_ENV_SERVICE_URL "COASTER_SERVICE_URL"
#define COASTER_ENV_CLIENT_SLOTS "COASTER_CLIENT_SLOTS"

#define COASTER_DEFAULT_SERVICE_URL "127.0.0.1:53001"
/*
  TODO: what is sensible default?  Potential factors:
  - Imbalance between Coaster clients?
  - Imbalance if Coaster clients connected to different services?
  - Memory/CPU overhead of managing many jobs
 */
#define COASTER_DEFAULT_CLIENT_SLOTS 1024

/*
  Coaster context info, e.g. configuration that remains constant
  over entire run.
 */
typedef struct {
  char *service_url;
  size_t service_url_len;
  int total_slots;
  coaster_settings *settings;
} coaster_context;

/*
  Information about an active task.
 */
typedef struct {
  turbine_task_callbacks callbacks;
  coaster_job *job;
} coaster_active_task;

/*
  State of an executor
 */
typedef struct coaster_state {
  // Overall context
  coaster_context *context;

  // Actual Coaster client
  coaster_client *client;

  // Config ID for server
  coaster_config_id *config;

  // Information about slots available
  turbine_exec_slot_state slots;

  // List with coaster_active_task data.  Key is Coaster job ID.
  struct table_lp active_tasks;
} coaster_state;

#define ACTIVE_TASKS_INIT_CAPACITY 32

static turbine_exec_code
coaster_configure(void **context, const char *config,
                  size_t config_len);

static turbine_exec_code
coaster_start(void *context, void **state);

static turbine_exec_code coaster_stop(void *state);
static turbine_exec_code
coaster_cleanup_active(coaster_state *state);

static turbine_exec_code coaster_free(void *context);

static turbine_exec_code
coaster_wait(void *state, turbine_completed_task *completed,
          int *ncompleted);

static turbine_exec_code
coaster_poll(void *state, turbine_completed_task *completed,
          int *ncompleted);

static turbine_exec_code
coaster_slots(void *state, turbine_exec_slot_state *slots);

static turbine_exec_code
check_completed(coaster_state *state, turbine_completed_task *completed,
               int *ncompleted, bool wait_for_completion);

static turbine_exec_code
init_coaster_executor(turbine_executor *exec)
{
  exec->name = COASTER_EXECUTOR_NAME;
  exec->notif_mode = EXEC_POLLING;

  exec->context = NULL;
  exec->state = NULL;
  exec->started = false;

  // Initialize all function pointers
  exec->configure = coaster_configure;
  exec->start = coaster_start;
  exec->stop = coaster_stop;
  exec->free = coaster_free;
  exec->wait = coaster_wait;
  exec->poll = coaster_poll;
  exec->slots = coaster_slots;

  return TURBINE_EXEC_SUCCESS;
}

turbine_code
coaster_executor_register(void)
{
  turbine_exec_code ec;
  turbine_executor exec;
  ec = init_coaster_executor(&exec);
  TURBINE_EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
               "error initializing Coaster executor");

  ec = turbine_add_async_exec(exec);
  TURBINE_EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
               "error registering Coaster executor with Turbine");

  return TURBINE_SUCCESS;
}

static turbine_exec_code
coaster_configure(void **context, const char *config,
                  size_t config_len)
{
  coaster_context *cx = malloc(sizeof(coaster_context));
  EXEC_MALLOC_CHECK(cx);

  cx->total_slots = COASTER_DEFAULT_CLIENT_SLOTS;
  const char *slots_str = getenv(COASTER_ENV_CLIENT_SLOTS);
  if (slots_str != NULL) {
    char *end;
    long slots_val = strtol(slots_str, &end, 10);
    turbine_condition(end != slots_str && end[0] == '\0',
        TURBINE_ERROR_INVALID, "Slots setting in environment var %s "
        "was not integer: \"%s\"", COASTER_ENV_CLIENT_SLOTS, slots_str);

    turbine_condition(slots_val >= 1 && slots_val <= INT_MAX,
        TURBINE_ERROR_INVALID, "Slots setting in environment var %s "
        "was not positive int value: %li", COASTER_ENV_CLIENT_SLOTS,
        slots_val);

    cx->total_slots = (int)slots_val;
  }

  coaster_rc crc = coaster_settings_create(&cx->settings);
  COASTER_CHECK(crc, TURBINE_EXEC_OOM);

  crc = coaster_settings_parse(cx->settings, config, config_len, ',');
  // Better reporting for config errors
  turbine_condition(crc != COASTER_ERROR_INVALID, TURBINE_ERROR_INVALID,
      "Error parsing settings string: \"%.*s\"\n%s",
      (int)config_len, config, coaster_last_err_info());
  COASTER_CHECK(crc, TURBINE_EXEC_OOM);

  const char *service_url = getenv(COASTER_ENV_SERVICE_URL);

  if (service_url == NULL)
  {
    service_url = COASTER_DEFAULT_SERVICE_URL;
  }

  size_t service_url_len = strlen(service_url);
  cx->service_url = malloc(service_url_len);
  EXEC_MALLOC_CHECK(cx->service_url);
  memcpy(cx->service_url, service_url, service_url_len);
  cx->service_url_len = service_url_len;

  *context = cx;
  return TURBINE_SUCCESS;
}

static turbine_exec_code
coaster_start(void *context, void **state)
{
  assert(context != NULL);
  coaster_context *cx = context;
  coaster_state *s = malloc(sizeof(coaster_state));
  EXEC_MALLOC_CHECK(s);

  s->context = cx;

  s->slots.used = 0;
  s->slots.total = cx->total_slots;

  table_lp_init(&s->active_tasks, ACTIVE_TASKS_INIT_CAPACITY);

  coaster_rc crc = coaster_client_start(cx->service_url,
                        cx->service_url_len, &s->client);
  COASTER_CHECK(crc, TURBINE_EXEC_OTHER);

  // Setup service config
  crc = coaster_apply_settings(s->client, cx->settings, &s->config);
  COASTER_CHECK(crc, TURBINE_EXEC_OTHER);

  *state = s;
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
coaster_stop(void *state)
{
  coaster_state *s = state;

  coaster_rc crc = coaster_client_stop(s->client);
  COASTER_CHECK(crc, TURBINE_EXEC_OTHER);

  crc = coaster_config_id_free(s->config);
  COASTER_CHECK(crc, TURBINE_EXEC_OTHER);

  turbine_exec_code ec = coaster_cleanup_active(s);
  EXEC_CHECK(ec);

  free(s);
  return TURBINE_EXEC_SUCCESS;
}

/*
  Check any outstanding tasks at shutdown.
  Should be called after stopping coaster client and thread.
 */
static turbine_exec_code
coaster_cleanup_active(coaster_state *state)
{
  coaster_rc crc;

  TABLE_LP_FOREACH(&state->active_tasks, entry)
  {
    int64_t job_id = entry->key;
    coaster_active_task *task = entry->data;

    char *job_str;
    size_t job_str_len;
    crc = coaster_job_to_string(task->job, &job_str, &job_str_len);
    fprintf(stderr, "Coaster job %"PRId64" still running at shutdown: "
                    "%s\n", job_id, job_str);
    free(job_str);

    // Free coaster job now that client is shut down
    crc = coaster_job_free(task->job);
    COASTER_CHECK(crc, TURBINE_EXEC_OTHER);

    Tcl_Obj *cb = task->callbacks.success.code;
    if (cb != NULL)
    {
      Tcl_DecrRefCount(cb);
    }

    cb = task->callbacks.failure.code;
    if (cb != NULL)
    {
      Tcl_DecrRefCount(cb);
    }
    free(task);
  }

  // Free table memory
  table_lp_free_callback(&state->active_tasks, false, NULL);
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
coaster_free(void *context)
{
  coaster_context *cx = context;
  if (cx != NULL) {
    coaster_settings_free(cx->settings);
    free(cx->service_url);
    free(cx);
  }

  return TURBINE_EXEC_SUCCESS;
}

turbine_code
coaster_execute(Tcl_Interp *interp, const turbine_executor *exec,
                coaster_job *job, turbine_task_callbacks callbacks)
{
  coaster_rc crc;

  assert(exec != NULL);
  assert(job != NULL);
  coaster_state *s = exec->state;
  turbine_condition(s != NULL, TURBINE_ERROR_INVALID,
        "Invalid state for coaster executor");
  assert(s->slots.used < s->slots.total);
  s->slots.used++;

  crc = coaster_submit(s->client, s->config, job);
  COASTER_CHECK(crc, TURBINE_ERROR_EXTERNAL);

  DEBUG_TURBINE("COASTER: Launched job: %p\n", job);

  coaster_active_task *task;
  task = malloc(sizeof(*task));
  TURBINE_MALLOC_CHECK(task);

  task->job = job;
  task->callbacks = callbacks;

  int64_t job_id = coaster_job_get_id(job);

  bool ok = table_lp_add(&s->active_tasks, job_id, task);
  turbine_condition(ok, TURBINE_ERROR_OOM, "Could not add table entry");

  if (callbacks.success.code != NULL)
  {
    Tcl_IncrRefCount(callbacks.success.code);
  }

  if (callbacks.failure.code != NULL)
  {
    Tcl_IncrRefCount(callbacks.failure.code);
  }

  return TURBINE_SUCCESS;
}

static turbine_exec_code
check_completed(coaster_state *state, turbine_completed_task *completed,
               int *ncompleted, bool wait_for_completion)
{
  coaster_rc crc;
  int completed_size = *ncompleted;
  assert(completed_size >= 1);
  int job_count = 0; // Number of completed jobs we returned

  while (job_count < completed_size)
  {
    const int tmp_jobs_size = 32;
    coaster_job *tmp_jobs[tmp_jobs_size];

    int maxleft = completed_size - job_count;
    int maxjobs = (maxleft < tmp_jobs_size) ? maxleft : tmp_jobs_size;

    int njobs;
    crc = coaster_check_jobs(state->client, wait_for_completion,
                             maxjobs, tmp_jobs, &njobs);
    COASTER_CHECK(crc, TURBINE_EXEC_OTHER);

    if (njobs > 0)
    {
      // Already got a job
      wait_for_completion = false;
    }

    for (int i = 0; i < njobs; i++)
    {
      coaster_job *job = tmp_jobs[i];
      int64_t job_id = coaster_job_get_id(job);

      coaster_active_task *task;
      table_lp_remove(&state->active_tasks, job_id, (void**)&task);
      EXEC_CONDITION(task != NULL, TURBINE_EXEC_OTHER,
                    "No matching entry for job id %"PRId64, job_id);

      coaster_job_status status;
      crc = coaster_job_status_code(job, &status);
      COASTER_CHECK(crc, TURBINE_EXEC_OTHER);

      // TODO: some way to get back info about cause of failure?
      // TODO: some way to pass job info to callback
      completed[job_count].success = (status == COASTER_STATUS_COMPLETED);
      completed[job_count].callbacks = task->callbacks;
      job_count++;

      crc = coaster_job_free(job);
      COASTER_CHECK(crc, TURBINE_EXEC_OTHER);
      free(task); // Done with task
    }

    if (njobs < maxjobs)
    {
      // Exhausted current jobs
      break;
    }
  }
  *ncompleted = job_count;
  state->slots.used -= *ncompleted;

  return TURBINE_EXEC_SUCCESS;
}

/*
 * Must wait for at least one task to completed.
 * Assume that at least one task is in system
 */
static turbine_exec_code
coaster_wait(void *state, turbine_completed_task *completed,
          int *ncompleted)
{
  coaster_state *s = state;

  EXEC_CONDITION(s->slots.used, TURBINE_EXEC_INVALID,
                "Cannot wait if no active coaster tasks");

  turbine_exec_code ec = check_completed(s, completed, ncompleted, true);
  EXEC_CHECK_MSG(ec, "error checking for completed tasks in Coaster "
                     "executor");

  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
coaster_poll(void *state, turbine_completed_task *completed,
          int *ncompleted)
{
  coaster_state *s = state;
  if (s->slots.used > 0)
  {
    turbine_exec_code ec = check_completed(s, completed, ncompleted, false);
    EXEC_CHECK_MSG(ec, "error checking for completed tasks in Coaster "
                       "executor");
  }
  else
  {
    *ncompleted = 0;
  }
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
coaster_slots(void *state, turbine_exec_slot_state *slots)
{
  *slots = ((coaster_state*)state)->slots;
  return TURBINE_EXEC_SUCCESS;
}
