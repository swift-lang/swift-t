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


#include <stdlib.h>
#include <string.h>

#include <tools.h>

#include "checks.h"
#include "debug.h"

// NOTE: DEBUG and TRACE are disabled by default by macros:
//       Cf. debug.h
bool xlb_debug_enabled = true;
bool xlb_trace_enabled = true;

adlb_code
xlb_debug_check_environment()
{
  bool rc;

  // Note that getenv_boolean() prints a warning message on
  //      invalid input

  // Use default values above if not specified by environment variable
  rc = getenv_boolean("ADLB_TRACE",  xlb_trace_enabled,
                      &xlb_trace_enabled);
  if (!rc) return ADLB_ERROR;

  rc = getenv_boolean("ADLB_DEBUG", xlb_debug_enabled,
                      &xlb_debug_enabled);
  if (!rc) return ADLB_ERROR;

  return ADLB_SUCCESS;
}
