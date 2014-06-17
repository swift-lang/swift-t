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
noop_initialize(const void *context, void **state);

static turbine_exec_code
noop_shutdown(void *state);

static turbine_exec_code
noop_wait(void *state, turbine_completed_task *completed,
          int *ncompleted);

static turbine_exec_code
noop_poll(void *state, turbine_completed_task *completed,
          int *ncompleted);

static turbine_exec_code
noop_slots(void *state, turbine_exec_slot_state *slots);

static void
init_noop_executor(turbine_executor *exec, int adlb_work_type)
{
  exec->name = NOOP_EXECUTOR_NAME;
  exec->adlb_work_type = adlb_work_type;
  exec->notif_mode = EXEC_POLLING;

  exec->context = NOOP_CONTEXT;
  exec->state = NULL;

  exec->initialize = noop_initialize;
  exec->shutdown = noop_shutdown;
  exec->wait = noop_wait;
  exec->poll = noop_poll;
  exec->slots = noop_slots;
}

turbine_exec_code
noop_executor_register(int adlb_work_type)
{
  turbine_exec_code ec;
  turbine_executor exec;
  init_noop_executor(&exec, adlb_work_type);
  ec = turbine_add_async_exec(exec);
  EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL,
               "error registering Noop executor");

  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_initialize(const void *context, void **state)
{
  assert(context == NOOP_CONTEXT);
  noop_state *tmp = malloc(sizeof(noop_state)); 
  assert(tmp != NULL);
  tmp->slots.used = 0;
  tmp->slots.total = NOOP_EXEC_SLOTS;
  tmp->tasks = malloc(sizeof(tmp->tasks[0]) * (size_t)tmp->slots.total);
  EXEC_MALLOC_CHECK(tmp->tasks);
  for (int i = 0; i < tmp->slots.total; i++)
  {
    tmp->tasks[i].active = false;
  }

  *state = tmp;
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_shutdown(void *state)
{
  free(state);
  return TURBINE_EXEC_SUCCESS;
}

turbine_exec_code
noop_execute(Tcl_Interp *interp, void *state, const void *work, int length,
             turbine_task_callbacks callbacks)
{
  noop_state *s = state;
  assert(s->slots.used < s->slots.total);
  s->slots.used++;

  bool found_slot = false;
  for (int i = 0; i < s->slots.total; i++)
  {
    if (!s->tasks[i].active)
    {
      DEBUG_TURBINE("Noop task assigned to slot %i\n", i);
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
  
  return TURBINE_EXEC_SUCCESS;
}

// Choose a random completed task
static void
choose_completed(noop_state *state, turbine_completed_task *completed)
{
  assert(state->slots.used > 0);
  while (true)
  {
    int slot = rand() % state->slots.total;
    if (state->tasks[slot].active)
    {
      DEBUG_TURBINE("Noop task in slot %i completed\n", slot);
      state->tasks[slot].active = false;
      completed->success = true;
      completed->callbacks = state->tasks[slot].callbacks;
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
noop_wait(void *state, turbine_completed_task *completed,
          int *ncompleted)
{
  noop_state *s = state;
  if (s->slots.used > 0)
  {
    usleep(20 * 1000);
    turbine_exec_code ec = fill_completed(s, completed, ncompleted);
    EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL, "error checking for "
                  "completed tasks Noop executor");
  }
  else
  {
    *ncompleted = 0;
  }
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_poll(void *state, turbine_completed_task *completed,
          int *ncompleted)
{
  noop_state *s = state;
  if (s->slots.used > 0 && rand() > (RAND_MAX / 5))
  {
    turbine_exec_code ec = fill_completed(s, completed, ncompleted);
    EXEC_CHECK_MSG(ec, TURBINE_ERROR_EXTERNAL, "error filling "
                  "completed task in Noop executor");
  }
  else
  {
    *ncompleted = 0;
  }
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_slots(void *state, turbine_exec_slot_state *slots)
{
  *slots = ((noop_state*)state)->slots;
  return TURBINE_EXEC_SUCCESS;
}
