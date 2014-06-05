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

#include <assert.h>

#define NOOP_CONTEXT ((void*)0x1)

typedef struct {
  turbine_exec_slot_state slots;
} noop_state;

static turbine_exec_code
noop_initialize(const void *context, void **state);

static turbine_exec_code
noop_shutdown(void *state);

static turbine_exec_code
noop_wait(void *state, turbine_completed_task **completed,
          int *ncompleted);

static turbine_exec_code
noop_poll(void *state, turbine_completed_task **completed,
          int *ncompleted);

static turbine_exec_code
noop_slots(void *state, turbine_exec_slot_state *slots);

static void
init_noop_executor(turbine_executor *exec, int adlb_work_type)
{
  exec->name = "Noop";
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

void
noop_executor_register(int adlb_work_type)
{
  turbine_executor exec;
  init_noop_executor(&exec, adlb_work_type);
  turbine_add_async_exec(exec);
}

static turbine_exec_code
noop_initialize(const void *context, void **state)
{
  assert(context == NOOP_CONTEXT);
  noop_state *tmp = malloc(sizeof(noop_state)); 
  assert(tmp != NULL);
  tmp->slots.used = 0;
  tmp->slots.total = 4;
  
  *state = tmp;
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_shutdown(void *state)
{
  free(state);
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_wait(void *state, turbine_completed_task **completed,
          int *ncompleted)
{
  // TODO: determine number finished

  *ncompleted = 0;
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_poll(void *state, turbine_completed_task **completed,
          int *ncompleted)
{
  // TODO: determine number finished

  *ncompleted = 0;
  return TURBINE_EXEC_SUCCESS;
}

static turbine_exec_code
noop_slots(void *state, turbine_exec_slot_state *slots)
{
  *slots = ((noop_state*)state)->slots;
  return TURBINE_EXEC_SUCCESS;
}
