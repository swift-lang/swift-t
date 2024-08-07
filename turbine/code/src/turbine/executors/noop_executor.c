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
#include "src/turbine/executors/noop_executor.h"

#include "src/turbine/executors/exec_interface.h"

#include "src/turbine/turbine-checks.h"
#include "src/util/debug.h"

#include <assert.h>
#include <unistd.h>

#define NOOP_CONTEXT ((void*)0x1)

// Fixed slot count for this executor
#define NOOP_EXEC_SLOTS 4

typedef struct {
  turbine_task_callbacks callbacks;
  bool active; // If being used
} noop_active_task;

typedef struct noop_state {
  turbine_exec_slot_state slots;
  // Array with one task per slot
  noop_active_task *tasks;
} noop_state;

static turbine_exec_code
noop_configure(turbine_context tcx, void **context,
    const char *config, size_t config_len);

static turbine_exec_code
noop_start(turbine_context tcx, void *context, void **state);

static turbine_exec_code noop_stop(turbine_context tcx, void *state);

static turbine_exec_code noop_free(turbine_context tcx, void *context);

static turbine_exec_code
noop_wait(turbine_context tcx, void *state,
    turbine_completed_task *completed, int *ncompleted);

static turbine_exec_code
noop_poll(turbine_context tcx, void *state,
    turbine_completed_task *completed, int *ncompleted);

static turbine_exec_code
noop_slots(turbine_context tcx, void *state,
    turbine_exec_slot_state *slots);

static turbine_exec_code
noop_max_slots(turbine_context tcx, void *context, int *max);

static void
init_noop_executor(turbine_executor* exec)
{
  exec->name = NOOP_EXECUTOR_NAME;

  exec->context = NULL;
  exec->state = NULL;
  exec->started = false;

  exec->configure = noop_configure;
  exec->start = noop_start;
  exec->stop = noop_stop;
  exec->do_free = noop_free;
  exec->wait = noop_wait;
  exec->poll = noop_poll;
  exec->slots = noop_slots;
  exec->max_slots = noop_max_slots;
}

turbine_code
noop_executor_register(void)
{
  turbine_code tc;
  turbine_executor exec;
  init_noop_executor(&exec);
  tc = turbine_add_async_exec(exec);
  turbine_check(tc);

  return TURBINE_SUCCESS;
}

static turbine_exec_code
noop_configure(turbine_context tcx, void **context,
    const char *config, size_t config_len)
{
  *context = NOOP_CONTEXT;
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_start(turbine_context tcx, void *context, void **state)
{
  assert(context == NOOP_CONTEXT);
  noop_state *s = malloc(sizeof(noop_state));
  assert(s != NULL);
  s->slots.used = 0;
  s->slots.total = NOOP_EXEC_SLOTS;
  s->tasks = malloc(sizeof(s->tasks[0]) * (size_t)s->slots.total);
  EXEC_MALLOC_CHECK(s->tasks);
  for (int i = 0; i < s->slots.total; i++)
  {
    s->tasks[i].active = false;
  }

  *state = s;
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_stop(turbine_context tcx, void *state)
{
  noop_state *s = state;
  for (int i = 0; i < s->slots.total; i++)
  {
    if (s->tasks[i].active)
    {
      fprintf(stderr, "Noop executor slot %i still active at shutdown\n",
                      i);

      Tcl_Obj *cb = s->tasks[i].callbacks.success.code;
      if (cb != NULL)
      {
        Tcl_DecrRefCount(cb);
      }

      cb = s->tasks[i].callbacks.failure.code;
      if (cb != NULL)
      {
        Tcl_DecrRefCount(cb);
      }
    }
  }
  free(s->tasks);
  free(s);
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_free(turbine_context tcx, void *context)
{
  // Don't need to do anything
  return TURBINE_EXEC_SUCCESS;
}

turbine_code
noop_execute(Tcl_Interp *interp, const turbine_executor *exec,
             const void *work, int length,
             turbine_task_callbacks callbacks)
{
  noop_state *s = exec->state;
  turbine_condition(exec != NULL, TURBINE_ERROR_INVALID,
                    "Null state for noop executor");

  assert(s->slots.used < s->slots.total);
  s->slots.used++;

  bool found_slot = false;
  for (int i = 0; i < s->slots.total; i++)
  {
    if (!s->tasks[i].active)
    {
      DEBUG_EXECUTOR("Noop task assigned to slot %i\n", i);
      s->tasks[i].callbacks = callbacks;
      s->tasks[i].active = true;
      found_slot = true;
      break;
    }
  }
  assert(found_slot);

  printf("NOOP: Launched task: %.*s\n", length, (const char*)work);

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

// Choose a random completed task
static void
choose_completed(noop_state *state, turbine_completed_task *comp)
{
  assert(state->slots.used > 0);
  while (true)
  {
    int slot = rand() % state->slots.total;
    if (state->tasks[slot].active)
    {
      DEBUG_EXECUTOR("Noop task in slot %i completed\n", slot);
      state->tasks[slot].active = false;
      comp->success = true;
      comp->callbacks = state->tasks[slot].callbacks;
      comp->vars = malloc(sizeof(comp->vars[0]) * 1);
      comp->vars_len = 1;

      // Add a dummy variable to test callback
      comp->vars[0].name = strdup("noop_task_result"); // Test freeing
      comp->vars[0].free_name = true;
      comp->vars[0].val = Tcl_NewStringObj("noop_task_result", -1);

      break;
    }
  }
}

static turbine_exec_code
fill_completed(noop_state *state, turbine_completed_task *completed,
               int *ncompleted)
{
  int completed_size = *ncompleted;
  assert(completed_size >= 1);

  if (state->slots.used > 1 && rand() > (RAND_MAX / 2) &&
      completed_size >= 2)
  {
    printf("NOOP: 2 completed\n");
    // Return multiple
    choose_completed(state, &completed[0]);
    choose_completed(state, &completed[1]);
    *ncompleted = 2;
  }
  else
  {
    printf("NOOP: 1 completed\n");
    choose_completed(state, &completed[0]);
    *ncompleted = 1;
  }

  state->slots.used -= *ncompleted;

  return TURBINE_EXEC_SUCCESS;
}


static turbine_exec_code
noop_wait(turbine_context tcx, void *state,
    turbine_completed_task *completed, int *ncompleted)
{
  noop_state *s = state;
  if (s->slots.used > 0)
  {
    usleep(20 * 1000);
    turbine_exec_code ec = fill_completed(s, completed, ncompleted);
    EXEC_CHECK_MSG(ec, "error checking for completed tasks Noop "
                       "executor");
  }
  else
  {
    *ncompleted = 0;
  }
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_poll(turbine_context tcx, void *state,
    turbine_completed_task *completed, int *ncompleted)
{
  noop_state *s = state;
  if (s->slots.used > 0 && rand() > (RAND_MAX / 5))
  {
    turbine_exec_code ec = fill_completed(s, completed, ncompleted);
    EXEC_CHECK_MSG(ec, "error filling completed task in Noop executor");
  }
  else
  {
    *ncompleted = 0;
  }
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_slots(turbine_context tcx, void *state,
    turbine_exec_slot_state *slots)
{
  *slots = ((noop_state*)state)->slots;
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_max_slots(turbine_context tcx, void *context, int *max)
{
  assert(context == NOOP_CONTEXT);
  *max = NOOP_EXEC_SLOTS;
  return TURBINE_EXEC_SUCCESS;
}
