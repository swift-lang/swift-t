/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
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

/**
 * Tcl extension for MPE
 *
 * This is affected by preprocessor variable ENABLE_MPE
 * If true, create the real MPE commands
 * Else, create noop commands
 * This allows Tcl scripts that call mpe:: functions to run even
 * if MPE is not enabled
 *
 * @author wozniak
 * */

#include <assert.h>

#include <tcl.h>

#include <config.h>

#include "src/tcl/util.h"
#include "tcl-mpe.h"

#ifdef ENABLE_MPE

#include <adlb.h>

#include <tools.h>

#include "src/util/debug.h"

#include <mpe.h>
static const char* MPE_CHOOSE_COLOR = "MPE_CHOOSE_COLOR";

#ifndef NDEBUG
static inline void
assert_mpe_initialized(void)
{
  if (MPE_Initialized_logging() != 1)
    valgrind_assert_failed_msg("TCL-MPE", 0, "MPE not initialized!");
}
#else
#define assert_mpe_initialized()
#endif

/**
  usage: mpe::enabled => true
 */
static int
MPE_Enabled_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  Tcl_Obj* result = Tcl_NewBooleanObj(true);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
  usage: mpe::create_pair <symbol> => [ list start-ID stop-ID ]
*/
static int
MPE_Create_Pair_Cmd(ClientData cdata, Tcl_Interp *interp,
                    int objc, Tcl_Obj *const objv[])
{
  assert_mpe_initialized();

  TCL_ARGS(2);
  char* state = Tcl_GetStringFromObj(objv[1], NULL);
  assert(state);

  int event1, event2;
  // This is typically the first call to MPE
  // A SEGV here probably means that ADLB was not configured with MPE
  MPE_Log_get_state_eventIDs(&event1, &event2);
  MPE_Describe_state(event1, event2, state, MPE_CHOOSE_COLOR);

  Tcl_Obj* items[2];
  items[0] = Tcl_NewIntObj(event1);
  items[1] = Tcl_NewIntObj(event2);
  Tcl_Obj* result = Tcl_NewListObj(2, items);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
  usage: mpe::create_solo <symbol> => ID
*/
static int
MPE_Create_Solo_Cmd(ClientData cdata, Tcl_Interp *interp,
                    int objc, Tcl_Obj *const objv[])
{
  assert_mpe_initialized();

  TCL_ARGS(2);
  char* token = Tcl_GetStringFromObj(objv[1], NULL);
  assert(token);

  int event;
  // This is typically the first call to MPE
  // A SEGV here probably means that ADLB was not configured with MPE
  MPE_Log_get_solo_eventID(&event);
  MPE_Describe_info_event(event, token, MPE_CHOOSE_COLOR, "%s");
  // MPE_Describe_event(event, token, MPE_CHOOSE_COLOR);

  Tcl_Obj* result = Tcl_NewIntObj(event);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: mpe::log <event-ID> [<message>]
*/
static int
MPE_Log_Cmd(ClientData cdata, Tcl_Interp *interp,
        int objc, Tcl_Obj *const objv[])
{
  assert_mpe_initialized();

  TCL_CONDITION((objc == 2 || objc == 3),
                "requires 1 or 2 args!");

  int event;
  int error = Tcl_GetIntFromObj(interp, objv[1], &event);
  TCL_CHECK(error);

  char* bytes = NULL;
  if (objc == 3)
    bytes = Tcl_GetStringFromObj(objv[2], NULL);

  MPE_Log_event(event, 0, bytes);
  return TCL_OK;
}

#else

// Not using MPE...

static int
MPE_Enabled_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  Tcl_Obj* result = Tcl_NewBooleanObj(false);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
MPE_Create_Pair_Cmd(ClientData cdata, Tcl_Interp *interp,
                    int objc, Tcl_Obj *const objv[])
{
  // NOOP
  return TCL_OK;
}

static int
MPE_Create_Solo_Cmd(ClientData cdata, Tcl_Interp *interp,
                    int objc, Tcl_Obj *const objv[])
{
  // NOOP
  return TCL_OK;
}

static int
MPE_Log_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  // NOOP
  return TCL_OK;
}

#endif

/**
   Shorten object creation lines.  mpe:: namespace is prepended
 */
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, "mpe::" tcl_function, c_function, \
                         NULL, NULL);

void
tcl_mpe_init(Tcl_Interp* interp)
{
  COMMAND("enabled",     MPE_Enabled_Cmd);
  COMMAND("create_pair", MPE_Create_Pair_Cmd);
  COMMAND("create_solo", MPE_Create_Solo_Cmd);
  COMMAND("log",         MPE_Log_Cmd);
}
