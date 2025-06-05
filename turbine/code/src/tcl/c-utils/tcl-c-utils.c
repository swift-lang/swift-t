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

/*
 * tcl-c-utils.c
 *
 *  Created on: Oct 2, 2012
 *      Author: wozniak
 *
 *  Tcl extension for miscellaneous C functions
 */

#include "config.h"

#include <inttypes.h>
#include <limits.h>
#include <sys/utsname.h>

#ifdef HAVE_MALLOC_H
#include <malloc.h>
#endif

#include <tcl.h>

// string hash function
#include <strkeys.h>

#include "src/tcl/util.h"

#include "tcl-c-utils.h"

static int
c_utils_heapsize_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);

  size_t count = -1;

  #if defined(HAVE_MALLINFO) && defined(HAVE_MALLOC_H)
  struct mallinfo2 s = mallinfo2();
  count = s.uordblks;
  #endif

  Tcl_Obj* result = Tcl_NewWideIntObj(count);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
c_utils_hash_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  char* s = Tcl_GetString(objv[1]);

  int hash = strkey_hash(s, INT_MAX);

  Tcl_Obj* result = Tcl_NewLongObj(hash);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
Hostname_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  struct utsname u;
  int rc = uname(&u);
  assert(rc == 0);
  Tcl_Obj* result = Tcl_NewStringObj(u.nodename, -1);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   Shorten command creation lines.  c_utils:: namespace is prepended
 */
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, "c_utils::" tcl_function, c_function, \
                         NULL, NULL);

void
tcl_c_utils_init(Tcl_Interp* interp)
{
  COMMAND("heapsize", c_utils_heapsize_Cmd);
  COMMAND("hash",     c_utils_hash_Cmd);
  COMMAND("hostname", Hostname_Cmd);
}
