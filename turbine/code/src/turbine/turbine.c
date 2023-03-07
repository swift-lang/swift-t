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
 * */

#include <assert.h>
#include <errno.h>
#include <inttypes.h>
#include <limits.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>

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
#include "turbine-finalizers.h"

#include "src/tcl/adlb/tcl-adlb.h"

MPI_Comm turbine_task_comm   = MPI_COMM_NULL;
MPI_Comm turbine_leader_comm = MPI_COMM_NULL;

/**
   Has turbine_init() been called successfully?
*/
static bool initialized = false;

static int mpi_size = -1;
static int mpi_rank = -1;

struct finalizer
{
  void (*func)(void*);
  void* context;
};
/** List of user finalization work, each a struct finalizer. */
static struct list* finalizers = NULL;

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
gdb_sleep(volatile int* t, int i)
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
      volatile int t = 0;
      int i = 0;
      while (!t)
        // In GDB, set t=1 to break out
        gdb_sleep(&t, i++);
    }
  }
}

static bool setup_cache(void);

static bool set_stdout(int rank, int size);

static int log_setup(int rank);

turbine_code
turbine_init(int amserver, int rank, int size)
{
  check_versions();

  gdb_check(rank);

  log_setup(rank);

  if (!amserver)
  {
    mpi_size = size;
    mpi_rank = rank;
    initialized = true;

    bool b = setup_cache();
    if (!b) return TURBINE_ERROR_NUMBER_FORMAT;
  }

  if (! set_stdout(rank, size))
    return TURBINE_ERROR_IO;

  turbine_code tc = turbine_async_exec_initialize();
  turbine_check(tc);
  return TURBINE_SUCCESS;
}

/**
   @return Tcl error code
*/
static int
log_setup(int rank)
{
  log_init();
  log_normalize();

  // Did the user enable logging?
  int enabled;
  getenv_integer("TURBINE_LOG", 0, &enabled);
  if (!enabled)
  {
    log_enable(false);
    return TCL_OK;
  }

  bool b;

  // Log is enabled.
  // Should we use a specific log file?
  char* filename = getenv("TURBINE_LOG_FILE");
  if (filename != NULL && strlen(filename) > 0)
  {
    b = log_file_set(filename);
    if (!b)
    {
      printf("Could not set log file: %s", filename);
      return TCL_ERROR;
    }
  }

  // Should we flush after every log message?
  getenv_boolean("TURBINE_LOG_FLUSH", true, &b);
  log_flush_auto_enable(b);

  // Should we prepend the MPI rank (emulate "mpiexec -l")?
  int log_rank_enabled;
  getenv_integer("TURBINE_LOG_RANKS", 0, &log_rank_enabled);
  if (log_rank_enabled)
  {
    char prefix[64];
    sprintf(prefix, "[%i]", rank);
    log_prefix_set(prefix);
  }

  return TCL_OK;
}

static bool
setup_cache()
{
  int size;
  unsigned long max_memory;
  bool b;

  // Cache is disabled by default:
  b = getenv_integer("TURBINE_CACHE_SIZE", 0, &size);
  if (!b)
  {
    printf("malformed integer in environment: TURBINE_CACHE_SIZE\n");
    return false;
  }
  /* if (mpi_rank == 0) */
  /*   DEBUG_TURBINE("TURBINE_CACHE_SIZE: %i", size); */
  b = getenv_ulong("TURBINE_CACHE_MAX", 10*1024*1024, &max_memory);
  if (!b)
  {
    printf("malformed integer in environment: TURBINE_CACHE_MAX\n");
    return false;
  }
  /* if (mpi_rank == 0) */
  /*   DEBUG_TURBINE("TURBINE_CACHE_MAX: %lu", max_memory); */

  turbine_cache_init(size, max_memory);

  return true;
}

/** return field width of integers up to max */
static int get_pad(int max)
{
  return (int) rintl(ceil(log(max+1)/log(10)));
}

static bool
set_stdout(int rank, int size)
{
  char tmpfname[PATH_MAX];
  char filename[PATH_MAX];
  char* s;
  getenv_string("TURBINE_STDOUT", NULL, &s);
  if (s == NULL) return true;

  strcpy(filename, s);

  // Substitute rank (as zero-padded string r) for %r into filename
  char* p;
  while ((p = strstr(filename, "%r")))
  {
    ptrdiff_t c = p - &filename[0];
    strcpy(tmpfname, filename);
    char* q = &tmpfname[0] + c + 1;
    *q = 's';
    char r[64];
    int pad = get_pad(size);
    sprintf(r, "%0*i", pad, rank);
    sprintf(filename, tmpfname, r);
  }
  log_printf("redirecting output to: %s", filename);
  log_flush();

  bool rc = make_parents(filename);
  if (!rc) return false;
  FILE* fp = freopen(filename, "w", stdout);
  if (fp == NULL)
  {
    // This has to go on stderr
    fprintf(stderr,
            "turbine: TURBINE_STDOUT: could not freopen: '%s'\n",
            filename);
    perror("turbine");
    return false;
  }
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

int
turbine_register_finalizer(void (*func)(void*), void* context)
{
  if (finalizers == NULL)
    finalizers = list_create();
  struct finalizer* fzr = malloc(sizeof(*fzr));
  fzr->func    = func;
  fzr->context = context;
  struct list_item* item = list_add(finalizers, fzr);
  if (item == NULL)
    return 0;
  return 1;
}

static void
call_user_finalizers(void)
{
  if (finalizers == NULL) return;
  while (true)
  {
    struct finalizer* fzr = list_poll(finalizers);
    if (fzr == NULL) break;
    // Call user finalizer:
    fzr->func(fzr->context);
  }
}

void
turbine_finalize(Tcl_Interp *interp)
{
  bool print_time;
  getenv_boolean("ADLB_PRINT_TIME", false, &print_time);
  if (print_time)
    if (adlb_comm_rank == 0)
      printf("turbine finalizing at: %0.3f\n", log_time());
  turbine_cache_finalize();
  turbine_async_exec_finalize(interp);
  call_user_finalizers();
  log_finalize();
}
