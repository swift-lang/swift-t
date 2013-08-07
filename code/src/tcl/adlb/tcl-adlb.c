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
 * Tcl extension for ADLB
 *
 * @author wozniak
 * */

// This file should do some user logging using the c-utils
// logging library - this is because the ADLB C layer cannot
// do that effectively, and these functions are called
// directly as Tcl extension functions

// This file should not do DEBUG logging for data operations
// except for during development of this file - the Turbine and ADLB
// messages are more useful.  This file only packs and unpacks
// calls to the ADLB C layer

#include "config.h"

#include <assert.h>

// strnlen() is a GNU extension: Need _GNU_SOURCE
#define _GNU_SOURCE
#if ENABLE_BGP == 1
// Also need __USE_GNU on the BG/P and on older GCC (4.1, 4.3)
#define __USE_GNU
#endif
#include <string.h>
#include <exm-string.h>

#include <tcl.h>
#include <mpi.h>
#include <adlb.h>
#include <adlb-defs.h>
#include <adlb_types.h>

#include <log.h>

#include <memory.h>
#include <table_lp.h>
#include <tools.h>
#include <vint.h>

#include "src/tcl/util.h"
#include "src/util/debug.h"

#include "tcl-adlb.h"

// Auto-detect: Old ADLB or new XLB
#ifdef XLB
#define USE_XLB
#else
#define USE_ADLB
#endif

int adlb_comm_rank;
/** Number of workers */
static int workers;
/** Number of servers */
static int servers;

static int am_server;

#ifdef USE_ADLB
static int am_debug_server;
#endif

/** Size of MPI_COMM_WORLD */
static int mpi_size = -1;

/** Rank in MPI_COMM_WORLD */
static int mpi_rank = -1;

/** Communicator for ADLB workers */
static MPI_Comm worker_comm;

/** If the controlling code passed us a communicator, it is here */
long adlb_comm_ptr = 0;

static char xfer[ADLB_DATA_MAX];
static const adlb_buffer xfer_buf = { .data = xfer, .length = ADLB_DATA_MAX };

/* Return a pointer to a shared transfer buffer */
char *tcl_adlb_xfer_buffer(int *buf_size) {
  *buf_size = ADLB_DATA_MAX;
  return xfer;
}
/**
   Map from TD to local blob pointers.
   This is not an LRU cache: the user must use blob_free to
   free memory
 */
static struct table_lp blob_cache;

typedef struct {
  bool initialized;
  char *name;
  int field_count;
  adlb_data_type *field_types;
  char **field_names;
  // Each field has an array of field names representing nesting
  int *field_nest_level;
  Tcl_Obj ***field_parts;
} adlb_struct_format;

/**
   Information about struct formats to enable parsing/constructing TCL dicts
   based on structs.
 */
static struct {
  adlb_struct_format *types;
  int types_len;
} adlb_struct_formats;
#define ADLB_STRUCT_TYPE_CHECK(st) \
    TCL_CONDITION(st < adlb_struct_formats.types_len &&     \
                adlb_struct_formats.types[st].initialized,  \
                "Struct type %i not registered with Tcl ADLB module", st);

static void set_namespace_constants(Tcl_Interp* interp);

static int refcount_mode(Tcl_Interp *interp, Tcl_Obj *const objv[],
                          Tcl_Obj* obj, adlb_refcount_type *mode);

static Tcl_Obj *build_tcl_blob(void *data, int length, adlb_datum_id id);

static int extract_tcl_blob(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj *obj, adlb_blob_t *blob, adlb_datum_id *id);


// Functions for managing struct formats
static void struct_format_init(void);
static int struct_format_finalize(void);
static int add_struct_format(Tcl_Interp *interp, Tcl_Obj *const objv[],
            adlb_struct_type type_id, const char *type_name,
            int field_count, const adlb_data_type *field_types,
            const char **field_names);
static int
packed_struct_to_tcl_dict(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         const void *data, int length,
                         const adlb_type_extra *extra, Tcl_Obj **result);
static int
tcl_dict_to_packed_struct(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj *dict, adlb_struct_type struct_type,
                         const adlb_buffer *caller_buffer,
                         adlb_binary_data* result);

#define DEFAULT_PRIORITY 0

/* current priority for rule */
int ADLB_curr_priority = DEFAULT_PRIORITY;


/* Layout of file list used as Tcl representation */
#define FILE_REF_ELEMS 3
#define FILE_REF_STATUS 0
#define FILE_REF_FILENAME 1
#define FILE_REF_MAPPED 2

static int
ADLB_Retrieve_Impl(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[], bool decr);

static int
ADLB_Acquire_Ref_Impl(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[], bool has_subscript);
/**
   usage: adlb::init <servers> <types> [<comm>]?
   Simplified use of ADLB_Init type_vect: just give adlb_init
   a number ntypes, and the valid types will be: [0..ntypes-1]
   If comm is given, run ADLB in that communicator
   Else, run ADLB in a dup of MPI_COMM_WORLD
 */
static int
ADLB_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 3 || objc == 4,
                "adlb::init requires 2 or 3 arguments!");

  mm_init();
  turbine_debug_init();

  int rc;

  int servers;
  rc = Tcl_GetIntFromObj(interp, objv[1], &servers);
  TCL_CHECK(rc);

  int ntypes;
  rc = Tcl_GetIntFromObj(interp, objv[2], &ntypes);
  TCL_CHECK(rc);

  int type_vect[ntypes];
  for (int i = 0; i < ntypes; i++)
    type_vect[i] = i;

  table_lp_init(&blob_cache, 16);

  MPI_Comm adlb_comm;

  if (objc == 3)
  {
    // Start with MPI_Init() and MPI_COMM_WORLD
    int argc = 0;
    char** argv = NULL;
    rc = MPI_Init(&argc, &argv);
    assert(rc == MPI_SUCCESS);
    MPI_Comm_dup(MPI_COMM_WORLD, &adlb_comm);
  }
  else if (objc == 4)
  {
    rc = Tcl_GetLongFromObj(interp, objv[3], &adlb_comm_ptr);
    TCL_CHECK(rc);
    memcpy(&adlb_comm, (void*) adlb_comm_ptr, sizeof(MPI_Comm));
  }
  else
    assert(false);

  MPI_Comm_size(adlb_comm, &mpi_size);
  workers = mpi_size - servers;
  MPI_Comm_rank(adlb_comm, &mpi_rank);

  if (mpi_rank == 0)
  {
    if (workers <= 0)
      puts("WARNING: No workers");
    // Other configuration information will go here...
  }

  // ADLB_Init(int num_servers, int use_debug_server,
  //           int aprintf_flag, int num_types, int *types,
  //           int *am_server, int *am_debug_server, MPI_Comm *app_comm)
#ifdef USE_ADLB
  rc = ADLB_Init(servers, 0, 0, ntypes, type_vect,
                   &am_server, &am_debug_server, &worker_comm);
#endif
#ifdef USE_XLB
  rc = ADLB_Init(servers, ntypes, type_vect,
                 &am_server, adlb_comm, &worker_comm);
#endif
  if (rc != ADLB_SUCCESS)
    return TCL_ERROR;

  if (! am_server)
    MPI_Comm_rank(worker_comm, &adlb_comm_rank);

  set_namespace_constants(interp);

  struct_format_init();

  Tcl_SetObjResult(interp, Tcl_NewIntObj(ADLB_SUCCESS));
  return TCL_OK;
}

static void struct_format_init(void)
{
  int init_size = 16;
  adlb_struct_formats.types_len = init_size; 
  adlb_struct_formats.types = malloc(sizeof(adlb_struct_formats.types[0]) *
                                    (size_t)init_size);
  for (int i = 0; i < init_size; i++)
  {
    adlb_struct_formats.types[i].initialized = false;
  }
}

static int struct_format_finalize(void)
{
  for (int i = 0; i < adlb_struct_formats.types_len; i++)
  {
    adlb_struct_format *f = &adlb_struct_formats.types[i];
    if (f->initialized)
    {
      free(f->name);
      free(f->field_types);
      for (int j = 0; j < f->field_count; j++)
      {
        for (int k = 0; k <= f->field_nest_level[j]; k++)
        {
          // free tcl object
          Tcl_DecrRefCount(f->field_parts[j][k]);
        }
        free(f->field_parts[j]);
        free(f->field_names[j]);
      }
      free(f->field_nest_level);
      free(f->field_names);
      free(f->field_parts);
    }
  }

  if (adlb_struct_formats.types != NULL)
    free(adlb_struct_formats.types);

  adlb_struct_formats.types = NULL;
  adlb_struct_formats.types_len = 0;
  return TCL_OK;
};

static int add_struct_format(Tcl_Interp *interp, Tcl_Obj *const objv[],
            adlb_struct_type type_id, const char *type_name,
            int field_count, const adlb_data_type *field_types,
            const char **field_names)
{
  assert(adlb_struct_formats.types != NULL); // Check init
  TCL_CONDITION(type_id >= 0, "Struct type id must be non-negative");
  assert(field_count >= 0);

  if (adlb_struct_formats.types_len <= type_id)
  {
    int old_len = adlb_struct_formats.types_len;
    int new_len = old_len * 2;
    if (new_len <= type_id)
      new_len = type_id + 1;

    adlb_struct_formats.types = realloc(adlb_struct_formats.types, 
                                      (size_t)new_len *
                                      sizeof(adlb_struct_formats.types[0]));
    TCL_MALLOC_CHECK(adlb_struct_formats.types);
    for (int i = old_len; i < new_len; i++)
    {
      adlb_struct_formats.types[i].initialized = false;
    }
  }

  adlb_struct_format *t = &adlb_struct_formats.types[type_id];
  t->initialized = true;
  t->name = strdup(type_name);
  TCL_MALLOC_CHECK(t->name);
  t->field_count = field_count;
  t->field_types = malloc(sizeof(t->field_types[0]) * (size_t)field_count);
  TCL_MALLOC_CHECK(t->field_types);
  t->field_nest_level = malloc(sizeof(t->field_nest_level[0]) *
                              (size_t)field_count);
  TCL_MALLOC_CHECK(t->field_nest_level);
  t->field_names = malloc(sizeof(t->field_names[0]) * (size_t)field_count);
  TCL_MALLOC_CHECK(t->field_names);
  t->field_parts = malloc(sizeof(t->field_parts[0]) * (size_t)field_count);
  TCL_MALLOC_CHECK(t->field_parts);

  for (int i = 0; i < field_count; i++)
  {
    t->field_types[i] = field_types[i];
    t->field_names[i] = strdup(field_names[i]);
    TCL_MALLOC_CHECK(t->field_names[i]);

    const char *fname_pos;

    // Discover number of nested structs
    int nest_level = 0;
    fname_pos = field_names[i];
    while ((fname_pos = strchr(fname_pos, '.')) != NULL)
    {
      fname_pos++; // Move past '.'
      nest_level++;
    }

    t->field_nest_level[i] = nest_level;
    t->field_parts[i] = malloc(sizeof(t->field_parts[i][0]) *
                               (size_t)(nest_level + 1));
    TCL_MALLOC_CHECK(t->field_parts[i]);
    
    // Extract field names, e.g. "field1.b.c"
    fname_pos = field_names[i];
    for (int j = 0; j <= nest_level; j++)
    {
      assert(fname_pos != NULL);
      // Find next separator and copy field name
      char *sep_pos = strchr(fname_pos, '.');
      int fname_len;
      if (sep_pos == NULL)
      {
        assert(j == nest_level);
        fname_len = (int)strlen(fname_pos);
      }
      else
      {
        assert(j < nest_level);
        fname_len = (int)(sep_pos - fname_pos);
      }
      t->field_parts[i][j] = Tcl_NewStringObj(fname_pos, fname_len);
      Tcl_IncrRefCount(t->field_parts[i][j]); // Hold on to reference
      TCL_MALLOC_CHECK(t->field_parts[i][j]);
      
      fname_pos = sep_pos + 1;
    }

  }
  return TCL_OK;
}

/**
   usage: adlb::declare_struct_type <type id> <type name> <field list>
      where field list is a list of (<field name> <field type>)*
 */
static int
ADLB_Declare_Struct_Type_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(4);
  int rc;
  adlb_struct_type type_id;
  const char *type_name;

  rc = Tcl_GetIntFromObj(interp, objv[1], &type_id);
  TCL_CHECK(rc);

  type_name = Tcl_GetString(objv[2]);

  Tcl_Obj **field_list;
  int field_list_len;
  rc = Tcl_ListObjGetElements(interp, objv[3], &field_list_len, &field_list);
  TCL_CHECK(rc);
  TCL_CONDITION(field_list_len % 2 == 0,
                "adlb::declare_struct_type field list must have even length");

  int field_count = field_list_len / 2;
  adlb_data_type field_types[field_count];
  const char *field_names[field_count];

  for (int i = 0; i < field_count; i++)
  {
    field_names[i] = Tcl_GetString(field_list[2 * i]);
    rc = type_from_obj(interp, objv, field_list[2 * i + 1], &field_types[i]);
    TCL_CHECK(rc);
  }

  rc = add_struct_format(interp, objv, type_id, type_name, field_count,
                         field_types, field_names);
  TCL_CHECK(rc);

  adlb_data_code dc = ADLB_Declare_struct_type(type_id, type_name, field_count,
                      field_types, field_names);
  return (dc == ADLB_DATA_SUCCESS) ? TCL_OK : TCL_ERROR;
}

static void
set_namespace_constants(Tcl_Interp* interp)
{
  tcl_set_integer(interp, "::adlb::SUCCESS",   ADLB_SUCCESS);
  tcl_set_integer(interp, "::adlb::RANK_ANY",  ADLB_RANK_ANY);
  tcl_set_long(interp,    "::adlb::NULL_ID",   ADLB_DATA_ID_NULL);
}

/**
   Enter server
 */
static int
ADLB_Server_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  if (!am_server)
  {
    printf("adlb::server: This process is not a server!\n");
    return TCL_ERROR;
  }

  DEBUG_ADLB("ADLB SERVER...");
  // Limit ADLB to 100MB
  int max_memory = 100*1024*1024;
#ifdef USE_ADLB
  double logging = 0.0;
  int rc = ADLB_Server(max_memory, logging);
#endif
#ifdef USE_XLB
  int rc = ADLB_Server(max_memory);
#endif

  TCL_CONDITION(rc == ADLB_SUCCESS, "SERVER FAILED");

  return TCL_OK;
}

/**
   usage: no args, returns MPI rank
*/
static int
ADLB_Rank_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  Tcl_SetObjResult(interp, Tcl_NewIntObj(mpi_rank));
  return TCL_OK;
}

/**
   usage: no args, returns true if a server, else false
*/
static int
ADLB_AmServer_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  Tcl_SetObjResult(interp, Tcl_NewBooleanObj(am_server));
  return TCL_OK;
}

/**
   usage: no args, returns number of MPI world ranks
*/
static int
ADLB_Size_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  Tcl_SetObjResult(interp, Tcl_NewIntObj(mpi_size));
  return TCL_OK;
}

/**
   usage: no args, returns number of servers
*/
static int
ADLB_Servers_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  Tcl_SetObjResult(interp, Tcl_NewIntObj(servers));
  return TCL_OK;
}

/**
   usage: no args, returns number of servers
*/
static int
ADLB_Workers_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  Tcl_SetObjResult(interp, Tcl_NewIntObj(workers));
  return TCL_OK;
}

/**
   usage: no args, barrier for workers
*/
static int
ADLB_Barrier_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  int rc = MPI_Barrier(MPI_COMM_WORLD);
  ASSERT(rc == MPI_SUCCESS);
  return TCL_OK;
}

static int
ADLB_Hostmap_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  // This is limited only by the number of ranks a user could
  // conceivably put on a node- getting bigger
  int count = 512;
  int ranks[count];
  int actual;

  char* name = Tcl_GetString(objv[1]);

  adlb_code rc = ADLB_Hostmap(name, count, ranks, &actual);
  TCL_CONDITION(rc == ADLB_SUCCESS || rc == ADLB_NOTHING,
                "error in hostmap!");
  if (rc == ADLB_NOTHING)
    TCL_RETURN_ERROR("host not found: %s", name);

  Tcl_Obj* items[actual];
  for (int i = 0; i < actual; i++)
    items[i] = Tcl_NewIntObj(ranks[i]);

  Tcl_Obj* result = Tcl_NewListObj(actual, items);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

/**
   usage: adlb::put <reserve_rank> <work type> <work unit> <priority>
                                                        <parallelism>
*/
static int
ADLB_Put_Cmd(ClientData cdata, Tcl_Interp *interp,
             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(6);

  int target_rank;
  int work_type;
  int priority;
  int parallelism;
  Tcl_GetIntFromObj(interp, objv[1], &target_rank);
  Tcl_GetIntFromObj(interp, objv[2], &work_type);
  int cmd_len;
  char* cmd = Tcl_GetStringFromObj(objv[3], &cmd_len);
  Tcl_GetIntFromObj(interp, objv[4], &priority);
  Tcl_GetIntFromObj(interp, objv[5], &parallelism);

  DEBUG_ADLB("adlb::put: target_rank: %i type: %i \"%s\" %i",
             target_rank, work_type, cmd, priority);

  // int ADLB_Put(void *work_buf, int work_len, int reserve_rank,
  //              int answer_rank, int work_type, int work_prio)
  int rc = ADLB_Put(cmd, cmd_len+1, target_rank, adlb_comm_rank,
                    work_type, priority, parallelism);

  ASSERT(rc == ADLB_SUCCESS);
  return TCL_OK;
}

/**
   Special-case put that takes no special arguments
   usage: adlb::spawn <work type> <work unit>
*/
static int
ADLB_Spawn_Cmd(ClientData cdata, Tcl_Interp *interp,
             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  int work_type;
  Tcl_GetIntFromObj(interp, objv[1], &work_type);
  int cmd_len;
  char* cmd = Tcl_GetStringFromObj(objv[2], &cmd_len);
  int priority = ADLB_curr_priority;

  DEBUG_ADLB("adlb::spawn: type: %i \"%s\" %i", work_type, cmd, priority);

  int rc = ADLB_Put(cmd, cmd_len+1, ADLB_RANK_ANY, adlb_comm_rank,
                    work_type, priority, 1);

  ASSERT(rc == ADLB_SUCCESS);
  return TCL_OK;
}

/**
   usage: get_priority
 */
static int
ADLB_Get_Priority_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  // Return a tcl int
  // Tcl_SetIntObj doesn't like shared values, but it should be
  // safe in our use case to modify in-place
  Tcl_SetObjResult(interp, Tcl_NewIntObj(ADLB_curr_priority));
  return TCL_OK;
}

/**
   usage: reset_priority
 */
static int
ADLB_Reset_Priority_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  ADLB_curr_priority = DEFAULT_PRIORITY;
  return TCL_OK;
}

/**
   usage: set_priority
 */
static int
ADLB_Set_Priority_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  int rc, new_prio;
  rc = Tcl_GetIntFromObj(interp, objv[1], &new_prio);
  TCL_CHECK_MSG(rc, "Priority must be integer");
  ADLB_curr_priority = new_prio;
  return TCL_OK;
}

/**
   usage: adlb::get <req_type> <answer_rank>
   Returns the next work unit of req_type or empty string when
   ADLB is done
   Stores answer_rank in given output variable
 */
static int
ADLB_Get_Cmd(ClientData cdata, Tcl_Interp *interp,
             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  int req_type;
  int error = Tcl_GetIntFromObj(interp, objv[1], &req_type);
  TCL_CHECK(error);
  Tcl_Obj* tcl_answer_rank_name = objv[2];
  DEBUG_ADLB("adlb::get: type=%i", req_type);

  int work_type;

  char* result = &xfer[0];
#ifdef USE_ADLB
  int work_handle[ADLB_HANDLE_SIZE];
#endif
  int work_len;
  int answer_rank;
  bool found_work = false;
  int rc;

#ifdef USE_ADLB

  int req_types[4];
  int work_prio;

  req_types[0] = req_type;
  req_types[1] = req_types[2] = req_types[3] = -1;

  DEBUG_ADLB("enter reserve: type=%i", req_types[0]);
  rc = ADLB_Reserve(req_types, &work_type, &work_prio,
                    work_handle, &work_len, &answer_rank);
  DEBUG_ADLB("exit reserve");
  if (rc == ADLB_DONE_BY_EXHAUSTION)
  {
    DEBUG_ADLB("ADLB_DONE_BY_EXHAUSTION!");
    result[0] = '\0';
  }
  else if (rc == ADLB_NO_MORE_WORK ) {
    DEBUG_ADLB("ADLB_NO_MORE_WORK!");
    result[0] = '\0';
  }
  else if (rc == ADLB_NO_CURRENT_WORK) {
    DEBUG_ADLB("ADLB_NO_CURRENT_WORK");
    result[0] = '\0';
  }
  else if (rc < 0) {
    DEBUG_ADLB("rc < 0");
    result[0] = '\0';
  }
  else
  {
    DEBUG_ADLB("work is reserved.");
    rc = ADLB_Get_reserved(result, work_handle);
    if (rc == ADLB_NO_MORE_WORK)
    {
      puts("No more work on Get_reserved()!");
      result[0] = '\0';
    }
    else
      found_work = true;
  }
  if (result[0] == '\0')
    answer_rank = -1;
#endif

#ifdef USE_XLB
  MPI_Comm task_comm;
  rc = ADLB_Get(req_type, result, &work_len,
                &answer_rank, &work_type, &task_comm);
  if (rc == ADLB_SHUTDOWN)
  {
    result[0] = '\0';
    work_len = 1;
    answer_rank = ADLB_RANK_NULL;
  }
  turbine_task_comm = task_comm;
#endif

  if (found_work)
    DEBUG_ADLB("adlb::get: %s", (char*) result);

  // Store answer_rank in caller's stack frame
  Tcl_Obj* tcl_answer_rank = Tcl_NewIntObj(answer_rank);
  Tcl_ObjSetVar2(interp, tcl_answer_rank_name, NULL, tcl_answer_rank,
                 EMPTY_FLAG);

  Tcl_SetObjResult(interp, Tcl_NewStringObj(result, work_len - 1));
  return TCL_OK;
}

/**
   usage: adlb::iget <req_type> <answer_rank>
   Returns the next work unit of req_type or
        "ADLB_SHUTDOWN" or "ADLB_NOTHING"
   Stores answer_rank in given output variable
 */
static int
ADLB_Iget_Cmd(ClientData cdata, Tcl_Interp *interp,
             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  int req_type;
  int error = Tcl_GetIntFromObj(interp, objv[1], &req_type);
  TCL_CHECK(error);
  Tcl_Obj* tcl_answer_rank_name = objv[2];

  DEBUG_ADLB("adlb::get: type=%i", req_type);

  int work_type;

  char* result = &xfer[0];
  int work_len;
  int answer_rank;

  adlb_code rc = ADLB_Iget(req_type, result, &work_len,
                           &answer_rank, &work_type);
  if (rc == ADLB_SHUTDOWN)
  {
    strcpy(result, "ADLB_SHUTDOWN");
    answer_rank = ADLB_RANK_NULL;
  }
  else if (rc == ADLB_NOTHING)
  {
    strcpy(result, "ADLB_NOTHING");
    answer_rank = ADLB_RANK_NULL;
  }

  DEBUG_ADLB("adlb::iget: %s", result);

  // Store answer_rank in caller's stack frame
  Tcl_Obj* tcl_answer_rank = Tcl_NewIntObj(answer_rank);
  Tcl_ObjSetVar2(interp, tcl_answer_rank_name, NULL, tcl_answer_rank,
                 EMPTY_FLAG);

  Tcl_SetObjResult(interp, Tcl_NewStringObj(result, -1));
  return TCL_OK;
}

/**
   Convert type string to adlb_data_type
 */
static int type_from_string(Tcl_Interp *interp, const char* type_string,
                            adlb_data_type *type, bool *has_extra,
                            adlb_type_extra *extra)
{
  
  adlb_code rc = ADLB_Data_string_totype(type_string, type, has_extra, extra);
  if (rc != ADLB_SUCCESS)
  {
    *type = ADLB_DATA_TYPE_NULL;
    char err[strlen(type_string) + 20];
    sprintf(err, "unknown type name %s!", type_string);
    Tcl_AddErrorInfo(interp, err);
    return TCL_ERROR;
  }
  return TCL_OK;
}

int type_from_obj(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj* obj, adlb_data_type *type)
{
  bool has_extra;
  adlb_type_extra extra;
  int rc = type_from_obj_extra(interp, objv, obj, type, &has_extra, &extra);
  TCL_CHECK(rc);
  TCL_CONDITION(!has_extra, "didn't expect extra type info in %s",
                             Tcl_GetString(obj));
  return TCL_OK;
}

int type_from_obj_extra(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj* obj, adlb_data_type *type,
                         bool *has_extra, adlb_type_extra *extra)
{
  const char *type_name = Tcl_GetString(obj);
  TCL_CONDITION(type_name != NULL, "type argument not found!");
  int rc = type_from_string(interp, type_name, type, has_extra, extra);
  TCL_CHECK(rc);
  return TCL_OK;
}

/*
  Extract variable create properties
  accept_id: if true, accept id as first element
  objv: arguments, objc: argument count, argstart: start argument
 */
static inline int
extract_create_props(Tcl_Interp *interp, bool accept_id, int argstart,
    int objc, Tcl_Obj *const objv[], adlb_datum_id *id, adlb_data_type *type,
    adlb_type_extra *type_extra, adlb_create_props *props)
{
  int rc;
  int argpos = argstart;
  if (accept_id) {
    TCL_CONDITION(objc - argstart >= 2, "adlb::create requires >= 2 args!");
    rc = Tcl_GetADLB_ID(interp, objv[argpos++], id);
    TCL_CHECK_MSG(rc, "adlb::create could not get data id");
  } else {
    TCL_CONDITION(objc - argstart >= 1, "adlb::create requires >= 1 args!");
    *id = ADLB_DATA_ID_NULL;
  }


  adlb_data_type tmp_type;
  rc = type_from_obj(interp, objv, objv[argpos++], &tmp_type);
  TCL_CHECK(rc);
  *type = tmp_type;

  // Process type-specific params
  switch (*type)
  {
    case ADLB_DATA_TYPE_CONTAINER:
      TCL_CONDITION(objc > argpos + 1,
                    "adlb::create type=container requires "
                    "key and value types!");
      adlb_data_type key_type, val_type;
      rc = type_from_obj(interp, objv, objv[argpos++], &key_type);
      TCL_CHECK(rc);
      rc = type_from_obj(interp, objv, objv[argpos++], &val_type);
      TCL_CHECK(rc);
      type_extra->CONTAINER.key_type = key_type;
      type_extra->CONTAINER.val_type = val_type;
      break;
    default:
      break;
  }

  // Process create props if present
  adlb_create_props defaults = DEFAULT_CREATE_PROPS;
  *props = defaults;

  if (argpos < objc) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &(props->read_refcount));
    TCL_CHECK_MSG(rc, "adlb::create could not get read_refcount argument");
  }

  if (argpos < objc) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &(props->write_refcount));
    TCL_CHECK_MSG(rc, "adlb::create could not get write_refcount argument");
  }

  if (argpos < objc) {
    int permanent;
    rc = Tcl_GetBooleanFromObj(interp, objv[argpos++], &permanent);
    TCL_CHECK_MSG(rc, "adlb::create could not get permanent argument");
    props->permanent = permanent != 0;
  }

  return TCL_OK;
}

/**
   usage: adlb::create <id> <type> [<extra for type>]
          [ <read_refcount> [ <write_refcount> [ <permanent> ] ] ]
   if <id> is adlb::NULL_ID, returns a newly created id
   @param extra is only used for files and containers
*/
static int
ADLB_Create_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  int rc;
  adlb_datum_id id = ADLB_DATA_ID_NULL;
  adlb_data_type type = ADLB_DATA_TYPE_NULL ;
  adlb_type_extra type_extra = ADLB_TYPE_EXTRA_NULL;
  adlb_create_props props;
  extract_create_props(interp, true, 1, objc, objv,
                       &id, &type, &type_extra, &props);

  adlb_datum_id new_id = ADLB_DATA_ID_NULL;

  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      rc = ADLB_Create_integer(id, props, &new_id);
      break;
    case ADLB_DATA_TYPE_FLOAT:
      rc = ADLB_Create_float(id, props, &new_id);
      break;
    case ADLB_DATA_TYPE_STRING:
      rc = ADLB_Create_string(id, props, &new_id);
      break;
    case ADLB_DATA_TYPE_BLOB:
      rc = ADLB_Create_blob(id, props, &new_id);
      break;
    case ADLB_DATA_TYPE_REF:
      rc = ADLB_Create_ref(id, props, &new_id);
      break;
    case ADLB_DATA_TYPE_FILE_REF:
      rc = ADLB_Create_file_ref(id, props, &new_id);
      break;
    case ADLB_DATA_TYPE_STRUCT:
      rc = ADLB_Create_struct(id, props, &new_id);
      break;
    case ADLB_DATA_TYPE_CONTAINER: {
      rc = ADLB_Create_container(id, type_extra.CONTAINER.key_type,
                    type_extra.CONTAINER.val_type, props, &new_id);
      break;
    }
    case ADLB_DATA_TYPE_NULL:
    default:
      Tcl_AddErrorInfo(interp,
                       "adlb::create: unknown type!");
      return TCL_ERROR;
      break;

  }

  if (id == ADLB_DATA_ID_NULL) {
    // need to return new ID
    Tcl_Obj* result = Tcl_NewADLB_ID(new_id);
    Tcl_SetObjResult(interp, result);
  }

  TCL_CONDITION(rc == ADLB_SUCCESS, "adlb::create <%lli> failed!", id);
  return TCL_OK;
}


/**
   usage: adlb::multicreate [list of variable specs]*
   each list contains:
          <id> <type> [<extra for type>]
          [ <read_refcount> [ <write_refcount> [ <permanent> ] ] ]
   returns a list of newly created ids
*/
static int
ADLB_Multicreate_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  int rc;
  int count = objc - 1;
  ADLB_create_spec specs[count];

  for (int i = 0; i < count; i++) {
    int n;
    Tcl_Obj **elems;
    rc = Tcl_ListObjGetElements(interp, objv[i + 1], &n, &elems);
    TCL_CONDITION(rc == TCL_OK, "adlb::multicreate arg %i must be list", i);
    ADLB_create_spec *spec = &(specs[i]);
    rc = extract_create_props(interp, false, 0, n, elems, &(spec->id),
              &(spec->type), &(spec->type_extra), &(spec->props));
    TCL_CHECK(rc);
  }

  rc = ADLB_Multicreate(specs, count);
  TCL_CONDITION(rc == ADLB_SUCCESS, "adlb::multicreate failed!");

  // Build list to return
  Tcl_Obj *tcl_ids[count];
  for (int i = 0; i < count; i++) {
    tcl_ids[i] = Tcl_NewADLB_ID(specs[i].id);
  }
  Tcl_SetObjResult(interp, Tcl_NewListObj(count, tcl_ids));
  return TCL_OK;
}

static int
ADLB_Exists_Impl(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[], bool has_subscript)
{
  int min_args = has_subscript ? 3 : 2;
  TCL_CONDITION(objc >= min_args, "requires at least %i arguments", min_args);
  int argpos = 1;

  adlb_datum_id id;
  bool b;
  int rc;
  rc = Tcl_GetADLB_ID(interp, objv[argpos++], &id);
  TCL_CHECK_MSG(rc, "requires a data ID");
    
  char *subscript = NULL;
  if (has_subscript)
  {
    subscript = Tcl_GetString(objv[argpos++]);
    TCL_CONDITION(subscript != NULL, "bad subscript argument");
  }

  adlb_refcounts decr = ADLB_NO_RC;
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.read_refcount);
    TCL_CHECK_MSG(rc, "Expected integer argument");
  }

  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.write_refcount);
    TCL_CHECK_MSG(rc, "Expected integer argument");
  }

  TCL_CONDITION(argpos == objc, "unexpected trailing args at %ith arg", argpos);

  rc = ADLB_Exists(id, subscript, &b, decr);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%lli> failed!", id);
  Tcl_Obj* result = Tcl_NewBooleanObj(b);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: adlb::exists <id> [ <read decr> ] [ <write decr> ]
 */
static int
ADLB_Exists_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return ADLB_Exists_Impl(cdata, interp, objc, objv, false);
}

/**
   usage: adlb::exists_sub <id> [<subscript>] [ <read decr> ] [ <write decr> ]
 */
static int
ADLB_Exists_Sub_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return ADLB_Exists_Impl(cdata, interp, objc, objv, true);
}

/**
  Take a Tcl object and an ADLB type and extract the binary representation
  type: adlb data type code
  caller_buffer: optional static buffer to use
  result: serialized result data.  Either has malloced buffer,
          or pointer to caller_buffer->data
 */
int
tcl_obj_to_adlb_data(Tcl_Interp *interp, Tcl_Obj *const objv[],
                adlb_data_type type, const adlb_type_extra *extra,
                Tcl_Obj *obj, const adlb_buffer *caller_buffer,
                adlb_binary_data* result)
{
  int rc;

  // temporary storage on stack
  adlb_datum_storage tmp;
  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      rc = Tcl_GetADLBInt(interp, obj, &tmp.INTEGER);
      TCL_CHECK_MSG(rc, "adlb extract int from %s failed!", Tcl_GetString(obj));
      break;
    case ADLB_DATA_TYPE_REF:
      rc = Tcl_GetADLB_ID(interp, obj, &tmp.REF);
      TCL_CHECK_MSG(rc, "adlb extract int from %s failed!",
                      Tcl_GetString(obj));
      break;
    case ADLB_DATA_TYPE_FLOAT:
      rc = Tcl_GetDoubleFromObj(interp, obj, &tmp.FLOAT);
      TCL_CHECK_MSG(rc, "adlb extract double from %s failed!",
                      Tcl_GetString(obj));
      break;
    case ADLB_DATA_TYPE_STRING:
      tmp.STRING.value = Tcl_GetStringFromObj(obj, &tmp.STRING.length);
      TCL_CONDITION(result != NULL, "adlb extract string from %p failed!",
                      obj);
      tmp.STRING.length++; // Account for null byte
      TCL_CONDITION(tmp.STRING.length < ADLB_DATA_MAX,
          "adlb: string too long (%i bytes)", tmp.STRING.length);
      break;
    case ADLB_DATA_TYPE_BLOB:
    {
      adlb_datum_id tmp_id;
      // Take list-based blob representation
      int rc = extract_tcl_blob(interp, objv, obj, &tmp.BLOB, &tmp_id);
      TCL_CHECK(rc);
      break;
    }
    case ADLB_DATA_TYPE_STRUCT:
    {
      TCL_CONDITION(extra != NULL, "Must specify struct type to convert"
                                    "dict to struct")
      int rc = tcl_dict_to_packed_struct(interp, objv, obj, 
                                         extra->STRUCT.struct_type,
                                         caller_buffer, result);
      TCL_CHECK(rc);
      // DONE!
      return TCL_OK;
    }
    case ADLB_DATA_TYPE_FILE_REF:
    {
      // Extract from Tcl list and pack into struct
      Tcl_Obj **fr_elems;
      int fr_count;
      rc = Tcl_ListObjGetElements(interp, obj, &fr_count, &fr_elems);
      TCL_CONDITION(rc == TCL_OK, "Failed interpreting object as list: %s",
                Tcl_GetString(obj));
      TCL_CONDITION(fr_count == FILE_REF_ELEMS, "Expected 3-element list as ADLB file "
                     "representation, but instead got %d-element list: %s",
                     fr_count, Tcl_GetString(obj));
      rc = Tcl_GetADLB_ID(interp, fr_elems[FILE_REF_STATUS], &tmp.FILE_REF.status_id);
      TCL_CHECK_MSG(rc, "adlb extract ID from %s failed!",
                      Tcl_GetString(fr_elems[FILE_REF_STATUS]));
      rc = Tcl_GetADLB_ID(interp, fr_elems[FILE_REF_FILENAME], &tmp.FILE_REF.filename_id);
      TCL_CHECK_MSG(rc, "adlb extract ID from %s failed!",
                      Tcl_GetString(fr_elems[FILE_REF_FILENAME]));
      int tmp_mapped;
      rc = Tcl_GetIntFromObj(interp, fr_elems[FILE_REF_MAPPED], &tmp_mapped);
      TCL_CHECK_MSG(rc, "adlb extract bool from %s failed!",
                      Tcl_GetString(fr_elems[FILE_REF_MAPPED]));
      tmp.FILE_REF.mapped = tmp_mapped != 0;
      break;
    }
    default:
      printf("unknown type %i!\n", type);
      return TCL_ERROR;
  }
  
  // Make sure data is serialized in contiguous memory
  adlb_data_code dc = ADLB_Pack(&tmp, type, caller_buffer, result);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
                "Error packing data type %i into buffer", type);

  // Make sure caller owns the memory (i.e. it's not a pointer to tmp)
  dc = ADLB_Own_data(caller_buffer, result);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
                "Error getting ownership of buffer for data type %i", type);

  return TCL_OK;
}

/*
   Build a representation of an ADLB struct using Tcl dicts. E.g.

   ADLB struct:
     [ "a.foo" = 1, "a.bar" = "hello", "b" = 3.14 ]
   Tcl Dict:
     { a: { foo: 1, bar: "hello" }, b: 3.14 }

    If extra type info is provided, checks type is as expected
 */
static int
packed_struct_to_tcl_dict(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         const void *data, int length,
                         const adlb_type_extra *extra, Tcl_Obj **result)
{
  assert(data != NULL);
  assert(length >= 0);
  assert(result != NULL);
  int rc;

  adlb_struct_type st;

  adlb_packed_struct_hdr *hdr = (adlb_packed_struct_hdr *)data;

  TCL_CONDITION(length >= sizeof(*hdr), "Not enough data for header");

  st = hdr->type;
  ADLB_STRUCT_TYPE_CHECK(st);

  TCL_CONDITION(extra == NULL || st == extra->STRUCT.struct_type,
                "Expected struct type %i but got %i",
                extra->STRUCT.struct_type, st);

  adlb_struct_format *f = &adlb_struct_formats.types[st];
  TCL_CONDITION(length >= sizeof(*hdr) + sizeof(hdr->field_offsets[0]) *
                (size_t)f->field_count, "Not enough data for header");

  Tcl_Obj *result_dict = Tcl_NewDictObj();

  for (int i = 0; i < f->field_count; i++)
  {
    const char *name = f->field_names[i];
    // Find slice of buffer for the field
    int offset = hdr->field_offsets[i];
    const void *field_data = data + offset;
    int field_data_length;
    if (i == f->field_count - 1)
      field_data_length = (int)(length - offset);
    else
      field_data_length = hdr->field_offsets[i + 1] - offset;

    TCL_CONDITION(offset >= 0,
        "invalid struct buffer: negative offset %i for field %s", offset, name);
    TCL_CONDITION(field_data_length >= 0,
        "invalid struct buffer: negative length %i for field %s",
                                                      field_data_length, name);
    TCL_CONDITION(offset + field_data_length <= length,
        "invalid struct buffer: field %s past buffer end: %d+%d vs %d", name,
        offset, field_data_length, length);

    // Create a TCL object for the field data
    Tcl_Obj *field_tcl_obj;
    rc = adlb_data_to_tcl_obj(interp, objv, ADLB_DATA_ID_NULL,
                  f->field_types[i], NULL, field_data, field_data_length,
                  &field_tcl_obj);
    TCL_CHECK_MSG(rc, "Error building tcl object for field %s", name);

    // Add it to nested dicts           
    rc = Tcl_DictObjPutKeyList(interp, result_dict,
          f->field_nest_level[i] + 1, f->field_parts[i], field_tcl_obj);
    TCL_CHECK_MSG(rc, "Error inserting tcl object for field %s", name);
  }

  *result = result_dict;
  return TCL_OK;
}

static int
tcl_dict_to_packed_struct(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj *dict, adlb_struct_type struct_type,
                         const adlb_buffer *caller_buffer,
                         adlb_binary_data* result)
{
  int rc;
  adlb_data_code dc;

  ADLB_STRUCT_TYPE_CHECK(struct_type);
  adlb_struct_format *f = &adlb_struct_formats.types[struct_type];


  adlb_buffer buf;
  // Use double pointer since caller_data may be changed
  adlb_packed_struct_hdr **hdr = (adlb_packed_struct_hdr**)&buf.data;

  bool using_caller_buffer;
  // TODO: would be nice to be able to make more direct use of other functions
  // Pack the struct into a buffer 
  int hdr_len = (int)sizeof(**hdr) + f->field_count *
                (int)sizeof((*hdr)->field_offsets[0]);
  ADLB_Init_buf(caller_buffer, &buf, &using_caller_buffer, hdr_len);
    
  (*hdr)->type = struct_type;
  int pos = hdr_len;

  for (int i = 0; i < f->field_count; i++)
  {
    Tcl_Obj *curr = dict;
    for (int j = 0; j <= f->field_nest_level[i]; j++)
    {
      Tcl_Obj *val;
      rc = Tcl_DictObjGet(interp, curr, f->field_parts[i][j], &val);
      TCL_CHECK(rc);
      TCL_CONDITION(val != NULL, 
            "Could not find field \"%s\". Lookup \"%s\" in {%s}",
            f->field_names[i], Tcl_GetString(f->field_parts[i][j]),
            Tcl_GetString(curr));
      curr = val;
    }
    // Get binary version of field
    adlb_binary_data field_data;
    // TODO: Don't support nested elements with extra type info
    rc = tcl_obj_to_adlb_data(interp, objv, f->field_types[i], NULL,
                    curr, NULL, &field_data);
    TCL_CHECK(rc);
    
    // Append field to buffer and mark offset
    (*hdr)->field_offsets[i] = pos;
    dc = ADLB_Resize_buf(&buf, &using_caller_buffer, pos + field_data.length);
    TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error resizing buffer");
    memcpy(&buf.data[pos], field_data.data, (size_t)field_data.length);
    pos += field_data.length;


    ADLB_Free_binary_data(&field_data);
  }

  result->data = result->caller_data = buf.data;
  result->length = pos;
  return TCL_OK;
}


/**
   usage: adlb::store <id> <type> [ <extra> ] <value>
                      [ <decrement writers> ] [ <decrement readers> ]
   @param extra any extra info for type, e.g. struct type when storing struct
   @param value value to be stored
   @param decrement writers Optional  Decrement the writers reference
                count by this amount.  Default if not provided is 1
   @param decrement readers Optional  Decrement the readers reference
                count by this amount.  Default if not provided is 0
*/
static int
ADLB_Store_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 4, "requires at least 4 args!");
  int argpos = 1;

  adlb_datum_id id;
  Tcl_GetADLB_ID(interp, objv[argpos++], &id);

  adlb_data_type type;
  bool has_extra;
  adlb_type_extra extra;
  int rc = type_from_obj_extra(interp, objv, objv[argpos++], &type,
                         &has_extra, &extra);
  TCL_CHECK(rc);

  adlb_binary_data data; 
  rc = tcl_obj_to_adlb_data(interp, objv, type, has_extra ? &extra : NULL,
                            objv[argpos++], &xfer_buf, &data);
  TCL_CHECK_MSG(rc, "<%lli> failed, could not extract data!", id);

  // Handle optional refcount spec
  adlb_refcounts decr = ADLB_WRITE_RC; // default is to decr writers
  if (argpos < objc) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.write_refcount);
    TCL_CHECK_MSG(rc, "decrement arg must be int!");
  }
  
  if (argpos < objc) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.read_refcount);
    TCL_CHECK_MSG(rc, "decrement arg must be int!");
  }

  TCL_CONDITION(argpos == objc,
          "extra trailing arguments starting at argument %i", argpos);

  // DEBUG_ADLB("adlb::store: <%lli>=%s", id, data);
  rc = ADLB_Store(id, NULL, type, data.data, data.length, decr);
  
  // Free if needed
  if (data.data != xfer_buf.data)
    ADLB_Free_binary_data(&data);

  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::store <%lli> failed!", id);

  return TCL_OK;
}

static inline void report_type_mismatch(adlb_data_type expected,
                                        adlb_data_type actual);

/**
   usage: adlb::retrieve <id> [<type>]
   @param type: if provided, then check that data is of correct type
   returns the contents of the adlb datum converted to a tcl object
*/
static int
ADLB_Retrieve_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  return ADLB_Retrieve_Impl(cdata, interp, objc, objv, false);
}

/**
   usage: adlb::retrieve_decr <id> <decr> [<type>]
   same as retrieve, but also decrement read reference count by <decr>
*/
static int
ADLB_Retrieve_Decr_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  return ADLB_Retrieve_Impl(cdata, interp, objc, objv, true);
}

static int
ADLB_Retrieve_Impl(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[], bool decr)
{
  if (decr) {
    TCL_CONDITION((objc == 3 || objc == 4),
                  "requires 2 or 3 args!");
  } else {
    TCL_CONDITION((objc == 2 || objc == 3),
                  "requires 1 or 2 args!");
  }

  int rc;
  adlb_datum_id id;
  int argpos = 1;
  rc = Tcl_GetADLB_ID(interp, objv[argpos++], &id);
  TCL_CHECK_MSG(rc, "requires id!");


  int decr_amount = 0;
  if (decr) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr_amount);
    TCL_CHECK_MSG(rc, "requires decr amount!");
  }

  adlb_data_type given_type = ADLB_DATA_TYPE_NULL;
  bool has_extra = false;
  adlb_type_extra extra;
  if (argpos < objc)
  {
    rc = type_from_obj_extra(interp, objv, objv[argpos++], &given_type,
                            &has_extra, &extra);
    TCL_CHECK_MSG(rc, "arg %i must be valid type!", argpos);
  }

  // Retrieve the data, actual type, and length from server
  adlb_data_type type;
  int length;
  adlb_retrieve_rc refcounts = ADLB_RETRIEVE_NO_RC;
  refcounts.decr_self.read_refcount = decr_amount;
  rc = ADLB_Retrieve(id, NULL, refcounts, &type, xfer, &length);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%lli> failed!", id);
  TCL_CONDITION(length >= 0, "adlb::retrieve <%lli> not found!",
                            id);

  // Type check
  if ((given_type != ADLB_DATA_TYPE_NULL &&
       given_type != type))
  {
    report_type_mismatch(given_type, type);
    return TCL_ERROR;
  }

  // Unpack from xfer to Tcl object
  Tcl_Obj* result;
  rc = adlb_data_to_tcl_obj(interp, objv, id, type, has_extra ? &extra : NULL,
                            xfer, length, &result);
  TCL_CHECK(rc);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}


/**
   interp, objv, id, and length: just for error checking and messages
   If object is a blob, this converts it to a string
 */
int
adlb_data_to_tcl_obj(Tcl_Interp *interp, Tcl_Obj *const objv[], adlb_datum_id id,
                adlb_data_type type, const adlb_type_extra *extra,
                const void *data, int length, Tcl_Obj** result)
{
  adlb_datum_storage tmp;
  adlb_data_code dc;
  assert(length >= 0);
  assert(length < ADLB_DATA_MAX);

  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      dc = ADLB_Unpack_integer(&tmp.INTEGER, data, length);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
            "Retrieve failed due to error unpacking data %i", dc);
      *result = Tcl_NewADLBInt(tmp.INTEGER);
      break;
    case ADLB_DATA_TYPE_REF:
      dc = ADLB_Unpack_ref(&tmp.REF, data, length);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
            "Retrieve failed due to error unpacking data %i", dc);
      *result = Tcl_NewADLB_ID(tmp.REF);
      break;
    case ADLB_DATA_TYPE_FLOAT:
      dc = ADLB_Unpack_float(&tmp.FLOAT, data, length);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
            "Retrieve failed due to error unpacking data %i", dc);
      *result = Tcl_NewDoubleObj(tmp.FLOAT);
      break;
    case ADLB_DATA_TYPE_STRING:
      // Don't allocate new memory
      // Ok to cast away const since TCL will copy string anyway
      dc = ADLB_Unpack_string(&tmp.STRING, (void *)data,
                              length, false);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
            "Retrieve failed due to error unpacking data %i", dc);
      *result = Tcl_NewStringObj(tmp.STRING.value, tmp.STRING.length-1);
      break;
    case ADLB_DATA_TYPE_BLOB:
      // Do allocate new memory
      // Ok to cast away const since we're copying blob
      dc = ADLB_Unpack_blob(&tmp.BLOB, (void *)data, length, true);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
            "Retrieve failed due to error unpacking data %i", dc);
      // Don't provide id to avoid blob caching
      *result = build_tcl_blob(tmp.BLOB.value, tmp.BLOB.length,
                               ADLB_DATA_ID_NULL);
      break;
    case ADLB_DATA_TYPE_FILE_REF:
    {
      dc = ADLB_Unpack_file_ref(&tmp.FILE_REF, data, length);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
            "Retrieve failed due to error unpacking data %i", dc);
      
      // Pack into Tcl list representation
      Tcl_Obj *file_ref_elems[FILE_REF_ELEMS];
      file_ref_elems[FILE_REF_STATUS] = Tcl_NewADLB_ID(tmp.FILE_REF.status_id);
      file_ref_elems[FILE_REF_FILENAME] =
                                      Tcl_NewADLB_ID(tmp.FILE_REF.filename_id);
      file_ref_elems[FILE_REF_MAPPED] =
                                    Tcl_NewIntObj(tmp.FILE_REF.mapped ? 1 : 0);
      *result = Tcl_NewListObj(FILE_REF_ELEMS, file_ref_elems);
      break;
    }
    case ADLB_DATA_TYPE_STRUCT:
      return packed_struct_to_tcl_dict(interp, objv, data, length,
                                       extra, result);
    default:
      *result = NULL;
      TCL_CONDITION(false, "unsupported type: %s(%i)",
                           ADLB_Data_type_tostring(type), type);
  }
  return TCL_OK;
}

static inline void
report_type_mismatch(adlb_data_type expected,
                     adlb_data_type actual)
{
  printf("type mismatch: expected: %s - received: %s\n",
                      ADLB_Data_type_tostring(expected),
                      ADLB_Data_type_tostring(actual));
}

/**
   usage: adlb::acquire_ref <id> <type> <increment> <decrement>
   Retrieve and increment read refcount of referenced ids by increment.
   Decrement refcount of this id by decrement
*/
static int
ADLB_Acquire_Ref_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  return ADLB_Acquire_Ref_Impl(cdata, interp, objc, objv, false);
}

/**
   usage: adlb::acquire_sub_ref <id> <subscript> <type> <increment> <decrement>
   Retrieve value at subscript and increment read refcount of referenced
   ids by increment.
   Decrement refcount of this id by decrement
*/
static int
ADLB_Acquire_Sub_Ref_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  return ADLB_Acquire_Ref_Impl(cdata, interp, objc, objv, true);
}

static int
ADLB_Acquire_Ref_Impl(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[], bool has_subscript)
{
  TCL_ARGS(has_subscript ? 6 : 5);
  int argpos = 1;
  int rc;
  adlb_datum_id id;
  rc = Tcl_GetADLB_ID(interp, objv[argpos++], &id);
  TCL_CHECK_MSG(rc, "requires id!");

  char *subscript = NULL;
  if (has_subscript)
  {
    subscript = Tcl_GetString(objv[argpos++]);
  }

  adlb_data_type expected_type;
  bool has_extra;
  adlb_type_extra extra;
  rc = type_from_obj_extra(interp, objv, objv[argpos++], &expected_type,
                          &has_extra, &extra);
  TCL_CHECK(rc);
  
  adlb_retrieve_rc refcounts = ADLB_RETRIEVE_NO_RC;
  rc = Tcl_GetIntFromObj(interp, objv[argpos++],
            &refcounts.incr_referand.read_refcount);
  TCL_CHECK_MSG(rc, "requires incr referand amount!");

  rc = Tcl_GetIntFromObj(interp, objv[argpos++],
            &refcounts.decr_self.read_refcount);
  TCL_CHECK_MSG(rc, "requires decr amount!");
  // TODO: support acquiring write reference

  // Retrieve the data, actual type, and length from server
  adlb_data_type type;
  int length;
  rc = ADLB_Retrieve(id, subscript, refcounts, &type, xfer, &length);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%lli> failed!", id);
  TCL_CONDITION(length >= 0, "<%lli> not found!", id);

  // Type check
  if (expected_type != type)
  {
    report_type_mismatch(expected_type, type);
    return TCL_ERROR;
  }

  // Unpack from xfer to Tcl object
  Tcl_Obj* result;
  rc = adlb_data_to_tcl_obj(interp, objv, id, type, has_extra ? &extra : NULL,
                            xfer, length, &result);
  TCL_CHECK(rc);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}


static inline int
set_enumerate_params(Tcl_Interp *interp, Tcl_Obj *const objv[],
                     const char* token, bool *include_keys,
                     bool *include_vals);

static inline int
enumerate_object(Tcl_Interp *interp, Tcl_Obj *const objv[],
                      adlb_datum_id id,
                      bool include_keys, bool include_vals,
                      char* data, int length, int records,
                      adlb_type_extra kv_type, Tcl_Obj** result);

/**
   usage:
   adlb::enumerate <id> subscripts|members|dict|count
                   <count>|all <offset> [<read decr>] [<write decr>]

   subscripts: return list of subscript strings
   members: return list of member TDs
   dict: return dict mapping subscripts to TDs
   count: return integer count of container elements
 */
static int
ADLB_Enumerate_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 5, "must have at least 5 arguments");
  int rc;
  int argpos = 1;
  adlb_datum_id container_id;
  int count;
  int offset;
  rc = Tcl_GetADLB_ID(interp, objv[argpos++], &container_id);
  TCL_CHECK_MSG(rc, "requires container id!");
  char* token = Tcl_GetStringFromObj(objv[argpos++], NULL);
  TCL_CONDITION(token, "requires token!");
  // This argument is either the integer count or "all", all == -1

  Tcl_Obj *count_obj = objv[argpos++];
  char* tmp = Tcl_GetStringFromObj(count_obj, NULL);
  if (strcmp(tmp, "all"))
  {
    rc = Tcl_GetIntFromObj(interp, count_obj, &count);
    TCL_CHECK_MSG(rc, "requires count!");
  }
  else
    count = -1;
  rc = Tcl_GetIntFromObj(interp, objv[argpos++], &offset);
  TCL_CHECK_MSG(rc, "requires offset!");

  adlb_refcounts decr = ADLB_NO_RC;
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.read_refcount);
    TCL_CHECK_MSG(rc, "Expected integer argument");
  }
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.write_refcount);
    TCL_CHECK_MSG(rc, "Expected integer argument");
  }

  TCL_CONDITION(argpos == objc, "unexpected trailing args at %ith arg", argpos);

  // Set up call
  bool include_keys;
  bool include_vals;
  void *data = NULL;
  int data_length;
  int records;
  adlb_type_extra kv_type;
  rc = set_enumerate_params(interp, objv, token, &include_keys, &include_vals);
  TCL_CHECK_MSG(rc, "unknown token %s!", token);


  // Call ADLB
  rc = ADLB_Enumerate(container_id, count, offset, decr,
                      include_keys, include_vals,
                      &data, &data_length, &records, &kv_type);

  // Return results to Tcl
  Tcl_Obj* result;
  rc = enumerate_object(interp, objv, container_id,
                        include_keys, include_vals,
                        data, data_length, records, kv_type, &result);
  TCL_CHECK(rc);

  if (data != NULL)
    free(data);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   Interpret args and set params
   interp, objv provided for error handling
 */
static inline int
set_enumerate_params(Tcl_Interp *interp, Tcl_Obj *const objv[],
                     const char* token, bool *include_keys,
                     bool *include_vals)
{
  if (!strcmp(token, "subscripts"))
  {
    *include_keys = true;
    *include_vals = false;
  }
  else if (!strcmp(token, "members"))
  {
    *include_keys = false;
    *include_vals = true;
  }
  else if (!strcmp(token, "dict"))
  {
    *include_keys = true;
    *include_vals = true;
  }
  else if (!strcmp(token, "count"))
  {
    *include_keys = false;
    *include_vals = false;
  }
  else
  {
    return TCL_ERROR;
  }
  return TCL_OK;
}

/**
   Simple string struct for indices of strings
   Note: s may not be NULL-terminated: user must refer to length
 */
struct record_entry
{
  char* s;
  int length;
};

/**
   Pack ADLB_Enumerate results into Tcl object
 */
static inline int
enumerate_object(Tcl_Interp *interp, Tcl_Obj *const objv[],
                      adlb_datum_id id,
                      bool include_keys, bool include_vals,
                      char* data, int length, int records,
                      adlb_type_extra kv_type, Tcl_Obj** result)
{
  int rc;
  int list_buf_len = 0;
  if (include_keys && include_vals)
  {
    *result = Tcl_NewDictObj();
  } 
  else if (include_keys || include_vals)
  {
    // Create list at end
    *result = NULL;
    list_buf_len = records;
  }
  else
  {
    // Just return count
    *result = Tcl_NewIntObj(records);
    return TCL_OK;
  }

  // Buffer for list
  Tcl_Obj * list_buf[list_buf_len];

  // Position in buffer
  int pos = 0;
  int consumed; // Amount just consumed

  for (int i = 0; i < records; i++)
  {
    Tcl_Obj *key = NULL, *val = NULL;
    if (include_keys)
    {
      cutil_long key_len;
      consumed = vint_decode(data + pos, length - pos, &key_len);
      TCL_CONDITION(consumed >= 1, "Corrupted message received");
      pos += consumed;
      TCL_CONDITION(key_len <= length - pos, 
                    "Truncated/corrupted message received");
      // Key currently must be string
      key = Tcl_NewStringObj(data + pos, (int)key_len);
      pos += (int)key_len;
    }

    if (include_vals)
    {
      cutil_long val_len;
      consumed = vint_decode(data + pos, length - pos, &val_len);
      TCL_CONDITION(consumed >= 1, "Corrupted message received");
      pos += consumed;
      TCL_CONDITION(val_len <= length - pos, 
                    "Truncated/corrupted message received");
      rc = adlb_data_to_tcl_obj(interp, objv, id, kv_type.CONTAINER.val_type,
                NULL, data + pos, (int)val_len, &val);
                    
      pos += (int)val_len;
    }

    if (include_keys && include_vals)
    {
      rc = Tcl_DictObjPut(interp, *result, key, val);
      TCL_CHECK(rc);
    }
    else if (include_keys)
    {
      list_buf[i] = key;
    }
    else
    { assert(include_vals);
      list_buf[i] = val;
    }
  }

  if (!include_keys || !include_vals)
  {
    // Build list from elements
    *result = Tcl_NewListObj(records, list_buf);
  }

  return TCL_OK;
}

static inline int
ADLB_Retrieve_Blob_Impl(ClientData cdata, Tcl_Interp *interp,
                        int objc, Tcl_Obj *const objv[], bool decr);

/**
   Copy a blob from the distributed store into a local blob
   in the memory of this process
   Must be freed with adlb::blob_free
   usage: adlb::retrieve_blob <id> => [ list <pointer> <length> ]
 */
static int
ADLB_Retrieve_Blob_Cmd(ClientData cdata, Tcl_Interp *interp,
                       int objc, Tcl_Obj *const objv[])
{
  return ADLB_Retrieve_Blob_Impl(cdata, interp, objc, objv, false);
}

static int
ADLB_Retrieve_Blob_Decr_Cmd(ClientData cdata, Tcl_Interp *interp,
                            int objc, Tcl_Obj *const objv[])
{
  return ADLB_Retrieve_Blob_Impl(cdata, interp, objc, objv, true);
}

static inline int
ADLB_Retrieve_Blob_Impl(ClientData cdata, Tcl_Interp *interp,
                        int objc, Tcl_Obj *const objv[], bool decr)
{
  if (decr) {
    TCL_ARGS(3);
  } else {
    TCL_ARGS(2);
  }

  int rc;
  adlb_datum_id id;
  rc = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");

  adlb_retrieve_rc refcounts = ADLB_RETRIEVE_NO_RC;
  /* Only decrement if refcounting enabled */
  if  (decr) {
    rc = Tcl_GetIntFromObj(interp, objv[2],
                          &refcounts.decr_self.read_refcount);
    TCL_CHECK_MSG(rc, "requires id!");
  }

  // Retrieve the blob data
  adlb_data_type type;
  int length;
  rc = ADLB_Retrieve(id, NULL, refcounts, &type, xfer, &length);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%lli> failed!", id);
  TCL_CONDITION(type == ADLB_DATA_TYPE_BLOB,
                "type mismatch: expected: %i actual: %i",
                ADLB_DATA_TYPE_BLOB, type);

  // Allocate the local blob
  void* blob = malloc((size_t)length);
  assert(blob);

  // Copy the blob data
  memcpy(blob, xfer, (size_t)length);

  // Link the blob into the cache
  bool b = table_lp_add(&blob_cache, id, blob);
  ASSERT(b);

  // printf("retrieved blob: [ %p %i ]\n", blob, length);

  Tcl_SetObjResult(interp, build_tcl_blob(blob, length, id));
  return TCL_OK;
}

static Tcl_Obj *build_tcl_blob(void *data, int length, adlb_datum_id id)
{
  // Pack and return the blob pointer, length, turbine ID as Tcl list
  int blob_elems = (id == ADLB_DATA_ID_NULL) ? 2 : 3;

  Tcl_Obj* list[blob_elems];
  list[0] = Tcl_NewPtr(data);
  list[1] = Tcl_NewIntObj(length);
  if (id != ADLB_DATA_ID_NULL)
    list[2] = Tcl_NewADLB_ID(id);
  return Tcl_NewListObj(blob_elems, list);
}

/*
  Construct a Tcl blob object, which has two representations:
   This handles two cases:
    -> A three element list representing a blob retrieved from the
       data store, in which case we fill in id
    -> A two element list representing a locally allocated blob,
        in which case we set id = ADLB_DATA_ID_NULL
 */

static int extract_tcl_blob(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj *obj, adlb_blob_t *blob, adlb_datum_id *id)
{
  int rc;
  Tcl_Obj **elems;
  int elem_count;
  rc = Tcl_ListObjGetElements(interp, obj, &elem_count, &elems);
  TCL_CONDITION(rc == TCL_OK && (elem_count == 2 || elem_count == 3),
                "Error interpreting %s as blob list", Tcl_GetString(obj));
  
  rc = Tcl_GetPtr(interp, elems[0], &blob->value);
  TCL_CHECK_MSG(rc, "Error extracting pointer from %s", Tcl_GetString(elems[0]));

  rc = Tcl_GetIntFromObj(interp, elems[1], &blob->length);
  TCL_CHECK_MSG(rc, "Error extracting blob length from %s",
                Tcl_GetString(elems[1]));
  if (elem_count == 2)
  {
    *id = ADLB_DATA_ID_NULL;
  }
  else
  {
    rc = Tcl_GetADLB_ID(interp, elems[2], id);
    TCL_CHECK_MSG(rc, "Error extracting ID from %s", Tcl_GetString(elems[2]));
  }
  return TCL_OK;
}

static int uncache_blob(Tcl_Interp *interp, int objc, Tcl_Obj *const objv[],
                        adlb_datum_id id, bool *found_in_cache) {
  void* blob = table_lp_remove(&blob_cache, id);
  *found_in_cache = blob != NULL;
  if (*found_in_cache)
  {
    free(blob);
  }
  return TCL_OK;
}

/**
   Free a local blob cached with adlb::blob_cache
   usage: adlb::blob_free <id>
 */
static int
ADLB_Blob_Free_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int rc;
  adlb_datum_id id;
  rc = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");

  bool found;
  rc = uncache_blob(interp, objc, objv, id, &found);
  TCL_CHECK(rc);
  TCL_CONDITION(found, "blob not cached: <%lli>", id);
  return TCL_OK;
}

/**
   Free a local blob object.
   If the blob contains an ADLB datum id, then it should be in the
   turbine blob cache, so uncache it.
   If the blob is not associated with a datum id, then it
   was allocated with malloc, so free it
   usage: adlb::local_blob_free <struct>
 */
static int
ADLB_Local_Blob_Free_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int rc;
  adlb_blob_t blob;
  adlb_datum_id id;

  rc = extract_tcl_blob(interp, objv, objv[1], &blob, &id);
  TCL_CHECK(rc);

  if (id == ADLB_DATA_ID_NULL)
  {
    if (blob.value != NULL)
      free(blob.value);
    return TCL_OK;
  } else {
    printf("uncache_blob: %s", Tcl_GetString(objv[1]));
    bool cached;
    rc = uncache_blob(interp, objc, objv, id, &cached);
    TCL_CHECK(rc);
    
    if (!cached)
      // Wasn't managed by cache
      free(blob.value);

    return TCL_OK;
  }
}

/**
   adlb::store_blob <id> <pointer> <length> [<decr>]
 */
static int
ADLB_Store_Blob_Cmd(ClientData cdata, Tcl_Interp *interp,
                    int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 4 || objc == 5,
                "adlb::store_blob requires 4 or 5 args!");

  int rc;
  adlb_datum_id id;
  void* pointer;
  int length;
  rc = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");
  rc = Tcl_GetPtr(interp, objv[2], &pointer);
  TCL_CHECK_MSG(rc, "requires pointer!");
  rc = Tcl_GetIntFromObj(interp, objv[3], &length);
  TCL_CHECK_MSG(rc, "requires length!");

  adlb_refcounts decr = ADLB_WRITE_RC;
  if (objc == 5) {
    rc = Tcl_GetIntFromObj(interp, objv[4], &decr.write_refcount);
    TCL_CHECK_MSG(rc, "decr must be int!");
  }

  rc = ADLB_Store(id, NULL, ADLB_DATA_TYPE_BLOB, pointer, length, decr);
  TCL_CONDITION(rc == ADLB_SUCCESS, "failed!");

  return TCL_OK;
}

/**
   adlb::store_blob_floats <id> [ list doubles ] [<decr>]
 */
static int
ADLB_Blob_store_floats_Cmd(ClientData cdata, Tcl_Interp *interp,
                           int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 3 || objc == 4, "Expected 2 or 3 args");
  int rc;
  adlb_datum_id id;
  rc = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");

  int length;
  Tcl_Obj** objs;
  rc = Tcl_ListObjGetElements(interp, objv[2], &length, &objs);
  TCL_CHECK_MSG(rc, "requires list!");
  assert(length >= 0);

  TCL_CONDITION((size_t)length*sizeof(double) <= ADLB_DATA_MAX,
                "list too long!");

  for (int i = 0; i < length; i++)
  {
    double v;
    rc = Tcl_GetDoubleFromObj(interp, objs[i], &v);
    memcpy(xfer+(size_t)i*sizeof(double), &v, sizeof(double));
  }

  adlb_refcounts decr = ADLB_WRITE_RC;
  if (objc == 4) {
    rc = Tcl_GetIntFromObj(interp, objv[3], &decr.write_refcount);
    TCL_CHECK_MSG(rc, "decr must be int!");
  
  }
  rc = ADLB_Store(id, NULL, ADLB_DATA_TYPE_BLOB, 
                  xfer, length*(int)sizeof(double), decr);
  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::store <%lli> failed!", id);

  return TCL_OK;
}

/**
   adlb::store_blob_ints <id> [ list ints ] [<decr>]
 */
static int
ADLB_Blob_store_ints_Cmd(ClientData cdata, Tcl_Interp *interp,
                         int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 3 || objc == 4, "Expected 2 or 3 args");
  int rc;
  adlb_datum_id id;
  rc = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");

  int length;
  Tcl_Obj** objs;
  rc = Tcl_ListObjGetElements(interp, objv[2], &length, &objs);
  TCL_CHECK_MSG(rc, "requires list!");

  TCL_CONDITION(length*(int)sizeof(int) <= ADLB_DATA_MAX,
                "list too long!");

  for (int i = 0; i < length; i++)
  {
    int v;
    rc = Tcl_GetIntFromObj(interp, objs[i], &v);
    memcpy(xfer+(size_t)i*sizeof(int), &v, sizeof(int));
  }

  adlb_refcounts decr = ADLB_WRITE_RC;
  if (objc == 4) {
    rc = Tcl_GetIntFromObj(interp, objv[3], &decr.write_refcount);
    TCL_CHECK_MSG(rc, "decr must be int!");
  
  }
  rc = ADLB_Store(id, NULL, ADLB_DATA_TYPE_BLOB,
                  xfer, length*(int)sizeof(int), decr);
  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::store <%lli> failed!", id);

  return TCL_OK;
}

/**
   adlb::blob_from_string <string value>
 */
static int
ADLB_Blob_From_String_Cmd(ClientData cdata, Tcl_Interp *interp,
                           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  int length;
  char *data = Tcl_GetStringFromObj(objv[1], &length);
  assert(length >= 0);

  TCL_CONDITION(data != NULL,
                "adlb::blob_from_string failed!");
  int length2 = length+1;

  void *blob = malloc((size_t)length2 * sizeof(char));
  memcpy(blob, data, (size_t)length2);

  Tcl_Obj* list[2];
  list[0] = Tcl_NewPtr(blob);
  list[1] = Tcl_NewIntObj(length2);
  Tcl_Obj* result = Tcl_NewListObj(2, list);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
  return TCL_OK;
}

/**
   adlb::blob_to_string <blob value>
   Convert null-terminated blob to string
 */
static int
ADLB_Blob_To_String_Cmd(ClientData cdata, Tcl_Interp *interp,
                           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  adlb_blob_t blob;
  adlb_datum_id tmp_id;
  int rc = extract_tcl_blob(interp, objv, objv[1], &blob, &tmp_id);
  TCL_CHECK(rc);

  TCL_CONDITION(((char*)blob.value)[blob.length-1] == '\0', "adlb::blob_to_string "
                "blob must be null terminated");
  Tcl_SetObjResult(interp, Tcl_NewStringObj(blob.value, blob.length-1));
  return TCL_OK;
}

/**
   usage: adlb::insert <id> <subscript> <member> <type> [<extra for type>] 
                       [<write refcount decr>] [<read refcount decr>]
*/
static int
ADLB_Insert_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION((objc >= 4),
                "requires at least 4 args!");

  int rc;
  adlb_datum_id id;
  int argpos = 1;
  rc = Tcl_GetADLB_ID(interp, objv[argpos++], &id);
  TCL_CHECK(rc);
  char* subscript = Tcl_GetString(objv[argpos++]);
 
  Tcl_Obj *member_obj = objv[argpos++];

  adlb_data_type type;
  bool has_extra;
  adlb_type_extra extra;
  rc = type_from_obj_extra(interp, objv, objv[argpos++], &type,
                           &has_extra, &extra);
  TCL_CHECK(rc);

  adlb_binary_data member; 
  rc = tcl_obj_to_adlb_data(interp, objv, type, has_extra ? &extra : NULL,
                            member_obj, &xfer_buf, &member);
  TCL_CHECK_MSG(rc, "adlb::insert <%lli>[%s] failed, could not extract data!",
                    id, subscript);

  DEBUG_ADLB("adlb::insert <%lli>[\"%s\"]=<%s>",
               id, subscript, Tcl_GetStringFromObj(member_obj, NULL));

  adlb_refcounts decr = ADLB_NO_RC;
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.write_refcount);
    TCL_CHECK(rc);
  }

  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.read_refcount);
    TCL_CHECK(rc);
  }

  TCL_CONDITION(argpos == objc, "trailing arguments after %i not consumed",
                                argpos);

  rc = ADLB_Store(id, subscript, type, member.data, member.length, decr);

  // Free if needed
  if (member.data != xfer_buf.data)
    ADLB_Free_binary_data(&member);

  TCL_CONDITION(rc == ADLB_SUCCESS, "failed: <%lli>[\"%s\"]\n", id, subscript);
  return TCL_OK;
}

/**
   usage: adlb::insert_atomic <id> <subscript>
   returns: 0 if the id[subscript] already existed, else 1
*/
static int
ADLB_Insert_Atomic_Cmd(ClientData cdata, Tcl_Interp *interp,
                       int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);

  bool b;
  adlb_datum_id id;
  Tcl_GetADLB_ID(interp, objv[1], &id);
  char* subscript = Tcl_GetString(objv[2]);

  DEBUG_ADLB("adlb::insert_atomic: <%lli>[\"%s\"]",
             id, subscript);
  int rc = ADLB_Insert_atomic(id, subscript, &b, NULL, NULL, NULL);

  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::insert_atomic: failed: <%lli>[%s]",
                id, subscript);

  Tcl_Obj* result = Tcl_NewBooleanObj(b);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

static int
ADLB_Lookup_Impl(Tcl_Interp *interp, int objc, Tcl_Obj *const objv[],
                 bool spin)
{
  TCL_CONDITION(objc >= 3, "adlb::lookup at least 2 arguments!");

  adlb_datum_id id;
  int argpos = 1;
  int rc;
  rc = Tcl_GetADLB_ID(interp, objv[argpos++], &id);
  TCL_CHECK_MSG(rc, "adlb::lookup could not parse given id!");
  char* subscript = Tcl_GetString(objv[argpos++]);

  TCL_CHECK_MSG((subscript == NULL), "adlb::lookup could not parse subscript!");

  adlb_data_type type;
  int len;

  // Optional reference decrement argument, defaults to 0
  adlb_retrieve_rc refcounts = ADLB_RETRIEVE_NO_RC;
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                &refcounts.decr_self.read_refcount);
    TCL_CHECK(rc);
  }
  
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                &refcounts.incr_referand.read_refcount);
    TCL_CHECK(rc);
  }
  
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                &refcounts.decr_self.write_refcount);
    TCL_CHECK(rc);
  }
  
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                &refcounts.incr_referand.write_refcount);
    TCL_CHECK(rc);
  }
  
  TCL_CONDITION(argpos == objc,
          "extra trailing arguments starting at argument %i", argpos);
 
  do {
    rc = ADLB_Retrieve(id, subscript, refcounts, &type, xfer, &len);
    TCL_CONDITION(rc == ADLB_SUCCESS, "lookup failed for: <%lli>[%s]",
                  id, subscript);
  } while (spin && len < 0);
  
  TCL_CONDITION(len >= 0, "adlb::lookup <%lli>[\"%s\"] not found",
                id, subscript);

  Tcl_Obj* result = NULL;
  adlb_data_to_tcl_obj(interp, objv, id, type, NULL, xfer, len, &result);
  DEBUG_ADLB("adlb::lookup <%lli>[\"%s\"]=<%s>",
             id, subscript, Tcl_GetStringFromObj(result, NULL));
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: adlb::lookup <id> <subscript> [<decr readers>] [<decr writers>]
        [<incr readers referand>] [<incr writers referand>]
   decr (readers|writers): decrement reference counts.  Default is zero.
   incr (readers|writers) referand: increment reference counts of referand
   returns the member
*/
static int
ADLB_Lookup_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
    return ADLB_Lookup_Impl(interp, objc, objv, false);
}

/**
   usage: adlb::lookup_spin <id> <subscript> [<decr readers>] [<decr writers>]
        [<incr readers referand>] [<incr writers referand>]
   decr (readers|writers): decrement reference counts.  Default is zero.
   incr (readers|writers) referand: increment reference counts of referand
   returns the member
*/
static int
ADLB_Lookup_Spin_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
    return ADLB_Lookup_Impl(interp, objc, objv, true);
}

/**
   usage: adlb::lock <id> => false (try again) or
                             true (locked by caller)
*/
static int
ADLB_Lock_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int rc;
  adlb_datum_id id;
  rc = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "argument must be a long integer!");

  bool locked;
  rc = ADLB_Lock(id, &locked);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%lli> failed!", id);

  Tcl_Obj* result = Tcl_NewBooleanObj(locked);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: adlb::unlock <id>
*/
static int
ADLB_Unlock_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int rc;
  adlb_datum_id id;
  rc = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "argument must be a long integer!");

  rc = ADLB_Unlock(id);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%lli> failed!", id);

  return TCL_OK;
}

/**
   usage: adlb::unique => id
*/
static int
ADLB_Unique_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);

  adlb_datum_id id;
  int rc = ADLB_Unique(&id);
  ASSERT(rc == ADLB_SUCCESS);

  // DEBUG_ADLB("adlb::unique: <%lli>", id);

  Tcl_Obj* result = Tcl_NewADLB_ID(id);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: adlb::container_typeof <id>
*/
static int
ADLB_Typeof_Cmd(ClientData cdata, Tcl_Interp *interp,
		int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  adlb_datum_id id;
  Tcl_GetADLB_ID(interp, objv[1], &id);

  adlb_data_type type;
  int rc = ADLB_Typeof(id, &type);
  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::container_typeof <%lli> failed!", id);

  // DEBUG_ADLB("adlb::container_typeof: <%lli> is: %i\n", id, type);

  const char *type_string = ADLB_Data_type_tostring(type);

  // DEBUG_ADLB("adlb::container_typeof: <%lli> is: %s",
  //            id, type_string);

  Tcl_Obj* result = Tcl_NewStringObj(type_string, -1);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

/**
   usage: adlb::container_typeof <id>
*/
static int
ADLB_Container_Typeof_Cmd(ClientData cdata, Tcl_Interp *interp,
                          int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  adlb_datum_id id;
  Tcl_GetADLB_ID(interp, objv[1], &id);

  adlb_data_type key_type, val_type;
  int rc = ADLB_Container_typeof(id, &key_type, &val_type);
  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::container_typeof <%lli> failed!", id);

  const char *key_type_string = ADLB_Data_type_tostring(val_type);
  const char *val_type_string = ADLB_Data_type_tostring(key_type);

  Tcl_Obj* types[2] = {Tcl_NewStringObj(key_type_string, -1),
                       Tcl_NewStringObj(val_type_string, -1)};
  
  Tcl_Obj* result = Tcl_NewListObj(2, types);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

/**
   usage: adlb::container_reference
      <container_id> <subscript> <reference> <reference_type>

      reference_type is type used internally to represent
      the reference e.g. integer for plain turbine IDs, or
      string if represented as a more complex datatype
*/
static int
ADLB_Container_Reference_Cmd(ClientData cdata, Tcl_Interp *interp,
                             int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(5);

  adlb_datum_id container_id;
  int rc;
  rc = Tcl_GetADLB_ID(interp, objv[1], &container_id);
  TCL_CHECK_MSG(rc, "adlb::container_reference: "
                "argument 1 is not a 64-bit integer!");
  char* subscript = Tcl_GetString(objv[2]);
  adlb_datum_id reference;
  rc = Tcl_GetADLB_ID(interp, objv[3], &reference);
  TCL_CHECK_MSG(rc, "adlb::container_reference: "
                "argument 3 is not a 64-bit integer!");

  adlb_data_type ref_type;
  bool has_extra;
  adlb_type_extra extra;
  // ignores extra type info
  rc = type_from_obj_extra(interp, objv, objv[4], &ref_type,
                           &has_extra, &extra);
  TCL_CHECK(rc);

  // DEBUG_ADLB("adlb::container_reference: <%lli>[%s] => <%lli>\n",
  //            container_id, subscript, reference);
  rc = ADLB_Container_reference(container_id, subscript, reference,
                                ref_type);
  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::container_reference: <%lli> failed!",
                container_id);
  return TCL_OK;
}

/**
   usage: adlb::container_size <container_id> [ <read decr> ] [ <write decr> ]
*/
static int
ADLB_Container_Size_Cmd(ClientData cdata, Tcl_Interp *interp,
                             int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 2, "must have at least 2 arguments");

  int argpos = 1;
  adlb_datum_id container_id;
  int rc;
  rc = Tcl_GetADLB_ID(interp, objv[argpos++], &container_id);
  TCL_CHECK_MSG(rc, "argument is not a valid ID!");

  adlb_refcounts decr = ADLB_NO_RC;
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.read_refcount);
    TCL_CHECK_MSG(rc, "Expected integer argument");
  }
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.write_refcount);
    TCL_CHECK_MSG(rc, "Expected integer argument");
  }
  
  TCL_CONDITION(argpos == objc, "unexpected trailing args at %ith arg", argpos);

  int size;
  // DEBUG_ADLB("adlb::container_size: <%lli>",
  //            container_id, size);
  rc = ADLB_Container_size(container_id, &size, decr);
  TCL_CONDITION(rc == ADLB_SUCCESS,
                "adlb::container_size: <%lli> failed!",
                container_id);
  Tcl_Obj* result = Tcl_NewIntObj(size);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: adlb::write_refcount_incr <id> [ increment ]
*/
static int
ADLB_Write_Refcount_Incr_Cmd(ClientData cdata, Tcl_Interp *interp,
                     int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION((objc == 2 || objc == 3),
                "requires 1 or 2 args!");

  adlb_datum_id container_id;
  Tcl_GetADLB_ID(interp, objv[1], &container_id);

  adlb_refcounts incr = ADLB_WRITE_RC;
  if (objc == 3)
    Tcl_GetIntFromObj(interp, objv[2], &incr.write_refcount);

  // DEBUG_ADLB("adlb::write_refcount_incr: <%lli>", container_id);
  int rc = ADLB_Refcount_incr(container_id, incr);

  if (rc != ADLB_SUCCESS)
    return TCL_ERROR;
  return TCL_OK;
}

/**
   usage: adlb::write_refcount_decr <id> <decrement>
*/
static int
ADLB_Write_Refcount_Decr_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION((objc == 2 || objc == 3),
                "requires 1 or 2 args!");

  adlb_datum_id container_id;
  Tcl_GetADLB_ID(interp, objv[1], &container_id);

  int decr_w = 1;
  if (objc == 3)
    Tcl_GetIntFromObj(interp, objv[2], &decr_w);

  // DEBUG_ADLB("adlb::write_refcount_decr: <%lli>", container_id);
  adlb_refcounts decr = { .read_refcount = 0, .write_refcount = -decr_w };
  int rc = ADLB_Refcount_incr(container_id, decr);

  if (rc != ADLB_SUCCESS)
    return TCL_ERROR;
  return TCL_OK;
}

/*
  Implement multiple reference count commands.
  amount: if null, assume 1
  bool: negate the reference count
 */
static int
ADLB_Refcount_Incr_Impl(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[],
                   adlb_refcount_type type,
                   Tcl_Obj *var, Tcl_Obj *amount,
                   bool negate)
{
  int rc;

  adlb_datum_id id;
  rc = Tcl_GetADLB_ID(interp, var, &id);
  TCL_CHECK(rc);

  int change = 1; // Default
  if (amount != NULL)
  {
    rc = Tcl_GetIntFromObj(interp, amount, &change);
    TCL_CHECK(rc);
  }

  if (negate)
  {
    change = -change;
  }
 
 // DEBUG_ADLB("adlb::refcount_incr: <%lli>", id);
  
  adlb_refcounts incr = ADLB_NO_RC;
  if (type == ADLB_READ_REFCOUNT || type == ADLB_READWRITE_REFCOUNT)
  {
    incr.read_refcount = change;
  }
  if (type == ADLB_WRITE_REFCOUNT || type == ADLB_READWRITE_REFCOUNT)
  {
    incr.write_refcount = change;
  }
  rc = ADLB_Refcount_incr(id, incr);

  if (rc != ADLB_SUCCESS)
    return TCL_ERROR;
  return TCL_OK;
}

static int refcount_mode(Tcl_Interp *interp, Tcl_Obj *const objv[],
                          Tcl_Obj* obj, adlb_refcount_type *mode)
{
  const char *mode_string = Tcl_GetString(obj);
  TCL_CONDITION(mode_string != NULL, "invalid refcountmode argument");
  if (strcmp(mode_string, "r") == 0)
  {
    *mode = ADLB_READ_REFCOUNT;
    return TCL_OK;
  }
  else if (strcmp(mode_string, "w") == 0)
  {
    *mode = ADLB_WRITE_REFCOUNT;
    return TCL_OK;
  }
  else if (strcmp(mode_string, "rw") == 0)
  {
    *mode = ADLB_READWRITE_REFCOUNT;
    return TCL_OK;
  }
  else
  {
    char err[strlen(mode_string) + 20];
    sprintf(err, "unknown refcount mode %s!", mode_string);
    Tcl_AddErrorInfo(interp, err);
    return TCL_ERROR;
  }
}

/**
   usage: adlb::refcount_incr <container_id> <refcount_type> <change>
   refcount_type in { r, w, rw }
*/
static int
ADLB_Refcount_Incr_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION((objc == 4), "requires 4 args!");

  adlb_refcount_type mode;
  int rc = refcount_mode(interp, objv, objv[2], &mode);
  TCL_CHECK(rc);
  return ADLB_Refcount_Incr_Impl(cdata, interp, objc, objv, mode,
                          objv[1], objv[3], false);
}

static int
ADLB_Read_Refcount_Incr_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION((objc == 2 || objc == 3), "requires 2-3 args!");
  Tcl_Obj *amount = (objc == 3) ? objv[2] : NULL;

  return ADLB_Refcount_Incr_Impl(cdata, interp, objc, objv,
              ADLB_READ_REFCOUNT, objv[1], amount, false);
}

static int
ADLB_Read_Refcount_Decr_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION((objc == 2 || objc == 3), "requires 2-3 args!");
  Tcl_Obj *amount = (objc == 3) ? objv[2] : NULL;

  return ADLB_Refcount_Incr_Impl(cdata, interp, objc, objv,
              ADLB_READ_REFCOUNT, objv[1], amount, true);
}


/**
   usage: adlb::read_refcount_enable
   If not set, all read reference count operations are ignored
 **/
static int
ADLB_Enable_Read_Refcount_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  adlb_code rc = ADLB_Read_refcount_enable();
  TCL_CONDITION(rc == ADLB_SUCCESS, "Unexpected failure");
  return TCL_OK;
}

/**
   usage: adlb::fail
 */
static int
ADLB_Fail_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  ADLB_Fail(1);
  return TCL_OK;
}

/**
   usage: adlb::abort
 */
static int
ADLB_Abort_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  ADLB_Abort(1);
  return TCL_OK;
}


/**
   usage: adlb::finalize <b>
   If b, finalize MPI
 */
static int
ADLB_Finalize_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  int rc = ADLB_Finalize();
  if (rc != ADLB_SUCCESS)
    printf("WARNING: ADLB_Finalize() failed!\n");
  TCL_ARGS(2);
  int b;
  Tcl_GetBooleanFromObj(interp, objv[1], &b);
  if (b)
    MPI_Finalize();
  turbine_debug_finalize();

  rc = struct_format_finalize();
  TCL_CHECK(rc);

  return TCL_OK;
}

/**
   Shorten object creation lines.  "adlb::" namespace is prepended
 */
#define ADLB_NAMESPACE "adlb::"
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, ADLB_NAMESPACE tcl_function, c_function, \
                         NULL, NULL);

/**
   Called when Tcl loads this extension
 */
int DLLEXPORT
Tcladlb_Init(Tcl_Interp* interp)
{
  if (Tcl_InitStubs(interp, TCL_VERSION, 0) == NULL)
    return TCL_ERROR;

  if (Tcl_PkgProvide(interp, "ADLB", "0.1") == TCL_ERROR)
    return TCL_ERROR;

  tcl_adlb_init(interp);

  return TCL_OK;
}

void
tcl_adlb_init(Tcl_Interp* interp)
{
  COMMAND("init",      ADLB_Init_Cmd);
  COMMAND("declare_struct_type", ADLB_Declare_Struct_Type_Cmd);
  COMMAND("server",    ADLB_Server_Cmd);
  COMMAND("rank",      ADLB_Rank_Cmd);
  COMMAND("amserver",  ADLB_AmServer_Cmd);
  COMMAND("size",      ADLB_Size_Cmd);
  COMMAND("servers",   ADLB_Servers_Cmd);
  COMMAND("workers",   ADLB_Workers_Cmd);
  COMMAND("barrier",   ADLB_Barrier_Cmd);
  COMMAND("hostmap",   ADLB_Hostmap_Cmd);
  COMMAND("get_priority",   ADLB_Get_Priority_Cmd);
  COMMAND("reset_priority", ADLB_Reset_Priority_Cmd);
  COMMAND("set_priority",   ADLB_Set_Priority_Cmd);
  COMMAND("put",       ADLB_Put_Cmd);
  COMMAND("spawn",     ADLB_Spawn_Cmd);
  COMMAND("get",       ADLB_Get_Cmd);
  COMMAND("iget",      ADLB_Iget_Cmd);
  COMMAND("create",    ADLB_Create_Cmd);
  COMMAND("multicreate",ADLB_Multicreate_Cmd);
  COMMAND("exists",    ADLB_Exists_Cmd);
  COMMAND("exists_sub", ADLB_Exists_Sub_Cmd);
  COMMAND("store",     ADLB_Store_Cmd);
  COMMAND("retrieve",  ADLB_Retrieve_Cmd);
  COMMAND("retrieve_decr",  ADLB_Retrieve_Decr_Cmd);
  COMMAND("acquire_ref",  ADLB_Acquire_Ref_Cmd);
  COMMAND("acquire_sub_ref",  ADLB_Acquire_Sub_Ref_Cmd);
  COMMAND("enumerate", ADLB_Enumerate_Cmd);
  COMMAND("retrieve_blob", ADLB_Retrieve_Blob_Cmd);
  COMMAND("retrieve_decr_blob", ADLB_Retrieve_Blob_Decr_Cmd);
  COMMAND("blob_free",  ADLB_Blob_Free_Cmd);
  COMMAND("local_blob_free",  ADLB_Local_Blob_Free_Cmd);
  COMMAND("store_blob", ADLB_Store_Blob_Cmd);
  COMMAND("store_blob_floats", ADLB_Blob_store_floats_Cmd);
  COMMAND("store_blob_ints", ADLB_Blob_store_ints_Cmd);
  COMMAND("blob_from_string", ADLB_Blob_From_String_Cmd);
  COMMAND("blob_to_string", ADLB_Blob_To_String_Cmd);
  COMMAND("enable_read_refcount",  ADLB_Enable_Read_Refcount_Cmd);
  COMMAND("refcount_incr", ADLB_Refcount_Incr_Cmd);
  COMMAND("read_refcount_incr", ADLB_Read_Refcount_Incr_Cmd);
  COMMAND("read_refcount_decr", ADLB_Read_Refcount_Decr_Cmd);
  COMMAND("write_refcount_incr", ADLB_Write_Refcount_Incr_Cmd);
  COMMAND("write_refcount_decr", ADLB_Write_Refcount_Decr_Cmd);
  COMMAND("insert",    ADLB_Insert_Cmd);
  COMMAND("insert_atomic", ADLB_Insert_Atomic_Cmd);
  COMMAND("lookup",    ADLB_Lookup_Cmd);
  COMMAND("lookup_spin", ADLB_Lookup_Spin_Cmd);
  COMMAND("lock",      ADLB_Lock_Cmd);
  COMMAND("unlock",    ADLB_Unlock_Cmd);
  COMMAND("unique",    ADLB_Unique_Cmd);
  COMMAND("typeof",    ADLB_Typeof_Cmd);
  COMMAND("container_typeof",    ADLB_Container_Typeof_Cmd);
  COMMAND("container_reference", ADLB_Container_Reference_Cmd);
  COMMAND("container_size",      ADLB_Container_Size_Cmd);
  COMMAND("fail",      ADLB_Fail_Cmd);
  COMMAND("abort",     ADLB_Abort_Cmd);
  COMMAND("finalize",  ADLB_Finalize_Cmd);

  // Export all commands
  Tcl_Namespace *ns = Tcl_FindNamespace(interp,
          ADLB_NAMESPACE, NULL, TCL_GLOBAL_ONLY);
  Tcl_Export(interp, ns, "*", true);
}
