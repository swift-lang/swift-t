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
 * turbine.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 *
 * TD means Turbine Datum, which is a variable id stored in ADLB
 * TR means TRansform, the in-memory record from a rule
 * */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>

#include <stdint.h>
#include <inttypes.h>

#include <adlb.h>

#include <c-utils.h>
#include <list.h>
#include <log.h>
#include <tools.h>

#include "src/util/debug.h"

#include "turbine-checks.h"
#include "turbine-version.h"
#include "async_exec.h"
#include "cache.h"
#include "turbine.h"

MPI_Comm turbine_task_comm = MPI_COMM_NULL;

/**
   Has turbine_init() been called successfully?
*/
static bool initialized = false;

static int mpi_size = -1;
static int mpi_rank = -1;

static void
check_versions()
{
  version tv, av, rav, cuv, rcuv;
  turbine_version(&tv);
  ADLB_Version(&av);
  // Required ADLB version:
  version_parse(&rav, ADLB_REQUIRED_VERSION);
  c_utils_version(&cuv);
  // Required c-utils version:
  version_parse(&rcuv, C_UTILS_REQUIRED_VERSION);
  version_require("Turbine", &tv, "c-utils", &cuv, &rcuv);
  version_require("Turbine", &tv, "ADLB",    &av,  &rav);
}

/**
   This is a separate function so we can set a function breakpoint
 */
static void
gdb_sleep(int* t, int i)
{
  sleep(1);
  DEBUG_TURBINE("gdb_check: %i %i\n", *t, i);
}

/**
   Allows user to launch Turbine in a loop until a debugger attaches
 */
static void
gdb_check(int rank)
{
  int gdb_rank;
  char* s = getenv("GDB_RANK");
  if (s != NULL &&
      strlen(s) > 0)
  {
    int c = sscanf(s, "%i", &gdb_rank);
    if (c != 1)
    {
      printf("Invalid GDB_RANK: %s\n", s);
      exit(1);
    }
    if (gdb_rank == rank)
    {
      pid_t pid = getpid();
      printf("Waiting for gdb: rank: %i pid: %i\n", rank, pid);
      int t = 0;
      int i = 0;
      while (!t)
        gdb_sleep(&t, i++);
    }
  }
}

static bool setup_cache(void);

turbine_code
turbine_init(int amserver, int rank, int size)
{
  check_versions();

  gdb_check(rank);

  if (amserver)
    return TURBINE_SUCCESS;

  mpi_size = size;
  mpi_rank = rank;
  initialized = true;

  bool b = setup_cache();
  if (!b) return TURBINE_ERROR_NUMBER_FORMAT;

  turbine_code tc = turbine_async_exec_initialize();
  turbine_check(tc);
  return TURBINE_SUCCESS;
}

static bool
setup_cache()
{
  int size;
  unsigned long max_memory;
  bool b;

  b = getenv_integer("TURBINE_CACHE_SIZE", 1024, &size);
  if (!b)
  {
    printf("malformed integer in environment: TURBINE_CACHE_SIZE\n");
    return false;
  }
  if (mpi_rank == 0)
    DEBUG_TURBINE("TURBINE_CACHE_SIZE: %i", size);
  b = getenv_ulong("TURBINE_CACHE_MAX", 10*1024*1024, &max_memory);
  if (!b)
  {
    printf("malformed integer in environment: TURBINE_CACHE_MAX\n");
    return false;
  }
  if (mpi_rank == 0)
    DEBUG_TURBINE("TURBINE_CACHE_MAX: %lu", max_memory);

  turbine_cache_init(size, max_memory);

  return true;
}

void
turbine_version(version* output)
{
  version_parse(output, TURBINE_VERSION);
}

/**
   @param output Should point to good storage for output,
   at least TURBINE_CODE_STRING_MAX chars
   @return Number of characters written
*/
int
turbine_code_tostring(char* output, turbine_code code)
{
  int result = -1;
  switch (code)
  {
    case TURBINE_SUCCESS:
      result = sprintf(output, "TURBINE_SUCCESS");
      break;
    case TURBINE_ERROR_OOM:
      result = sprintf(output, "TURBINE_ERROR_OOM");
      break;
    case TURBINE_ERROR_DOUBLE_DECLARE:
      result = sprintf(output, "TURBINE_ERROR_DOUBLE_DECLARE");
      break;
    case TURBINE_ERROR_DOUBLE_WRITE:
      result = sprintf(output, "TURBINE_ERROR_DOUBLE_WRITE");
      break;
    case TURBINE_ERROR_UNSET:
      result = sprintf(output, "TURBINE_ERROR_UNSET");
      break;
    case TURBINE_ERROR_NOT_FOUND:
      result = sprintf(output, "TURBINE_ERROR_NOT_FOUND");
      break;
    case TURBINE_ERROR_NUMBER_FORMAT:
      result = sprintf(output, "TURBINE_ERROR_NUMBER_FORMAT");
      break;
    case TURBINE_ERROR_INVALID:
      result = sprintf(output, "TURBINE_ERROR_INVALID");
      break;
    case TURBINE_ERROR_NULL:
      result = sprintf(output, "TURBINE_ERROR_NULL");
      break;
    case TURBINE_ERROR_UNKNOWN:
      result = sprintf(output, "TURBINE_ERROR_UNKNOWN");
      break;
    case TURBINE_ERROR_TYPE:
      result = sprintf(output, "TURBINE_ERROR_TYPE");
      break;
    case TURBINE_ERROR_STORAGE:
      result = sprintf(output, "TURBINE_ERROR_STORAGE");
      break;
    case TURBINE_ERROR_UNINITIALIZED:
      result = sprintf(output, "TURBINE_ERROR_UNINITIALIZED");
      break;
    default:
      sprintf(output, "<could not convert code %d to string>", code);
      break;
  }
  return result;
}

void
turbine_finalize(void)
{
  turbine_cache_finalize();
  turbine_async_exec_finalize();
}

