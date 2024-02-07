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

// This contains some very big macros for error handling
// but the use of macros allows us to provide nice Tcl error results
// and C file and line numbers.

#include "config.h"
#include <assert.h>

// strnlen() is a GNU extension: Need _GNU_SOURCE
#define _GNU_SOURCE
#if ENABLE_BGP == 1
// Also need __USE_GNU on the BG/P and on older GCC (4.1, 4.3)
#define __USE_GNU
#endif
#include <string.h>
#include <exm-memory.h>
#include <exm-string.h>

#include <limits.h>
#include <inttypes.h>
#include <stdbool.h>
#include <stdint.h>

#include <tcl.h>
#include <mpi.h>
#include <adlb.h>
#include <adlb-defs.h>
#include <adlb_types.h>
#ifdef ENABLE_XPT
#include <adlb-xpt.h>
#endif

#include <log.h>

#include <table_bp.h>
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

/** If ADLB communicator initialized */
static bool adlb_comm_init = false;

/** If ADLB fully initialized */
static bool adlb_init = false;

/** The communicator to use in our ADLB instance */
MPI_Comm adlb_comm = MPI_COMM_NULL;

/** Size of adlb_comm */
int adlb_comm_size = -1;

/** The rank of this process in adlb_comm */
int adlb_comm_rank = -1;

/** Number of workers */
static int adlb_workers = -1;

/** Number of servers */
static int adlb_servers = -1;

static int am_server;

#ifdef USE_ADLB
static int am_debug_server;
#endif

/** Communicator for ADLB workers */
static MPI_Comm adlb_worker_comm = MPI_COMM_NULL;

/** Rank in adlb_comm */
static int adlb_worker_comm_rank = -1;

/**
 Large buffer for receiving ADLB payloads, etc.
 */
static char xfer[ADLB_PAYLOAD_MAX];
static const adlb_buffer xfer_buf = {
  .data = xfer, .length = ADLB_PAYLOAD_MAX
};

/**
 Smaller scratch buffer for subscripts, etc.
 */
#define TCL_ADLB_SCRATCH_LEN ADLB_DATA_SUBSCRIPT_MAX
static char tcl_adlb_scratch[TCL_ADLB_SCRATCH_LEN];
static const adlb_buffer tcl_adlb_scratch_buf = {
  .data = tcl_adlb_scratch, .length = TCL_ADLB_SCRATCH_LEN
};

/**
 Free any buffer that isn't tcl_adlb_scratch in data
 */
static void free_non_scratch(adlb_buffer buf)
{
  if (buf.data != NULL &&
      buf.data != tcl_adlb_scratch)
  {
    // Must have been malloced
    free(buf.data);
  }
}

/* Return a pointer to a shared transfer buffer */
char *tcl_adlb_xfer_buffer(uint64_t *buf_size) {
  *buf_size = ADLB_PAYLOAD_MAX;
  return xfer;
}

/**
   Map from binary packed [TD,subscript] to local blob pointers.
   This is not an LRU cache: the user must use blob_free to
   free memory
 */
static table_bp blob_cache;

/**
   Cache Tcl_Objs for struct field names
 */
static struct
{
  Tcl_Obj*** objs;
  int size;
} field_name_objs;

/**
   Represent full type of a data structure
*/
typedef struct
{
  int len;
  adlb_data_type *types; /* E.g. container and nested types */
  adlb_type_extra *extras; /* E.g. for struct subtype */
} compound_type;

static int adlb_setup_comm(Tcl_Interp *interp, Tcl_Obj *const objv[],
                           MPI_Comm *comm);
static void set_namespace_constants(Tcl_Interp* interp);

static int refcount_mode(Tcl_Interp *interp, Tcl_Obj *const objv[],
                          Tcl_Obj* obj, adlb_refcount_type *mode);

static int blob_cache_key(Tcl_Interp *interp, Tcl_Obj *const objv[],
                          adlb_datum_id *id, adlb_subscript *sub,
                          void **key, size_t *key_len, bool *alloced);

static Tcl_Obj *build_tcl_blob(void *data, size_t length, Tcl_Obj *handle);

static int extract_tcl_blob(Tcl_Interp *interp, Tcl_Obj *const objv[],
                   Tcl_Obj *obj, adlb_blob_t *blob, Tcl_Obj **handle);

static int cache_blob(Tcl_Interp *interp, int objc,
    Tcl_Obj *const objv[], adlb_datum_id id, adlb_subscript sub,
    void *blob);

static int uncache_blob(Tcl_Interp *interp, int objc,
    Tcl_Obj *const objv[], adlb_datum_id id, adlb_subscript sub,
    bool *found_in_cache);

static int blob_cache_finalize(void);

static int
packed_struct_to_tcl_dict(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         const void *data, size_t length,
                         adlb_type_extra extra, Tcl_Obj **result);
static int
tcl_dict_to_adlb_struct(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj *dict, adlb_struct_type struct_type,
                         adlb_struct **result);

static int
packed_multiset_to_list(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         const void *data, size_t length,
                         adlb_type_extra extra, Tcl_Obj **result);

static int
tcl_list_to_packed_multiset(Tcl_Interp *interp, Tcl_Obj *const objv[],
        const compound_type types, int ctype_pos, Tcl_Obj *list,
        bool canonicalize, adlb_buffer *output, bool *output_caller_buf,
        size_t *output_pos);

static int
packed_container_to_dict(Tcl_Interp *interp, Tcl_Obj *const objv[],
       const void *data, size_t length,
       adlb_type_extra extra, Tcl_Obj **result);

static int
tcl_dict_to_packed_container(Tcl_Interp *interp, Tcl_Obj *const objv[],
        const compound_type types, int ctype_pos, Tcl_Obj *dict,
        bool canonicalize, adlb_buffer *output, bool *output_caller_buf,
        size_t *output_pos);

static int
parse_variable_spec_list(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj *list, ADLB_create_spec *spec);

static int
get_compound_type(Tcl_Interp *interp, int objc, Tcl_Obj *const objv[],
                int *argpos, compound_type *types);

static void
free_compound_type(compound_type *types);

static inline int
compound_type_next(Tcl_Interp *interp, Tcl_Obj *const objv[],
      const compound_type types, int *ctype_pos,
      adlb_data_type *type, adlb_type_extra *extra);

static int
adlb_tclobj2bin_compound(Tcl_Interp *interp, Tcl_Obj *const objv[],
                const compound_type types,
                Tcl_Obj *obj, bool canonicalize,
                const adlb_buffer *caller_buffer,
                adlb_binary_data* result);

static int
adlb_tclobj_bin_append(Tcl_Interp *interp, Tcl_Obj *const objv[],
        const compound_type types, int ctype_pos,
        Tcl_Obj *obj, bool prefix_len, bool canonicalize,
        adlb_buffer *output, bool *output_caller_buf,
        size_t *output_pos);

static int
adlb_tclobj_bin_append2(Tcl_Interp *interp, Tcl_Obj *const objv[],
        adlb_data_type type, adlb_type_extra extra,
        Tcl_Obj *obj, bool prefix_len, bool canonicalize,
        adlb_buffer *output, bool *output_caller_buf,
        size_t *output_pos);

static int ADLB_Parse_Struct_Subscript(Tcl_Interp *interp,
  Tcl_Obj *const objv[],
  const char *str, size_t length,
  adlb_buffer *buf, adlb_subscript *sub,
  bool *using_caller_buf, bool append);

#define PARSE_STRUCT_SUB(str, len, buf, sub, using_caller_buf, append) \
    ADLB_Parse_Struct_Subscript(interp, objv, str, len, buf, sub, \
                                using_caller_buf, append)

static int append_subscript(Tcl_Interp *interp,
      Tcl_Obj *const objv[], adlb_subscript *sub,
      adlb_subscript to_append, adlb_buffer *buf);

static int field_name_objs_init(Tcl_Interp *interp, Tcl_Obj *const objv[]);
static int field_name_objs_add(Tcl_Interp *interp, Tcl_Obj *const objv[],
      adlb_struct_type type, int field_count,
      const char *const* field_names);
static int field_name_objs_finalize(Tcl_Interp *interp,
                                    Tcl_Obj *const objv[]);

#define DEFAULT_PRIORITY 0

/* current priority for rule */
int ADLB_curr_priority = DEFAULT_PRIORITY;

/** We only free this if we are the outermost MPI communicator */
static bool must_comm_free = false;

static int
CHECK_ADLB_STORE(Tcl_Interp* interp, Tcl_Obj* const objv[],
                 int rc, int64_t id, adlb_subscript sub)
{
  if (adlb_has_sub(sub))
  {
    TCL_CONDITION(rc != ADLB_REJECTED,
                  "<%"PRId64">[\"%s\"] failed: double assign!",
                  id, (const char*) sub.key);
    TCL_CONDITION(rc == ADLB_SUCCESS,
                  "<%"PRId64">[\"%s\"] failed!",
                  id, (const char*) sub.key);
  }
  else
  {
    TCL_CONDITION(rc != ADLB_REJECTED,
                  "<%"PRId64">, double assign!", id);
    TCL_CONDITION(rc == ADLB_SUCCESS, "<%"PRId64"> failed", id);
  }
  return TCL_OK;
}

#define CHECK_ADLB_RETRIEVE(rc, handle) {                  \
  if (adlb_has_sub((handle).sub.val)) {                    \
    if ((rc) == ADLB_NOTHING)                              \
      TCL_RETURN_ERROR("<%"PRId64">[%.*s] not found!",     \
                       (handle).id,                        \
                       (int)(handle).sub.val.length,       \
                       (const char*)(handle).sub.val.key); \
    TCL_CONDITION((rc) == ADLB_SUCCESS,                    \
                  "<%"PRId64">[%.*s] failed!",             \
                  (handle).id,                             \
                  (int)(handle).sub.val.length,            \
                  (const char*)(handle).sub.val.key);      \
  } else {                                                 \
    if ((rc) == ADLB_NOTHING)                              \
      TCL_RETURN_ERROR("<%"PRId64"> not found!",           \
                       (handle).id);                       \
    TCL_CONDITION((rc) == ADLB_SUCCESS,                    \
                  "<%"PRId64"> failed!", (handle).id);     \
  }                                                        \
}

static int
ADLB_Retrieve_Impl(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj* const objv[], bool decr);

static int
ADLB_Acquire_Ref_Impl(ClientData cdata, Tcl_Interp* interp,
          int objc, Tcl_Obj* const objv[], bool write_ref,
          adlb_subscript_kind sub_kind);

/**
   usage: adlb::init_comm [<comm>]?

   Setup ADLB communicator and MPI if needed, but no other parts of ADLB.

   If comm is given, run ADLB in that communicator
   Else, run ADLB in a dup of MPI_COMM_WORLD

   After this is run, adlb::comm_size and adlb::comm_rank can be used.
 */
static int
ADLB_Init_Comm_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 1 || objc == 2, "requires 0 or 1 arguments!");
  int rc;

  MPI_Comm *adlb_comm_ptr = NULL;
  if (objc == 2)
  {
    long tmp_ptr = 0;
    rc = Tcl_GetLongFromObj(interp, objv[1], &tmp_ptr);
    TCL_CHECK(rc);
    adlb_comm_ptr = (MPI_Comm*)tmp_ptr;
  }

  rc = adlb_setup_comm(interp, objv, adlb_comm_ptr);
  TCL_CHECK(rc);

  return TCL_OK;
}

static int do_mpi_init(Tcl_Interp* interp, Tcl_Obj* const objv[]);

/*
 * Setup the ADLB communicator
 *
 * comm: if NULL, use MPI_COMM_WORLD
 * @return A Tcl error code
 */
static int adlb_setup_comm(Tcl_Interp* interp, Tcl_Obj* const objv[],
                           MPI_Comm* comm)
{
  TCL_CONDITION(!adlb_comm_init, "ADLB Communicator already initialized");

  if (comm == NULL)
  {
    must_comm_free = true;
    int rc = do_mpi_init(interp, objv);
    TCL_CHECK_MSG(rc, "Failed to set up MPI!");
    MPI_Comm_dup(MPI_COMM_WORLD, &adlb_comm);
  }
  else
  {
    adlb_comm = *comm;
  }

  MPI_Comm_size(adlb_comm, &adlb_comm_size);
  MPI_Comm_rank(adlb_comm, &adlb_comm_rank);

  adlb_comm_init = true;

  return TCL_OK;
}

/**
 Initialize MPI
 @arg interp: Just for errors
 @return A Tcl error code
*/
static int
do_mpi_init(Tcl_Interp* interp, Tcl_Obj* const objv[])
{
  // Start with MPI_Init() and MPI_COMM_WORLD
  int argc = 0;
  char** argv = NULL;

  int rc;
  int use_thread_multiple;
  rc = getenv_integer("TURBINE_MPI_THREAD", 0, &use_thread_multiple);
  TCL_CONDITION(rc, "invalid setting for TURBINE_MPI_THREAD: %s",
                getenv("TURBINE_MPI_THREAD"));

  if (use_thread_multiple)
  {
    int provided;
    rc = MPI_Init_thread(&argc, &argv, MPI_THREAD_MULTIPLE, &provided);
    TCL_CONDITION((provided == MPI_THREAD_MULTIPLE),
                  "MPI_THREAD_MULTIPLE is not supported by this MPI!");
  }
  else
    rc = MPI_Init(&argc, &argv);
  assert(rc == MPI_SUCCESS);
  return TCL_OK;
}

/**
   usage: adlb::init <servers> <types> [<comm>]?
   Simplified use of ADLB_Init type_vect: just give adlb_init
   a number ntypes, and the valid types will be: [0..ntypes-1]

   If adlb::init_comm was run, use that communicator, otherwise
   set up using the optional communicator provided.
 */
static int
ADLB_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 3 || objc == 4, "requires 2 or 3 arguments!");
  TCL_CONDITION(!adlb_init, "ADLB already initialized");

  mm_init();

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

  bool ok = table_bp_init(&blob_cache, 16);
  TCL_CONDITION(ok, "Could not initialize blob cache");

  rc = field_name_objs_init(interp, objv);
  TCL_CHECK(rc);

  MPI_Comm *adlb_comm_ptr = NULL;
  if (objc == 4)
  {
    long tmp_comm_ptr = 0;
    rc = Tcl_GetLongFromObj(interp, objv[3], &tmp_comm_ptr);
    TCL_CHECK(rc);
    adlb_comm_ptr = (MPI_Comm*) tmp_comm_ptr;
  }

  if (!adlb_comm_init)
  {
    rc = adlb_setup_comm(interp, objv, adlb_comm_ptr);
    TCL_CHECK(rc);
  }

  int workers = adlb_comm_size - servers;
  if (adlb_comm_rank == 0)
  {
    if (workers <= 0)
      puts("WARNING: No workers");
    // Other configuration information will go here...
  }

  // ADLB_Init(int num_servers, int use_debug_server,
  //           int aprintf_flag, int num_types, int *types,
  //           int *am_server, int *am_debug_server, MPI_Comm *app_comm)

  rc = ADLB_Init(servers, ntypes, type_vect,
                 &am_server, adlb_comm, &adlb_worker_comm);
  if (rc != ADLB_SUCCESS)
    return TCL_ERROR;

  if (! am_server)
    MPI_Comm_rank(adlb_worker_comm, &adlb_worker_comm_rank);

  // Set static variables
  adlb_workers = workers;
  adlb_servers = servers;

  set_namespace_constants(interp);

  adlb_init = true;
  Tcl_SetObjResult(interp, Tcl_NewIntObj(ADLB_SUCCESS));
  return TCL_OK;
}

/**
   usage: adlb::declare_struct_type <type id> <type name> <field list>
      where field list is a list of (<field name> <field type>)*
      field type should be the full type of the field, i.e. what you
      would pass to adlb::create.
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
  int max_field_count = field_list_len / 2;
  adlb_struct_field_type field_types[max_field_count];
  const char *field_names[max_field_count];

  int field_count = 0;
  int field_list_ix = 0;
  while (field_list_ix < field_list_len)
  {
    field_names[field_count] = Tcl_GetString(field_list[field_list_ix++]);

    TCL_CONDITION(field_list_ix < field_list_len, "missing type for "
                  "field named %s", field_names[field_count]);
    rc = adlb_type_from_array(interp, objv, field_list, field_list_len,
                         &field_list_ix, &field_types[field_count].type,
                         &field_types[field_count].extra);
    TCL_CHECK(rc);
    field_count++;
  }

  adlb_data_code dc = ADLB_Declare_struct_type(type_id, type_name,
                      field_count, field_types, field_names);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
                "Declaring ADLB struct type failed");


  rc = field_name_objs_add(interp, objv, type_id, field_count,
                           field_names);
  TCL_CHECK(rc);
  return TCL_OK;
}

/**
   usage: adlb::is_struct_type <type name>
    Returns 1 if type name is a valid ADLB struct type or
           0 if another valid ADLB type
    Raises error if type name is invalid.
 */
static int
ADLB_Is_Struct_Type_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  int rc;

  adlb_data_type type;
  adlb_type_extra extra;
  rc = adlb_type_from_obj_extra(interp, objv, objv[1], &type, &extra);
  TCL_CHECK(rc);

  int result = (type == ADLB_DATA_TYPE_STRUCT) ? 1 : 0;

  Tcl_SetObjResult(interp, Tcl_NewIntObj(result));
  return TCL_OK;
}

static int field_name_objs_init(Tcl_Interp *interp, Tcl_Obj *const objv[])
{
  field_name_objs.size = 64;
  field_name_objs.objs = malloc((size_t)field_name_objs.size *
                            sizeof(field_name_objs.objs[0]));
  TCL_CONDITION(field_name_objs.objs != NULL,
                "error allocating field names");

  // Init. entries
  for (int i = 0; i < field_name_objs.size; i++)
  {
    field_name_objs.objs[i] = NULL;
  }
  return TCL_OK;
}

static int field_name_objs_add(Tcl_Interp *interp, Tcl_Obj *const objv[],
    adlb_struct_type type, int field_count,
    const char *const* field_names)
{
  if (field_name_objs.size <= type)
  {
    int new_size = field_name_objs.size * 2;
    if (new_size <= type)
    {
      new_size = type;
    }
    Tcl_Obj ***tmp = realloc(field_name_objs.objs, (size_t)new_size *
                                    sizeof(field_name_objs.objs[0]));
    TCL_MALLOC_CHECK(tmp);
    // Initialize to NULL
    for (int i = field_name_objs.size; i < new_size; i++)
    {
      tmp[i] = NULL;
    }
    field_name_objs.objs = tmp;
    field_name_objs.size = new_size;
  }

  field_name_objs.objs[type] = malloc(
        sizeof(field_name_objs.objs[0][0]) * (size_t)field_count);
  TCL_MALLOC_CHECK(field_name_objs.objs[type]);

  for (int i = 0; i < field_count; i++)
  {
    Tcl_Obj *name_obj = Tcl_NewStringObj(field_names[i], -1);
    TCL_MALLOC_CHECK(name_obj);
    field_name_objs.objs[type][i] = name_obj;
    Tcl_IncrRefCount(name_obj);
  }
  return TCL_OK;
}

/**
 * Free memory used to keep field object names around.
 * Must be called before ADLB_Finalize
 */
static int field_name_objs_finalize(Tcl_Interp *interp,
                                     Tcl_Obj *const objv[])
{
  if (field_name_objs.objs != NULL)
  {
    for (int i = 0; i < field_name_objs.size; i++)
    {
      Tcl_Obj **name_arr = field_name_objs.objs[i];
      if (name_arr != NULL)
      {
        int field_count;
        adlb_data_code dc = ADLB_Lookup_struct_type(i, NULL,
                                  &field_count, NULL, NULL);
        TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
                      "Error looking up struct type %i", i);
        for (int j = 0; j < field_count; j++)
        {
          Tcl_DecrRefCount(name_arr[j]);
        }
        free(name_arr);
      }
    }

    free(field_name_objs.objs);
  }
  field_name_objs.objs = NULL;
  field_name_objs.size = 0;
  return TCL_OK;
}

static void
set_namespace_constants(Tcl_Interp* interp)
{
  turbine_tcl_set_integer(interp, "::adlb::SUCCESS",   ADLB_SUCCESS);
  turbine_tcl_set_integer(interp, "::adlb::RANK_ANY",  ADLB_RANK_ANY);
  turbine_tcl_set_integer(interp, "::adlb::RANK_NULL", ADLB_RANK_NULL);
  turbine_tcl_set_long(interp,    "::adlb::NULL_ID",   ADLB_DATA_ID_NULL);
}

/**
   Enter server
 */
static int
ADLB_Server_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(adlb_init, "ADLB not initialized");
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

static int
ADLB_Barrier_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  int rc;
  Tcl_WideInt comm_int;
  Tcl_Obj* comm_int_obj = objv[1];
  rc = Tcl_GetWideIntFromObj(interp, comm_int_obj, &comm_int);
  TCL_CHECK_MSG(rc, "barrier: not an integer: '%s'",
                Tcl_GetString(comm_int_obj));
  MPI_Comm comm = (MPI_Comm) comm_int;

  rc = MPI_Barrier(comm);
  ASSERT(rc == MPI_SUCCESS);
  return TCL_OK;
}

/*
 * adlb::worker_barrier
 * Barrier for all workers.
 * Should only be called by workers (i.e. non-adlb servers) and
 * must be called by all workers.
 */
static int
ADLB_Worker_Barrier_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);
  int rc;

  rc = MPI_Barrier(ADLB_GetComm_workers());
  ASSERT(rc == MPI_SUCCESS);
  return TCL_OK;
}

/**
   usage: returns MPI rank in given comm or, by default, adlb_comm
*/
static int
ADLB_CommRank_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  int rank = -1;
  if (objc == 1)
  {
    TCL_CONDITION(adlb_comm_init, "ADLB communicator not initialized");
    rank = adlb_comm_rank;
  }
  else if (objc == 2)
  {
    Tcl_WideInt comm_int;
    Tcl_Obj* comm_int_obj = objv[1];
    int rc = Tcl_GetWideIntFromObj(interp, comm_int_obj, &comm_int);
    TCL_CHECK_MSG(rc, "comm_rank: not an integer: '%s'",
                  Tcl_GetString(comm_int_obj));
    MPI_Comm comm = (MPI_Comm) comm_int;
    MPI_Comm_rank(comm, &rank);
    /* printf("ADLB_CommRank_Cmd(): comm_int: %lli\n", comm_int); */
    /* printf("ADLB_CommRank_Cmd(): rank:     %i\n",   rank); */
  }
  else
    TCL_RETURN_ERROR("requires 1 or 2 arguments!");

  Tcl_Obj* result = Tcl_NewIntObj(rank);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
ADLB_CommSize_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  int size = -1;
  if (objc == 1)
  {
    TCL_CONDITION(adlb_comm_init, "ADLB communicator not initialized");
    size = adlb_comm_size;
  }
  else if (objc == 2)
  {
    Tcl_WideInt comm_int;
    Tcl_Obj* comm_int_obj = objv[1];
    int rc = Tcl_GetWideIntFromObj(interp, comm_int_obj, &comm_int);
    TCL_CHECK_MSG(rc, "comm_size: not an integer: '%s'",
                  Tcl_GetString(comm_int_obj));
    MPI_Comm comm = (MPI_Comm) comm_int;
    MPI_Comm_size(comm, &size);
  }
  else
    TCL_RETURN_ERROR("requires 1 or 2 arguments!");

  Tcl_Obj* result = Tcl_NewIntObj(size);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
ADLB_CommDup_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2)
  Tcl_WideInt comm_int;
  Tcl_Obj* comm_int_obj = objv[1];
  int rc = Tcl_GetWideIntFromObj(interp, comm_int_obj, &comm_int);
  TCL_CHECK_MSG(rc, "comm_dup: not an integer: '%s'",
                Tcl_GetString(comm_int_obj));
  MPI_Comm comm = (MPI_Comm) comm_int;

  MPI_Comm newcomm;
  MPI_Comm_dup(comm, &newcomm);
  // This should work for MPICH or OpenMPI:
  Tcl_WideInt newcomm_int = (long long int) newcomm;
  Tcl_Obj* result = Tcl_NewWideIntObj(newcomm_int);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
ADLB_CommGet_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  char* comm_name = Tcl_GetString(objv[1]);
  MPI_Comm comm;
  if (strcmp(comm_name, "world") == 0)
    comm = MPI_COMM_WORLD;
  else if (strcmp(comm_name, "adlb") == 0)
    comm = ADLB_GetComm(); // MPI_COMM_WORLD;
  else if (strcmp(comm_name, "null") == 0)
    comm = MPI_COMM_NULL;
  else if (strcmp(comm_name, "leaders") == 0)
    comm = ADLB_GetComm_leaders();
  else if (strcmp(comm_name, "workers") == 0)
    comm = ADLB_GetComm_workers();
  else
    return turbine_user_errorv
      (interp, "adlb::comm_get: error: unknown comm: %s", comm_name);
  Tcl_Obj* result = Tcl_NewWideIntObj((long long int) comm);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: no args, returns rank within workers
*/
static int
ADLB_Worker_Rank_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(adlb_init, "ADLB not initialized");
  Tcl_SetObjResult(interp, Tcl_NewIntObj(adlb_worker_comm_rank));
  return TCL_OK;
}

/**
   usage: no args, returns true if a server, else false
*/
static int
ADLB_AmServer_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(adlb_init, "ADLB not initialized");
  Tcl_SetObjResult(interp, Tcl_NewBooleanObj(am_server));
  return TCL_OK;
}

/**
   usage: no args, returns size of MPI communicator ADLB is running on
*/
/*
static int
ADLB_Size_Cmd(ClientData cdata, Tcl_Interp *interp,
              int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(adlb_comm_init, "ADLB communicator not initialized");
  Tcl_SetObjResult(interp, Tcl_NewIntObj(adlb_comm_size));
  return TCL_OK;
}
*/

/**
   usage: no args, returns number of servers
*/
static int
ADLB_Servers_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(adlb_init, "ADLB not initialized");
  Tcl_SetObjResult(interp, Tcl_NewIntObj(adlb_servers));
  return TCL_OK;
}

/**
   usage: no args, returns number of workers
*/
static int
ADLB_Workers_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(adlb_init, "ADLB not initialized");
  Tcl_SetObjResult(interp, Tcl_NewIntObj(adlb_workers));
  return TCL_OK;
}

static int
ADLB_Hostmap_Lookup_Cmd(ClientData cdata, Tcl_Interp *interp,
                        int objc, Tcl_Obj *const objv[])
{
  // This is limited only by the number of ranks a user could
  // conceivably put on a node- getting bigger
  int count = 512;
  int ranks[count];
  int actual;

  char* name = Tcl_GetString(objv[1]);

  adlb_code rc = ADLB_Hostmap_lookup(name, count, ranks, &actual);
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
    Output a list containing the entries of the hostmap
    Note that the Turbine version of this function is different
 */
static int
ADLB_Hostmap_List_Cmd(ClientData cdata, Tcl_Interp *interp,
                      int objc, Tcl_Obj *const objv[])
{
  uint count;
  uint name_max;
  ADLB_Hostmap_stats(&count, &name_max);
  // Extra byte per name for RS
  uint chars = count*(name_max+1);
  char* buffer = malloc(chars * sizeof(char));

  int actual;
  ADLB_Hostmap_list(buffer, chars, 0, &actual);
  assert(actual == count);

  Tcl_Obj* names[count];
  char* p = buffer;
  for (int i = 0; i < count; i++)
  {
    char* t = strchr(p, '\r');
    assert(t != NULL);
    *t = '\0';
    Tcl_Obj* name = Tcl_NewStringObj(p, (int) (t-p));
    names[i] = name;
    p = t+1;
  }

  free(buffer);

  assert(count <= INT_MAX);
  Tcl_Obj* result = Tcl_NewListObj((int)count, names);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
ADLB_Leaders_Cmd(ClientData cdata, Tcl_Interp *interp,
                 int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(1);

  int* leaders = malloc(adlb_comm_size * sizeof(int));
  int count;
  ADLB_Leaders(leaders, &count);

  Tcl_Obj* dict = Tcl_NewDictObj();

  for (int i = 0; i < count; i++)
  {
    Tcl_Obj* index =  Tcl_NewIntObj(i);
    Tcl_Obj* leader = Tcl_NewIntObj(leaders[i]);
    Tcl_DictObjPut(interp, dict, index, leader);
  }
  Tcl_SetObjResult(interp, dict);
  return TCL_OK;
}

/**
   usage: adlb::put <reserve_rank> <work type> <work unit> <priority>
                    <parallelism> [<soft> <target>]
*/
static int
ADLB_Put_Cmd(ClientData cdata, Tcl_Interp *interp,
             int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 6 && objc <= 8, "Expected 5 to 7 arguments");
  int rc;

  int target_rank;
  int work_type;
  adlb_put_opts opts = ADLB_DEFAULT_PUT_OPTS;
  Tcl_GetIntFromObj(interp, objv[1], &target_rank);
  Tcl_GetIntFromObj(interp, objv[2], &work_type);
  int cmd_len;
  char* cmd = Tcl_GetStringFromObj(objv[3], &cmd_len);
  Tcl_GetIntFromObj(interp, objv[4], &opts.priority);
  Tcl_GetIntFromObj(interp, objv[5], &opts.parallelism);

  if (target_rank >= 0)
  {
    // Defaults: may be written below
    opts.strictness = ADLB_TGT_STRICT_HARD;
    opts.accuracy = ADLB_TGT_ACCRY_RANK;
  }

  if (objc >= 7)
  {
    rc = adlb_parse_strictness(interp, objv[6], &opts.strictness);
    TCL_CHECK(rc);
  }
  if (objc >= 8)
  {
    rc = adlb_parse_accuracy(interp, objv[7], &opts.accuracy);
    TCL_CHECK(rc);
  }

  DEBUG_ADLB("adlb::put: target_rank: %i type: %i \"%s\" %i",
             target_rank, work_type, cmd, opts.priority);


  adlb_code ac = ADLB_Put(cmd, cmd_len+1, target_rank, adlb_comm_rank,
                    work_type, opts);
  TCL_CONDITION(ac == ADLB_SUCCESS, "ADLB_Put failed!");
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

  adlb_put_opts opts = ADLB_DEFAULT_PUT_OPTS;
  opts.priority = ADLB_curr_priority;

  if (ADLB_Status() == ADLB_STATUS_SHUTDOWN)
  {
    printf("turbine: warning: canceling task spawn\n");
    return TCL_OK;
  }

  DEBUG_ADLB("adlb::spawn: type: %i \"%s\" %i",
             work_type, cmd, opts.priority);
  int rc = ADLB_Put(cmd, cmd_len+1, ADLB_RANK_ANY, adlb_comm_rank,
                    work_type, opts);

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

  void* payload = NULL;
  int work_len = 1000*1000*1000; // 1 GB
  int answer_rank;

  MPI_Comm task_comm;
  int rc = ADLB_Get(req_type, &payload, &work_len, work_len,
                    &answer_rank, &work_type, &task_comm);
  if (rc == ADLB_ERROR) TCL_RETURN_ERROR("Error getting work!");
  if (rc == ADLB_SHUTDOWN)
  {
    Tcl_SetObjResult(interp, Tcl_NewStringObj("", 0));
    work_len = 1;
    answer_rank = ADLB_RANK_NULL;
  }
  else // Good work unit
  {
    DEBUG_ADLB("adlb::get: %s\n", (char*) payload);
    Tcl_SetObjResult(interp, Tcl_NewStringObj(payload, work_len - 1));
    free(payload);
  }
  turbine_task_comm = task_comm;
  /* printf("ADLB_Get_Cmd(): turbine_task_comm: %lli\n", */
  /*        (long long int) turbine_task_comm); */

  int size;
  MPI_Comm_size(turbine_task_comm, &size);
  /* printf("ADLB_Get_Cmd(): turbine_task_comm size: %i\n", size); */

  // Store answer_rank in caller's stack frame
  Tcl_Obj* tcl_answer_rank = Tcl_NewIntObj(answer_rank);
  Tcl_ObjSetVar2(interp, tcl_answer_rank_name, NULL, tcl_answer_rank,
                 EMPTY_FLAG);

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

  MPI_Comm task_comm;
  adlb_code rc = ADLB_Iget(req_type, result, &work_len,
                           &answer_rank, &work_type, &task_comm);
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

  turbine_task_comm = task_comm;

  // Store answer_rank in caller's stack frame
  Tcl_Obj* tcl_answer_rank = Tcl_NewIntObj(answer_rank);
  Tcl_ObjSetVar2(interp, tcl_answer_rank_name, NULL, tcl_answer_rank,
                 EMPTY_FLAG);

  Tcl_SetObjResult(interp, Tcl_NewStringObj(result, -1));
  return TCL_OK;
}

/**
   Convert type string to adlb_data_type.
   If extra type info is provided, extra->valid is set to true
 */
static int adlb_type_from_string(Tcl_Interp *interp,
  const char* type_string, adlb_data_type *type, adlb_type_extra *extra)
{

  adlb_code rc = ADLB_Data_string_totype(type_string, type, extra);
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

/**
  Extract type info from object.

  Does not return any extra type info, if present
 */
int adlb_type_from_obj(Tcl_Interp *interp, Tcl_Obj *const objv[],
                   Tcl_Obj* obj, adlb_data_type *type)
{
  adlb_type_extra extra;
  int rc = adlb_type_from_obj_extra(interp, objv, obj, type, &extra);
  TCL_CHECK(rc);
  return TCL_OK;
}

int adlb_type_from_obj_extra(Tcl_Interp *interp, Tcl_Obj *const objv[],
         Tcl_Obj* obj, adlb_data_type *type, adlb_type_extra *extra)
{
  const char *type_name = Tcl_GetString(obj);
  TCL_CONDITION(type_name != NULL, "type argument not found!");
  int rc = adlb_type_from_string(interp, type_name, type, extra);
  TCL_CHECK(rc);
  return TCL_OK;
}


/**
  Extra type info from argument list, advancing index.
  First consume type name as first arg, then if there is additional info
  needed, e.g. container key/value types, consume that info
 */
int adlb_type_from_array(Tcl_Interp *interp, Tcl_Obj *const objv[],
        Tcl_Obj *const array[], int len, int *ix,
        adlb_data_type *type, adlb_type_extra *extra)
{
  int rc;
  // Avoid passing out any uninitialized bytes
  memset(extra, 0, sizeof(*extra));

  adlb_data_type tmp_type;
  rc = adlb_type_from_obj_extra(interp, objv, array[(*ix)++], &tmp_type,
                           extra);
  TCL_CHECK(rc);
  *type = tmp_type;

  // Process type-specific params if not already in type extra
  if (!extra->valid)
  {
    rc = adlb_type_extra_from_array(interp, objv, array, len, ix,
                                   *type, extra);
    TCL_CHECK(rc);
  }
  return TCL_OK;
}

int adlb_type_extra_from_array(Tcl_Interp *interp, Tcl_Obj *const objv[],
        Tcl_Obj *const array[], int len, int *ix,
        adlb_data_type type, adlb_type_extra *extra) {
  int rc;

  switch (type)
  {
    case ADLB_DATA_TYPE_CONTAINER: {
      TCL_CONDITION(len > *ix + 1, "type=container requires "
                    "key and value types!");
      adlb_data_type key_type, val_type;
      rc = adlb_type_from_obj(interp, objv, array[(*ix)++], &key_type);
      TCL_CHECK(rc);
      rc = adlb_type_from_obj(interp, objv, array[(*ix)++], &val_type);
      TCL_CHECK(rc);
      extra->CONTAINER.key_type = key_type;
      extra->CONTAINER.val_type = val_type;
      extra->valid = true;
      break;
    }
    case ADLB_DATA_TYPE_MULTISET: {
      TCL_CONDITION(len > *ix, "type=multiset requires "
                    "member type!");
      adlb_data_type val_type;
      rc = adlb_type_from_obj(interp, objv, array[(*ix)++], &val_type);
      TCL_CHECK(rc);
      extra->MULTISET.val_type = val_type;
      extra->valid = true;
      break;
    }
    default:
      // No extra info expected
      break;
  }
  return TCL_OK;
}

/*
  Extract variable create properties
  accept_id: if true, accept id as first element
  objv: arguments, objc: argument count, argstart: start argument
 */
static inline int
extract_create_props(Tcl_Interp *interp, bool accept_id, int argstart,
    int objc, Tcl_Obj *const objv[], adlb_datum_id *id,
    adlb_data_type *type, adlb_type_extra *type_extra,
    adlb_create_props *props)
{
  int rc;
  int argpos = argstart;

  // Avoid passing out any uninitialized bytes
  memset(props, 0, sizeof(*props));

  if (accept_id) {
    TCL_CONDITION(objc - argstart >= 2, "requires >= 2 args!");
    rc = Tcl_GetADLB_ID(interp, objv[argpos++], id);
    TCL_CHECK_MSG(rc, "could not get data id");
  } else {
    TCL_CONDITION(objc - argstart >= 1, "requires >= 1 args!");
    *id = ADLB_DATA_ID_NULL;
  }

  // Consume type info from arg list
  rc = adlb_type_from_array(interp, objv, objv, objc, &argpos, type,
                            type_extra);
  TCL_CHECK(rc);

  // Process create props if present
  *props = DEFAULT_CREATE_PROPS;
  props->release_write_refs = turbine_release_write_rc_policy(*type);

  // Separate integer and keyword args (for backward compatibility)
  const int max_int_args = 4;
  int n_int_args = 0;
  int int_args[max_int_args];

  for (;argpos < objc; argpos++)
  {
    if (n_int_args < max_int_args)
    {
      rc = Tcl_GetIntFromObj(interp, objv[argpos],
                            &int_args[n_int_args]);
      if (rc == TCL_OK)
      {
        n_int_args++;
        continue;
      }
    }

    // Must be keyword arg
    const char *argname = Tcl_GetString(objv[argpos]);
    if (strcmp(argname, "placement") == 0)
    {
      TCL_CONDITION(argpos + 1 < objc, "Missing placement argument");
      const char *placement_s = Tcl_GetString(objv[argpos + 1]);
      adlb_code ac = ADLB_string_to_placement(placement_s, &props->placement);
      TCL_CONDITION(ac == ADLB_SUCCESS, "invalid placement string %s",
                    placement_s);
      argpos++;
    }
    else
    {
      TCL_RETURN_ERROR("Invalid argument to data create call: %s",
                       argname);
    }
  }

  if (n_int_args >= 1) {
    props->read_refcount = int_args[0];
  }

  if (n_int_args >= 2) {
    props->write_refcount = int_args[1];
  }

  if (n_int_args >= 3) {
    props->symbol = (adlb_dsym)int_args[2];
  }

  if (n_int_args >= 4) {
    props->permanent = int_args[3] != 0;
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
    case ADLB_DATA_TYPE_STRUCT: {
      adlb_struct_type struct_t = type_extra.valid ?
              type_extra.STRUCT.struct_type : ADLB_STRUCT_TYPE_NULL;
      rc = ADLB_Create_struct(id, props, struct_t, &new_id);
      break;
    }
    case ADLB_DATA_TYPE_CONTAINER: {
      assert(type_extra.valid);
      rc = ADLB_Create_container(id, type_extra.CONTAINER.key_type,
                    type_extra.CONTAINER.val_type, props, &new_id);
      break;
    }
    case ADLB_DATA_TYPE_MULTISET: {
      assert(type_extra.valid);
      rc = ADLB_Create_multiset(id, type_extra.MULTISET.val_type,
                                props, &new_id);
      break;
    }
    case ADLB_DATA_TYPE_NULL:
    default:
      Tcl_AddErrorInfo(interp, "unknown type!");
      return TCL_ERROR;
      break;

  }

  if (id == ADLB_DATA_ID_NULL) {
    // need to return new ID
    Tcl_Obj* result = Tcl_NewADLB_ID(new_id);
    Tcl_SetObjResult(interp, result);
  }

  TCL_CONDITION(rc == ADLB_SUCCESS, "<%"PRId64"> failed!", id);
  return TCL_OK;
}

/**
   usage: adlb::multicreate [list of variable specs]*
   each list contains:
          <type> [<extra for type>]
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

  for (int i = 0; i < count; i++)
  {
    rc = parse_variable_spec_list(interp, objv, objv[i + 1], &specs[i]);
    TCL_CHECK(rc);
  }

  rc = ADLB_Multicreate(specs, count);
  TCL_CONDITION(rc == ADLB_SUCCESS, "failed!");

  // Build list to return
  Tcl_Obj *tcl_ids[count];
  for (int i = 0; i < count; i++) {
    tcl_ids[i] = Tcl_NewADLB_ID(specs[i].id);
  }
  Tcl_SetObjResult(interp, Tcl_NewListObj(count, tcl_ids));
  return TCL_OK;
}

/**
   usage: adlb::create_globals [list of variable specs]*
    Variable specs follow same format as adlb::multicreate.
    All globals have the permanent flag set so are not garbage collected.
    Must be called collectively by all ADLB ranks.
*/
static int
ADLB_Create_Globals_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  int rc;
  adlb_code ac;

  int count = objc - 1;

  // Allocate IDs for them (collective call)
  adlb_datum_id start;
  ac = ADLB_Alloc_global(count, &start);
  TCL_CONDITION(ac == ADLB_SUCCESS, "fail to reserve space for globals");

  ADLB_create_spec specs[count];

  for (int i = 0; i < count; i++)
  {
    rc = parse_variable_spec_list(interp, objv, objv[i + 1], &specs[i]);
    TCL_CHECK(rc);

    specs[i].props.permanent = true;

    specs[i].id = start + i;

    // Initialize local variables on server
    if (ADLB_Locate(specs[i].id) == adlb_comm_rank)
    {
      ac = ADLB_Create(specs[i].id, specs[i].type, specs[i].type_extra,
                       specs[i].props, NULL);
      TCL_CONDITION(ac == ADLB_SUCCESS, "error initializing global "
                   "<%"PRId64">", specs[i].id);
    }
  }


  // Build list to return
  Tcl_Obj *tcl_ids[count];
  for (int i = 0; i < count; i++) {
    tcl_ids[i] = Tcl_NewADLB_ID(specs[i].id);
  }
  Tcl_SetObjResult(interp, Tcl_NewListObj(count, tcl_ids));
  return TCL_OK;
}

static int
parse_variable_spec_list(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj *list, ADLB_create_spec *spec)
{
  int rc;
  int n;
  Tcl_Obj **elems;
  rc = Tcl_ListObjGetElements(interp, list, &n, &elems);
  TCL_CONDITION(rc == TCL_OK, "arg must be list: %s", Tcl_GetString(list));
  rc = extract_create_props(interp, false, 0, n, elems, &(spec->id),
            &(spec->type), &(spec->type_extra), &(spec->props));
  TCL_CHECK(rc);

  return TCL_OK;
}

/**
   usage: adlb::locate <id>

   Returns server rank
 */
static int
ADLB_Locate_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  int rc;

  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[1], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[1]));

  int rank = ADLB_Locate(handle.id);
  Tcl_SetObjResult(interp, Tcl_NewIntObj(rank));
  return TCL_OK;
}

static int
ADLB_Exists_Impl(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[],
                adlb_subscript_kind sub_kind)
{
  int min_args = sub_kind == ADLB_SUB_NONE ? 2 : 3;
  TCL_CONDITION(objc >= min_args,
                "requires at least %i arguments", min_args);
  int rc;

  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[1], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[1]));

  int argpos = 2;
  if (sub_kind != ADLB_SUB_NONE)
  {
    rc = ADLB_PARSE_SUB(objv[2], sub_kind, &handle.sub, true, true);
    TCL_CHECK_MSG(rc, "Invalid subscript argument %s",
                      Tcl_GetString(objv[2]));
    argpos = 3;
  }

  adlb_refc decr = ADLB_NO_REFC;
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                           &decr.read_refcount);
    TCL_CHECK_MSG(rc, "Expected integer argument");
  }

  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                           &decr.write_refcount);
    TCL_CHECK_MSG(rc, "Expected integer argument");
  }

  TCL_CONDITION(argpos == objc,
                "unexpected trailing args at %ith arg", argpos);

  bool b;
  rc = ADLB_Exists(handle.id, handle.sub.val, &b, decr);

  TCL_CONDITION(rc == ADLB_SUCCESS, "<%"PRId64"> failed!", handle.id);

  if (sub_kind != ADLB_SUB_NONE)
    DEBUG_ADLB("adlb::exists <%"PRId64">[%.*s] => %s", handle.id,
                (int)handle.sub.val.length,
                (const char*)handle.sub.val.key, bool2string(b));
  else
    DEBUG_ADLB("adlb::exists <%"PRId64"> => %s", handle.id,
               bool2string(b));

  ADLB_PARSE_HANDLE_CLEANUP(&handle);

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
  return ADLB_Exists_Impl(cdata, interp, objc, objv, ADLB_SUB_NONE);
}

/**
   usage: adlb::exists_sub <id> [<subscript>] [ <read decr> ]
                          [ <write decr> ]
 */
static int
ADLB_Exists_Sub_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return ADLB_Exists_Impl(cdata, interp, objc, objv, ADLB_SUB_CONTAINER);
}


/**
  Check if a datum is closed.
  If not found, counted as closed
  NOTE: decrements are applied before checking for close
   usage: adlb::closed <id> [ <read decr> ] [ <write decr> ]
 */
static int
ADLB_Closed_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 2, "requires at least 1 argument");
  int rc;

  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[1], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[1]));

  int argpos = 2;
  adlb_refc decr = ADLB_NO_REFC;
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                           &decr.read_refcount);
    TCL_CHECK_MSG(rc, "Expected integer argument");
  }

  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                           &decr.write_refcount);
    TCL_CHECK_MSG(rc, "Expected integer argument");
  }

  TCL_CONDITION(argpos == objc,
                "unexpected trailing args at %ith arg", argpos);

  adlb_refc curr_refcounts;
  rc = ADLB_Refcount_get(handle.id, &curr_refcounts, decr);

  TCL_CONDITION(rc == ADLB_SUCCESS, "<%"PRId64"> failed!", handle.id);

  ADLB_PARSE_HANDLE_CLEANUP(&handle);

  bool closed = curr_refcounts.write_refcount == 0;
  Tcl_Obj* result = Tcl_NewBooleanObj(closed);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/*
  Convert a tcl object to the ADLB representation.
  own_pointers: whether we want to own any memory allocated

  Note: initialises refcounts to 0
  result: the result
  alloced: whether memory was allocated that must be freed with
           ADLB_Free_storage
 */
int
adlb_tclobj2datum(Tcl_Interp *interp, Tcl_Obj *const objv[],
  adlb_data_type type, adlb_type_extra extra,
  Tcl_Obj *obj, bool own_pointers,
  adlb_datum_storage *result, bool *alloced)
{
  int rc;
  int length;
  *alloced = false; // Most don't allocate data
  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      rc = Tcl_GetADLBInt(interp, obj, &result->INTEGER);
      TCL_CHECK_MSG(rc, "adlb extract int from %s failed!",
                    Tcl_GetString(obj));
      return TCL_OK;
    case ADLB_DATA_TYPE_REF:
      rc = Tcl_GetADLB_ID(interp, obj, &result->REF.id);
      TCL_CHECK_MSG(rc, "adlb extract int from %s failed!",
                      Tcl_GetString(obj));
      // init refcounts to zero
      result->REF.read_refs = 0;
      result->REF.write_refs = 0;

      return TCL_OK;
    case ADLB_DATA_TYPE_FLOAT:
      rc = Tcl_GetDoubleFromObj(interp, obj, &result->FLOAT);
      TCL_CHECK_MSG(rc, "adlb extract double from %s failed!",
                      Tcl_GetString(obj));
      return TCL_OK;
    case ADLB_DATA_TYPE_STRING:
      result->STRING.value = Tcl_GetStringFromObj(obj, &length);
      result->STRING.length = (size_t) length;
      TCL_CONDITION(result != NULL, "adlb extract string from %p failed!",
                      obj);
      result->STRING.length++; // Account for null byte
      TCL_CONDITION(result->STRING.length <= ADLB_DATA_MAX,
          "adlb: string too long (%zu bytes)", result->STRING.length);
      if (own_pointers)
      {
        result->STRING.value = strdup(result->STRING.value);
        TCL_CONDITION(result->STRING.value != NULL,
                      "Error allocating memory");
      }
      return TCL_OK;
    case ADLB_DATA_TYPE_BLOB:
    {
      // Take list-based blob representation
      int rc = extract_tcl_blob(interp, objv, obj, &result->BLOB, NULL);
      TCL_CHECK(rc);
      if (own_pointers)
      {
        void *tmp = malloc(result->BLOB.length);
        TCL_CONDITION(tmp != NULL, "Error allocating memory");
        memcpy(tmp, result->BLOB.value, result->BLOB.length);
        result->BLOB.value = tmp;
      }
      return TCL_OK;
    }
    case ADLB_DATA_TYPE_STRUCT:
    {
      TCL_CONDITION(extra.valid, "Must specify struct type to convert "
                                    "dict to struct");
      int rc = tcl_dict_to_adlb_struct(interp, objv, obj,
             extra.STRUCT.struct_type, &result->STRUCT);
      *alloced = true;
      TCL_CHECK(rc);
      return TCL_OK;
    }
    case ADLB_DATA_TYPE_CONTAINER:
    case ADLB_DATA_TYPE_MULTISET:
        // Containers/multiset packed directly to binary
      TCL_RETURN_ERROR("Type %s should be packed directly to binary\n",
          ADLB_Data_type_tostring(type));
      return TCL_ERROR;
    default:
      printf("unknown type %i!\n", type);
      return TCL_ERROR;
  }
  return TCL_OK;
}

static void
free_compound_type(compound_type *types)
{
  assert(types != NULL);
  if (types->types != NULL)
  {
    free(types->types);
  }
  if (types->extras != NULL)
  {
    free(types->extras);
  }
}

/* Consume next entry from compound_type */
static inline int
compound_type_next(Tcl_Interp *interp, Tcl_Obj *const objv[],
      const compound_type types, int *ctype_pos,
      adlb_data_type *type, adlb_type_extra *extra)
{
  TCL_CONDITION(*ctype_pos < types.len,
          "Consumed past end of compound type info (%i/%i)",
          *ctype_pos, types.len);

  *type = types.types[*ctype_pos];
  if (types.extras == NULL)
  {
    extra->valid = false;
  }
  else
  {
    *extra =  types.extras[*ctype_pos];
  }
  (*ctype_pos)++;
  return TCL_OK;
}

static int
adlb_tclobj2bin_compound(Tcl_Interp *interp, Tcl_Obj *const objv[],
                const compound_type types,
                Tcl_Obj *obj, bool canonicalize,
                const adlb_buffer *caller_buffer,
                adlb_binary_data* result)
{
  adlb_data_code dc;
  int rc;

  adlb_buffer packed;
  size_t pos = 0;
  bool using_caller_buf;

  // Caller blob needs to own data, so don't provide a static buffer
  dc = ADLB_Init_buf(caller_buffer, &packed, &using_caller_buf, 2048);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error initializing buffer");

  rc = adlb_tclobj_bin_append(interp, objv, types, 0, obj, false,
                canonicalize, &packed, &using_caller_buf, &pos);
  TCL_CHECK(rc);

  result->data = result->caller_data = packed.data;
  result->length = pos;
  return TCL_OK;
}

/*
  Append binary representation of Tcl object to buffer
  types: full ADLB type of data for serialization
  ctype_pos: current position into types (in case of nested types).
          This is advanced as type entries are processed.
  canonicalize: ensure binary representation is canonical, e.g.
          containers are in sorted order
 */
static int
adlb_tclobj_bin_append(Tcl_Interp *interp, Tcl_Obj *const objv[],
        const compound_type types, int ctype_pos,
        Tcl_Obj *obj, bool prefix_len, bool canonicalize,
        adlb_buffer *output, bool *output_caller_buf,
        size_t *output_pos)
{
  int rc;
  adlb_data_type type;
  adlb_type_extra extra;

  rc = compound_type_next(interp, objv, types, &ctype_pos, &type, &extra);
  TCL_CHECK(rc);

  // Some serialization routines know how to append to buffer
  if (ADLB_pack_pad_size(type))
  {
    size_t start_pos = *output_pos;
    if (prefix_len)
    {
      adlb_data_code dc = ADLB_Resize_buf(output, output_caller_buf,
                                          start_pos + VINT_MAX_BYTES);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error resizing");

      memset(output->data + start_pos, 0, VINT_MAX_BYTES);
      (*output_pos) += (int)VINT_MAX_BYTES;
    }

    if (type == ADLB_DATA_TYPE_CONTAINER)
    {
      rc = tcl_dict_to_packed_container(interp, objv, types, ctype_pos,
            obj, canonicalize, output, output_caller_buf, output_pos);
      TCL_CHECK(rc);
    }
    else if (type == ADLB_DATA_TYPE_MULTISET)
    {
      rc = tcl_list_to_packed_multiset(interp, objv, types, ctype_pos,
            obj, canonicalize, output, output_caller_buf, output_pos);
      TCL_CHECK(rc);
    }
    else
    {
      TCL_RETURN_ERROR("Don't know how to incrementally append type: %s",
                        ADLB_Data_type_tostring(type));
    }

    if (prefix_len)
    {
      size_t packed_len = *output_pos - start_pos - VINT_MAX_BYTES;
      // Add int to spot we reserved
      vint_encode_size_t(packed_len, output->data + start_pos);
    }
  }
  else
  {
    // In other cases, we serialize the whole thing, then append it
    adlb_datum_storage tmp;
    bool alloced;
    rc = adlb_tclobj2datum(interp, objv, type, extra, obj, false,
                              &tmp, &alloced);
    TCL_CHECK(rc);

    // TODO: need canonicalize option to ADLB_Pack
    adlb_binary_data packed;
    // Make sure data is serialized in contiguous memory
    adlb_data_code dc = ADLB_Pack(&tmp, type, NULL, &packed);

    if (alloced)
    {
      // Free memory before checking for errors
      adlb_data_code dc2 = ADLB_Free_storage(&tmp, type);
      TCL_CONDITION(dc2 == ADLB_DATA_SUCCESS, "Error freeing storage");
    }

    TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
                  "Error packing data type %i into buffer", type);

    dc = ADLB_Append_buffer(ADLB_DATA_TYPE_NULL, packed.data,
              packed.length, prefix_len, output, output_caller_buf,
              output_pos);

    if (packed.caller_data != NULL)
    {
      // We were given ownership of data, free now
      free(packed.caller_data);
    }
    TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error resizing buffer");

  }
  return TCL_OK;
}

static int
adlb_tclobj_bin_append2(Tcl_Interp *interp, Tcl_Obj *const objv[],
        adlb_data_type type, adlb_type_extra extra,
        Tcl_Obj *obj, bool prefix_len, bool canonicalize,
        adlb_buffer *output, bool *output_caller_buf,
        size_t *output_pos)
{
  // NOTE: it's ok to remove const qualifier since it isn't
  //       modified by called function.
  compound_type ct = { .len = 1, .types = &type,
        .extras = (adlb_type_extra*)&extra };
  return adlb_tclobj_bin_append(interp, objv, ct, 0, obj,
         false, canonicalize, output, output_caller_buf, output_pos);
}

/**
  Take a Tcl object and an ADLB type and extract the binary representation
  type: adlb data type code
  caller_buffer: optional static buffer to use
  result: serialized result data.  Either has malloced buffer,
          or pointer to caller_buffer->data
 */
int
adlb_tclobj2bin(Tcl_Interp *interp, Tcl_Obj *const objv[],
                adlb_data_type type, adlb_type_extra extra,
                Tcl_Obj *obj, bool canonicalize,
                const adlb_buffer *caller_buffer,
                adlb_binary_data* result)
{
  int rc;
  adlb_data_code dc;
  if (type == ADLB_DATA_TYPE_CONTAINER ||
      type == ADLB_DATA_TYPE_MULTISET)
  {
    // For container types, use temporary buffer to append
    adlb_buffer buf;
    bool using_caller_buf;
    size_t pos = 0;
    dc = ADLB_Init_buf(caller_buffer, &buf,
                                      &using_caller_buf, 128);
    TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error initializing buffer");

    rc = adlb_tclobj_bin_append2(interp, objv, type, extra, obj,
                false, canonicalize, &buf, &using_caller_buf, &pos);
    TCL_CHECK(rc);

    result->data = result->caller_data = buf.data;
    result->length = pos;
    return TCL_OK;
  }

  // For other types, where we will not typically be appending to array
  adlb_datum_storage tmp;
  bool alloced;
  rc = adlb_tclobj2datum(interp, objv, type, extra, obj, false,
                            &tmp, &alloced);
  TCL_CHECK(rc);

  // Make sure data is serialized in contiguous memory
  dc = ADLB_Pack(&tmp, type, caller_buffer, result);

  if (alloced)
  {
    // Free memory before checking for errors
    adlb_data_code dc2 = ADLB_Free_storage(&tmp, type);
    TCL_CONDITION(dc2 == ADLB_DATA_SUCCESS, "Error freeing storage");
  }

  TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
                "Error packing data type %i into buffer", type);

  // Make sure caller owns the memory (i.e. it's not a pointer to tmp)
  dc = ADLB_Own_data(caller_buffer, result);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error getting ownership of "
                "buffer for data type %i", type);
  return TCL_OK;
}

static int
tcl_append_key_val(Tcl_Interp *interp, Tcl_Obj *const objv[],
  const compound_type types, int ctype_pos, adlb_data_type key_type,
  Tcl_Obj *key, Tcl_Obj *val, bool canonicalize,
  adlb_buffer *output, bool *output_caller_buf, size_t *output_pos)
{
  adlb_data_code dc;
  int rc;

  int tmplen;
  const char* key_data = Tcl_GetStringFromObj(key, &tmplen);
  size_t key_strlen = (size_t) tmplen;

  // Pack string as binary directly
  dc = ADLB_Append_buffer(key_type, key_data, key_strlen + 1,
                          true, output, output_caller_buf, output_pos);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error appending to buffer");

  // Recursively serialize value (which may be a compound type such as
  //  a list or a dict)
  // Value type needs to be first for recursive call
  int rec_ctype_pos = ctype_pos - 1;
  rc = adlb_tclobj_bin_append(interp, objv, types, rec_ctype_pos, val, true,
                  canonicalize, output, output_caller_buf, output_pos);
  TCL_CHECK_MSG(rc, "Error serializing dict val");

  return TCL_OK;
}

static int
tcl_dict_to_packed_container(Tcl_Interp *interp, Tcl_Obj *const objv[],
        const compound_type types, int ctype_pos, Tcl_Obj *dict,
        bool canonicalize, adlb_buffer *output, bool *output_caller_buf,
        size_t *output_pos)
{
  int rc;
  adlb_data_code dc;

  int entries;
  rc = Tcl_DictObjSize(interp, dict, &entries);
  TCL_CHECK(rc);

  adlb_data_type key_type, val_type;
  adlb_type_extra key_extra, val_extra;

  // Note: assuming key isn't a compound type, because we don't
  //       consume additional type info for key
  rc = compound_type_next(interp, objv, types, &ctype_pos,
                          &key_type, &key_extra);
  TCL_CHECK(rc);

  // Val might be a compound type: we consume that info later
  rc = compound_type_next(interp, objv, types, &ctype_pos,
                          &val_type, &val_extra);
  TCL_CHECK(rc);

  dc = ADLB_Pack_container_hdr(entries, key_type, val_type, output,
                                output_caller_buf, output_pos);
  TCL_CONDITION_GOTO(dc == ADLB_DATA_SUCCESS, exit_err,
        "Error packing container header");

  if (canonicalize)
  {
    // Need to sort keys into canonical order
    Tcl_Obj *dict_str = Tcl_NewStringObj("dict", 4);
    Tcl_Obj *keys_str = Tcl_NewStringObj("keys", 4);
    Tcl_Obj *dict_keys_objv[] = {dict_str, keys_str, dict};
    int dict_keys_objc = 3;
    rc = Tcl_EvalObjv(interp, dict_keys_objc, dict_keys_objv, 0);
    TCL_CHECK(rc);
    Tcl_DecrRefCount(dict_str);
    Tcl_DecrRefCount(keys_str);

    Tcl_Obj *dict_keys = Tcl_GetObjResult(interp);
    assert(dict_keys != NULL);
    // Need to get reference count to prevent result from being
    // overwritten too early by lsort
    Tcl_IncrRefCount(dict_keys);
    Tcl_ResetResult(interp);

    Tcl_Obj *lsort_str = Tcl_NewStringObj("lsort", 5);
    Tcl_Obj *lsort_objv[] = {lsort_str, dict_keys};
    int lsort_objc = 2;
    rc = Tcl_EvalObjv(interp, lsort_objc, lsort_objv, 0);
    TCL_CHECK(rc);
    Tcl_DecrRefCount(lsort_str);

    // Now have sorted keys
    Tcl_DecrRefCount(dict_keys);
    dict_keys = Tcl_GetObjResult(interp);
    assert(dict_keys != NULL);
    Tcl_IncrRefCount(dict_keys);
    Tcl_ResetResult(interp);

    Tcl_Obj **dict_keysv;
    int dict_keysc;
    rc = Tcl_ListObjGetElements(interp, dict_keys, &dict_keysc,
                                &dict_keysv);
    TCL_CHECK(rc);

    for (int i = 0; i < dict_keysc; i++)
    {
      Tcl_Obj *key, *val;

      key = dict_keysv[i];
      assert(key != NULL);
      rc = Tcl_DictObjGet(interp, dict, key, &val);
      TCL_CHECK(rc);

      rc = tcl_append_key_val(interp, objv, types, ctype_pos,
          key_type, key, val, canonicalize,
          output, output_caller_buf, output_pos);
      TCL_CHECK_MSG_GOTO(rc, exit_err, "Error appending key/val");
    }
  }
  else
  {
    Tcl_DictSearch iter;
    for (int i = 0; i < entries; i++)
    {
      Tcl_Obj *key, *val;
      int done;
      if (i == 0)
      {
        rc = Tcl_DictObjFirst(interp, dict, &iter, &key, &val, &done);
        TCL_CHECK_MSG_GOTO(rc, exit_err, "Error parsing packed "
                                         "container entry");
      }
      else
      {
        Tcl_DictObjNext(&iter, &key, &val, &done);
      }
      assert(!done); // Should match Tcl_DictObjSize call

      rc = tcl_append_key_val(interp, objv, types, ctype_pos,
          key_type, key, val, canonicalize,
          output, output_caller_buf, output_pos);
      TCL_CHECK_MSG_GOTO(rc, exit_err, "Error appending key/val");
    }
  }

  return TCL_OK;

exit_err:
  return TCL_ERROR;
}

int
tcl_list_to_packed_multiset(Tcl_Interp *interp, Tcl_Obj *const objv[],
        const compound_type types, int ctype_pos, Tcl_Obj *list,
        bool canonicalize, adlb_buffer *output, bool *output_caller_buf,
        size_t *output_pos)
{
  int rc;
  adlb_data_code dc;

  if (canonicalize)
  {
    // Need to sort list if canonicalize
    Tcl_Obj *lsort_str = Tcl_NewStringObj("lsort", 5);
    Tcl_Obj *lsort_objv[] = {lsort_str, list};
    int lsort_objc = 2;
    rc = Tcl_EvalObjv(interp, lsort_objc, lsort_objv, 0);
    TCL_CHECK(rc);
    Tcl_DecrRefCount(lsort_str);
    list = Tcl_GetObjResult(interp);
    assert(list != NULL);
    Tcl_IncrRefCount(list);
    Tcl_ResetResult(interp);
  }
  int listc;
  Tcl_Obj **listv;
  rc = Tcl_ListObjGetElements(interp, list, &listc, &listv);
  TCL_CHECK(rc);


  adlb_data_type elem_type;
  adlb_type_extra elem_extra;

  // Elem might be a compound type: we consume that info later
  rc = compound_type_next(interp, objv, types, &ctype_pos,
                          &elem_type, &elem_extra);
  TCL_CHECK(rc);

  dc = ADLB_Pack_multiset_hdr(listc, elem_type, output, output_caller_buf,
                              output_pos);
  TCL_CONDITION_GOTO(dc == ADLB_DATA_SUCCESS, exit_err,
                     "Error serializing multiset header");

  for (int i = 0; i < listc; i++)
  {
    Tcl_Obj *elem = listv[i];

    // Value type needs to be first for recursive call
    int rec_ctype_pos = ctype_pos - 1;
    rc = adlb_tclobj_bin_append(interp, objv, types, rec_ctype_pos,
                elem, true, canonicalize,
                output, output_caller_buf, output_pos);
    TCL_CHECK_MSG_GOTO(rc, exit_err, "Error serializing multiset elem");
  }

  return TCL_OK;

exit_err:
  return TCL_ERROR;
}

/*
   Build a representation of an ADLB struct using Tcl dicts, handling
   nested structs. E.g.

   ADLB struct:
     [ a: { foo: 1, bar: "hello" }, b: 3.14 ]
   Tcl Dict:
     { a: { foo: 1, bar: "hello" }, b: 3.14 }

    If extra type info is provided, checks type is as expected
 */
static int
packed_struct_to_tcl_dict(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         const void *data, size_t length,
                         adlb_type_extra extra, Tcl_Obj **result)
{
  assert(data != NULL);
  assert(result != NULL);
  int rc;

  adlb_struct_type st;

  adlb_packed_struct_hdr *hdr = (adlb_packed_struct_hdr *)data;

  TCL_CONDITION(length >= sizeof(*hdr), "Not enough data for header");

  st = hdr->type;
  TCL_CONDITION(!extra.valid || st == extra.STRUCT.struct_type,
                "Expected struct type %i but got %i",
                extra.STRUCT.struct_type, st);

  const char *st_name;
  int field_count;
  const adlb_struct_field_type *field_types;
  char const* const* field_names;
  adlb_data_code dc = ADLB_Lookup_struct_type(st,
                  &st_name, &field_count, &field_types, &field_names);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
                "Error looking up struct type %i", st);

  TCL_CONDITION(length >= sizeof(*hdr) + sizeof(hdr->field_offsets[0]) *
                (size_t)field_count, "Not enough data for header");

  assert(st < field_name_objs.size);
  Tcl_Obj **field_names2 = field_name_objs.objs[st];
  assert(field_names2 != NULL);

  Tcl_Obj *result_dict = Tcl_NewDictObj();

  for (int i = 0; i < field_count; i++)
  {
    const char *name = field_names[i];
    // Find slice of buffer for the field
    size_t offset = hdr->field_offsets[i];
    // Check if
    bool valid = (((char*)data)[offset]) != 0;
    if (valid)
    {
      size_t data_offset = offset + 1;
      const char* field_data = (char*) data;
      field_data += data_offset;
      size_t field_data_length;
      if (i == field_count - 1)
        field_data_length = length - data_offset;
      else
        field_data_length = hdr->field_offsets[i + 1] - data_offset;

      TCL_CONDITION(data_offset + field_data_length <= length,
          "invalid struct buffer: field %s past buffer end: %zu+%zu vs %zu",
          name, data_offset, field_data_length, length);

      // Create a Tcl object for the field data
      Tcl_Obj *field_tcl_obj;
      rc = adlb_datum2tclobj(interp, objv, ADLB_DATA_ID_NULL,
                    field_types[i].type, field_types[i].extra,
                    field_data, field_data_length, &field_tcl_obj);
      TCL_CHECK_MSG(rc, "Error building tcl object for field %s", name);

      // Add it to nested dicts
      assert(field_names2[i] != NULL);
      assert(field_tcl_obj != NULL);
      rc = Tcl_DictObjPut(interp, result_dict,
                        field_names2[i], field_tcl_obj);
      TCL_CHECK_MSG(rc, "Error inserting tcl object for field %s", name);
    }
  }

  *result = result_dict;
  return TCL_OK;
}

/*
  Note that result must be freed by caller
 */
static int
tcl_dict_to_adlb_struct(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj *dict, adlb_struct_type struct_type,
                         adlb_struct **result)
{
  int rc;

  const char *st_name;
  int field_count;
  const adlb_struct_field_type *field_types;
  char const* const* field_names;
  adlb_data_code dc = ADLB_Lookup_struct_type(struct_type,
                  &st_name, &field_count, &field_types, &field_names);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
                "Error looking up struct type %i", struct_type);
  *result = malloc(sizeof(adlb_struct) +
                   sizeof((*result)->fields[0]) * (size_t)field_count);
  TCL_MALLOC_CHECK(*result);
  (*result)->type = struct_type;

  // Get field name objects
  assert(struct_type < field_name_objs.size);
  Tcl_Obj **field_names2 = field_name_objs.objs[struct_type];
  assert(field_names2 != NULL);


  for (int i = 0; i < field_count; i++)
  {
    Tcl_Obj *val;

    rc = Tcl_DictObjGet(interp, dict, field_names2[i], &val);
    TCL_CHECK_MSG(rc, "Could not find val for %s (or %s) in %s",
          field_names[i], Tcl_GetString(field_names2[i]),
          Tcl_GetString(dict));

    if (val != NULL)
    {
      adlb_datum_storage *field = &(*result)->fields[i].data;
      bool alloced;
      // Need to own memory in allocated object so we can free correctly
      rc = adlb_tclobj2datum(interp, objv, field_types[i].type,
                        field_types[i].extra, val, true, field, &alloced);
      TCL_CHECK(rc);
      (*result)->fields[i].initialized = true;
    }
    else
    {
      // Data not present
      (*result)->fields[i].initialized = false;
    }
  }

  return TCL_OK;
}

static int
packed_container_to_dict(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         const void *data, size_t length,
                         adlb_type_extra extra, Tcl_Obj **result)
{
  size_t pos = 0;
  adlb_data_type key_type, val_type;
  int entries;
  int rc = TCL_OK;

  adlb_data_code dc;

  dc = ADLB_Unpack_container_hdr(data, length, &pos, &entries,
                                 &key_type, &val_type);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error parsing packed data "
                                         "header");

  if (extra.valid)
  {
    TCL_CONDITION(val_type == extra.CONTAINER.val_type, "Packed value "
          "type doesn't match expected: %s vs. %s",
          ADLB_Data_type_tostring(val_type),
          ADLB_Data_type_tostring(extra.CONTAINER.val_type));
    TCL_CONDITION(key_type == extra.CONTAINER.key_type, "Packed key "
          "type doesn't match expected: %s vs. %s",
          ADLB_Data_type_tostring(key_type),
          ADLB_Data_type_tostring(extra.CONTAINER.key_type));
  }

  Tcl_Obj *dict = Tcl_NewDictObj();
  for (int i = 0; i < entries; i++)
  {
    const void *key, *val;
    size_t key_len, val_len;
    dc = ADLB_Unpack_container_entry(key_type, val_type, data, length,
                                &pos, &key, &key_len, &val, &val_len);
    TCL_CONDITION_GOTO(dc == ADLB_DATA_SUCCESS, exit_err,
            "Error parsing packed container entry");

    Tcl_Obj *key_obj, *val_obj;

    rc = adlb_datum2tclobj(interp, objv, ADLB_DATA_ID_NULL, val_type,
            ADLB_TYPE_EXTRA_NULL, val, val_len, &val_obj);
    TCL_CHECK_MSG_GOTO(rc, exit_err, "Error constructing Tcl object "
            "of type %s for packed container val",
            ADLB_Data_type_tostring(val_type));

    int tmp_len = (int) (key_len - 1);
    key_obj = Tcl_NewStringObj(key, tmp_len);
    rc = Tcl_DictObjPut(interp, dict, key_obj, val_obj);
    if (rc != TCL_OK)
    {
      Tcl_DecrRefCount(key_obj);
      Tcl_DecrRefCount(val_obj);
      turbine_tcl_condition_failed(interp, objv[0],
            "Error adding entry to dict");
      goto exit_err;

    }
  }

  TCL_CONDITION_GOTO(pos == length, exit_err, "Didn't consume all "
      "container data: %zu bytes packed, consumed %zu bytes",
      length, pos);

  rc = TCL_OK;

exit_err:
  if (rc == TCL_OK)
  {
    *result = dict;
  }
  else
  {
    Tcl_DecrRefCount(dict);
  }

  return rc;
}

static int
packed_multiset_to_list(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         const void *data, size_t length,
                         adlb_type_extra extra, Tcl_Obj **result)
{
  Tcl_Obj **arr = NULL;
  size_t pos = 0;
  adlb_data_type elem_type;
  int entry = 0; // Track how many entries we've inserted
  int entries;
  int rc = TCL_OK;

  adlb_data_code dc;

  dc = ADLB_Unpack_multiset_hdr(data, length, &pos, &entries, &elem_type);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error parsing packed data "
                                          "header");

  if (extra.valid)
  {
    TCL_CONDITION(elem_type == extra.MULTISET.val_type, "Packed element "
          "type doesn't match expected: %s vs. %s",
          ADLB_Data_type_tostring(elem_type),
          ADLB_Data_type_tostring(extra.MULTISET.val_type));
  }


  assert(entries >= 0);
  arr = malloc(sizeof(Tcl_Obj*) * (size_t)entries);
  for (entry = 0; entry < entries; entry++)
  {
    const void *elem;
    size_t elem_len;
    dc = ADLB_Unpack_multiset_entry(elem_type, data, length, &pos,
                                    &elem, &elem_len);
    if (dc != ADLB_DATA_SUCCESS)
    {
      turbine_tcl_condition_failed(interp, objv[0],
            "Error parsing packed multiset entry");
      goto exit_err;
    }

    rc = adlb_datum2tclobj(interp, objv, ADLB_DATA_ID_NULL, elem_type,
            ADLB_TYPE_EXTRA_NULL, elem, elem_len, &arr[entry]);
    if (rc != TCL_OK)
    {
      turbine_tcl_condition_failed(interp, objv[0],
            "Error constructing Tcl object for packed multiset entry");
      goto exit_err;
    }
  }

  TCL_CONDITION_GOTO(pos == length, exit_err, "Didn't consume all "
      "container data: %zu bytes packed, consumed %zu bytes", length, pos);
  rc = TCL_OK;

exit_err:
  if (rc == TCL_OK)
  {
    *result = Tcl_NewListObj(entries, arr);
    free(arr);
  }
  else if (arr != NULL)
  {
    // Free any added entries
    for (int i = 0; i < entry - 1; i++)
    {
      Tcl_DecrRefCount(arr[i]);
    }
    free(arr);
  }

  return rc;
}


/**
   usage: adlb::store <id> <type> [ <extra> ] <value>
                      [ <decrement writers> ] [ <decrement readers> ]
                      [ <store readers> ] [ <store writers> ]
   extra: any extra info for type, e.g. struct type when storing struct
   value: value to be stored
   decrement readers/writers: Optional  Decrement the readers/writers
          reference count by this amount.  Defaults are 0 read, 1 write
   store readers/writers: Optional  Add this many references to any
          stored reference variables.   Defaults are 2 read, 0 write
*/
static int
ADLB_Store_Cmd(ClientData cdata, Tcl_Interp* interp,
               int objc, Tcl_Obj* const objv[])
{
  TCL_CONDITION(objc >= 4, "requires at least 4 args!");
  int rc;
  int argpos = 1;

  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[argpos++], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s",
                Tcl_GetString(objv[argpos-1]));

  adlb_data_type type;
  adlb_type_extra extra;
  rc = adlb_type_from_obj_extra(interp, objv, objv[argpos++], &type,
                         &extra);
  TCL_CHECK(rc);

  adlb_binary_data data; // The data to send
  if (type == ADLB_DATA_TYPE_CONTAINER ||
      type == ADLB_DATA_TYPE_MULTISET)
  {
    // Handle non-straightforward cases where we need additional type info
    argpos--; // Rewind so type can be reprocessed
    compound_type compound_type;
    rc = get_compound_type(interp, objc, objv, &argpos, &compound_type);
    TCL_CHECK(rc);

    Tcl_Obj *obj = objv[argpos++];
    // Straightforward case with no nested type info
    rc = adlb_tclobj2bin_compound(interp, objv, compound_type,
                                 obj, false, &xfer_buf, &data);
    TCL_CHECK_MSG(rc, "<%"PRId64"> failed, could not extract data from "
                  "%s!", handle.id, Tcl_GetString(obj));
    free_compound_type(&compound_type);
  }
  else
  {
    Tcl_Obj *obj = objv[argpos++];
    // Straightforward case with no nested type info
    rc = adlb_tclobj2bin(interp, objv, type, extra,
                        obj, false, &xfer_buf, &data);
    TCL_CHECK_MSG(rc, "<%"PRId64"> failed, could not extract data from "
                  "%s!", handle.id, Tcl_GetString(obj));
  }

  // Handle optional refcount spec
  adlb_refc decr = ADLB_WRITE_REFC; // default is to decr writers
  if (argpos < objc) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.write_refcount);
    TCL_CHECK_MSG(rc, "decrement arg must be int!");
  }

  if (argpos < objc) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr.read_refcount);
    TCL_CHECK_MSG(rc, "decrement arg must be int!");
  }

  // Handle optional number of refcounts to store
  adlb_refc store_refcounts = ADLB_READ_REFC;
  if (argpos < objc) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                 &store_refcounts.read_refcount);
    TCL_CHECK_MSG(rc, "store refcount arg must be int!");
  }

  if (argpos < objc) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                 &store_refcounts.write_refcount);
    TCL_CHECK_MSG(rc, "store refcount arg must be int!");
  }


  TCL_CONDITION(argpos == objc,
          "extra trailing arguments starting at argument %i", argpos);

  // DEBUG_ADLB("adlb::store: <%"PRId64">=%s", id, data);
  int store_rc = ADLB_Store(handle.id, handle.sub.val, type,
                  data.data, data.length, decr, store_refcounts);

  // Free if needed
  if (data.data != xfer_buf.data)
    ADLB_Free_binary_data(&data);

  CHECK_ADLB_STORE(interp, objv, store_rc, handle.id, handle.sub.val);

  rc = ADLB_PARSE_HANDLE_CLEANUP(&handle);
  TCL_CHECK(rc);

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
  int argpos = 1;

  Tcl_Obj *handle_obj = objv[argpos++];
  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(handle_obj, &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(handle_obj));

  int decr_amount = 0;
  if (decr) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++], &decr_amount);
    TCL_CHECK_MSG(rc, "requires decr amount!");
  }

  adlb_data_type given_type = ADLB_DATA_TYPE_NULL;
  adlb_type_extra extra = { .valid = false };
  if (argpos < objc)
  {
    rc = adlb_type_from_obj_extra(interp, objv, objv[argpos++],
                                  &given_type, &extra);
    TCL_CHECK_MSG(rc, "arg %i must be valid type!", argpos);
  }

  // Retrieve the data, actual type, and length from server
  adlb_data_type type;
  size_t length;
  adlb_retrieve_refc refcounts = ADLB_RETRIEVE_NO_REFC;
  refcounts.decr_self.read_refcount = decr_amount;
  int ret_rc = ADLB_Retrieve(handle.id, handle.sub.val, refcounts,
                             &type, xfer, &length);
  CHECK_ADLB_RETRIEVE(ret_rc, handle);

  rc = ADLB_PARSE_HANDLE_CLEANUP(&handle);
  TCL_CHECK(rc);

  // Type check
  if ((given_type != ADLB_DATA_TYPE_NULL &&
       given_type != type))
  {
    report_type_mismatch(given_type, type);
    return TCL_ERROR;
  }

  // Unpack from xfer to Tcl object
  Tcl_Obj* result = NULL;
  rc = adlb_datum2tclobj(interp, objv, handle.id, type, extra,
                            xfer, length, &result);
  TCL_CHECK(rc);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   interp, objv, id, and length: just for error checking and messages
   If object is a blob, this converts it to a string

   TODO: this always copies input, will need to change to support
         large blobs
 */
int
adlb_datum2tclobj(Tcl_Interp *interp, Tcl_Obj *const objv[],
    adlb_datum_id id, adlb_data_type type, adlb_type_extra extra,
    const void *data, size_t length, Tcl_Obj** result)
{
  adlb_datum_storage tmp;
  adlb_data_code dc;
  assert(length <= ADLB_DATA_MAX);

  switch (type)
  {
    case ADLB_DATA_TYPE_INTEGER:
      dc = ADLB_Unpack_integer(&tmp.INTEGER, data, length);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
            "Retrieve failed due to error unpacking integer data <%"PRId64"> error code: %i", id, dc);
      *result = Tcl_NewADLBInt(tmp.INTEGER);
      break;
    case ADLB_DATA_TYPE_REF:
      dc = ADLB_Unpack_ref(&tmp.REF, data, length, ADLB_NO_REFC, true);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
            "Retrieve failed due to error unpacking reference data <%"PRId64"> error code: %i", id, dc);
      *result = Tcl_NewADLB_ID(tmp.REF.id);
      break;
    case ADLB_DATA_TYPE_FLOAT:
      dc = ADLB_Unpack_float(&tmp.FLOAT, data, length);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
            "Retrieve failed due to error unpacking float data <%"PRId64"> error code: %i", id, dc);
      *result = Tcl_NewDoubleObj(tmp.FLOAT);
      break;
    case ADLB_DATA_TYPE_STRING:
      // Don't allocate new memory
      // Ok to cast away const since Tcl will copy string anyway
      // Length is limited by Tcl to INT_MAX
      dc = ADLB_Unpack_string(&tmp.STRING, (void*)data,
                              length, false);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
            "Retrieve failed due to error unpacking string data <%"PRId64"> length: %zi error code: %i", id, length, dc);
      *result = Tcl_NewStringObj(tmp.STRING.value,
                                 (int) (tmp.STRING.length-1));
      break;
    case ADLB_DATA_TYPE_BLOB:
      // Do allocate new memory
      // Ok to cast away const since we're copying blob
      dc = ADLB_Unpack_blob(&tmp.BLOB, (void*)data, length, true);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
                    "Retrieve failed due to error unpacking blob data <%"PRId64"> length: %zi error code: %i", id, length, dc);
      // Don't provide id to avoid blob caching
      *result = build_tcl_blob(tmp.BLOB.value, tmp.BLOB.length, NULL);
      break;
    case ADLB_DATA_TYPE_STRUCT:
      return packed_struct_to_tcl_dict(interp, objv, data, length,
                                       extra, result);
    case ADLB_DATA_TYPE_CONTAINER:
      return packed_container_to_dict(interp, objv, data, length, extra,
                                      result);
    case ADLB_DATA_TYPE_MULTISET:
      return packed_multiset_to_list(interp, objv, data, length, extra,
                                     result);
    default:
      *result = NULL;
      TCL_RETURN_ERROR("unsupported type: %s(%i)",
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

int
adlb_parse_strictness(Tcl_Interp *interp, Tcl_Obj *obj,
      adlb_target_strictness *strictness)
{
  char* s = Tcl_GetString(obj);
  if (strcmp(s, "HARD") == 0)
    *strictness = ADLB_TGT_STRICT_HARD;
  else if (strcmp(s, "SOFT") == 0)
    *strictness = ADLB_TGT_STRICT_SOFT;
  else
  {
    Tcl_Obj* msg = Tcl_ObjPrintf("invalid strictness value: %s", s);
    Tcl_Obj* msgs[1] = { msg };
    return turbine_user_error(interp, 1, msgs);
  }

  return TCL_OK;
}

int
adlb_parse_accuracy(Tcl_Interp *interp, Tcl_Obj *obj,
      adlb_target_accuracy *accuracy)
{
  char* s = Tcl_GetString(obj);
  if (strcmp(s, "RANK") == 0)
    *accuracy = ADLB_TGT_ACCRY_RANK;
  else if (strcmp(s, "NODE") == 0)
    *accuracy = ADLB_TGT_ACCRY_NODE;
  else
  {
    Tcl_Obj* msg = Tcl_ObjPrintf("invalid accuracy value: %s", s);
    Tcl_Obj* msgs[1] = { msg };
    return turbine_user_error(interp, 1, msgs);
  }

  return TCL_OK;
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
  return ADLB_Acquire_Ref_Impl(cdata, interp, objc, objv,
                               false, ADLB_SUB_NONE);
}

/**
   usage: adlb::acquire_write_ref <id> <type>
          <read increment> <write increment> <read decrement>
   Retrieve and increment read & write refcount of referenced ids by
      <write increment>.
   Decrement refcount of this id by <read decrement>
*/
static int
ADLB_Acquire_Write_Ref_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  return ADLB_Acquire_Ref_Impl(cdata, interp, objc, objv,
                               true, ADLB_SUB_NONE);
}

/**
   usage: adlb::acquire_sub_ref <id> <subscript> <type> <increment>
                                <decrement>
   Retrieve value at subscript and increment read refcount of referenced
   ids by increment.
   Decrement refcount of this id by decrement
*/
static int
ADLB_Acquire_Sub_Ref_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  return ADLB_Acquire_Ref_Impl(cdata, interp, objc, objv,
                               false, ADLB_SUB_CONTAINER);
}

/**
   usage: adlb::acquire_sub_write_ref <id> <subscript> <type>
          <read increment> <write increment> <read decrement>
   Retrieve value at subscript and increment read & write refcounts
   of referenced ids by increment.
   Decrement refcount of this id by decrement
*/
static int
ADLB_Acquire_Sub_Write_Ref_Cmd(ClientData cdata, Tcl_Interp *interp,
                  int objc, Tcl_Obj *const objv[])
{
  return ADLB_Acquire_Ref_Impl(cdata, interp, objc, objv,
                               true, ADLB_SUB_CONTAINER);
}

static int
ADLB_Acquire_Ref_Impl(ClientData cdata, Tcl_Interp *interp,
          int objc, Tcl_Obj *const objv[],
          bool write_ref, adlb_subscript_kind sub_kind)
{
  int expected_args = 5;
  if (sub_kind != ADLB_SUB_NONE) {
    expected_args++;
  }
  if (write_ref)
  {
    expected_args++;
  }

  TCL_ARGS(expected_args);
  int rc;

  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[1], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[1]));

  int argpos = 2;

  if (sub_kind != ADLB_SUB_NONE)
  {
    rc = ADLB_PARSE_SUB(objv[2], sub_kind, &handle.sub, true, true);
    TCL_CHECK_MSG(rc, "Invalid subscript argument %s",
                      Tcl_GetString(objv[2]));
    argpos = 3;
  }

  adlb_data_type expected_type;
  adlb_type_extra extra;
  rc = adlb_type_from_obj_extra(interp, objv, objv[argpos++],
                          &expected_type, &extra);
  TCL_CHECK(rc);

  adlb_retrieve_refc refcounts = ADLB_RETRIEVE_NO_REFC;
  rc = Tcl_GetIntFromObj(interp, objv[argpos++],
            &refcounts.incr_referand.read_refcount);
  TCL_CHECK_MSG(rc, "requires incr referand read amount!");

  if (write_ref) {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
              &refcounts.incr_referand.write_refcount);
    TCL_CHECK_MSG(rc, "requires incr referand write amount!");
  }

  rc = Tcl_GetIntFromObj(interp, objv[argpos++],
            &refcounts.decr_self.read_refcount);
  TCL_CHECK_MSG(rc, "requires decr amount!");

  // Retrieve the data, actual type, and length from server
  adlb_data_type type;
  size_t length;
  adlb_code ac = ADLB_Retrieve(handle.id, handle.sub.val, refcounts,
                               &type, xfer, &length);
  CHECK_ADLB_RETRIEVE(ac, handle);

  ADLB_PARSE_HANDLE_CLEANUP(&handle);

  // Type check
  if (expected_type != type)
  {
    report_type_mismatch(expected_type, type);
    return TCL_ERROR;
  }

  // Unpack from xfer to Tcl object
  Tcl_Obj* result;
  rc = adlb_datum2tclobj(interp, objv, handle.id, type, extra,
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
                      char* data, size_t length, int records,
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

  adlb_refc decr = ADLB_NO_REFC;
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

  TCL_CONDITION(argpos == objc, "unexpected trailing args at %ith arg",
                                argpos);

  // Set up call
  bool include_keys;
  bool include_vals;
  void *data = NULL;
  size_t data_length;
  int records;
  adlb_type_extra kv_type;
  rc = set_enumerate_params(interp, objv, token, &include_keys,
                            &include_vals);
  TCL_CHECK_MSG(rc, "unknown token %s!", token);


  // Call ADLB
  rc = ADLB_Enumerate(container_id, count, offset, decr,
                      include_keys, include_vals,
                      &data, &data_length, &records, &kv_type);
  TCL_CONDITION(rc == ADLB_SUCCESS, "ADLB enumerate call failed");

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
                      char* data, size_t length, int records,
                      adlb_type_extra kv_type, Tcl_Obj** result)
{
  int rc;
  adlb_data_code dc;
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
  size_t pos = 0;

  for (int i = 0; i < records; i++)
  {
    Tcl_Obj *key = NULL, *val = NULL;
    if (include_keys)
    {
      const void *key_data;
      size_t key_len; // Length including null terminator

      dc = ADLB_Unpack_buffer(ADLB_DATA_TYPE_NULL,
            data, length, &pos, &key_data, &key_len);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
          "Error unpacking key buffer for record %i/%i", i+1, records);

      // Key currently must be string
      key = Tcl_NewStringObj(key_data, (int)key_len - 1);
    }

    if (include_vals)
    {
      const void *val_data;
      size_t val_len = 0;

      dc = ADLB_Unpack_buffer(kv_type.CONTAINER.val_type,
            data, length, &pos, &val_data, &val_len);
      TCL_CONDITION(dc == ADLB_DATA_SUCCESS,
          "Error unpacking value buffer for record %i/%i", i+1, records);

      rc = adlb_datum2tclobj(interp, objv, id,
          kv_type.CONTAINER.val_type, ADLB_TYPE_EXTRA_NULL,
          val_data, val_len, &val);
      TCL_CHECK(rc);
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

/**
 * Construct cache key
 * Key may point to id or sub
 * @return Tcl error code
 */
static int blob_cache_key(Tcl_Interp *interp, Tcl_Obj *const objv[],
                          adlb_datum_id *id, adlb_subscript *sub,
                          void **key, size_t *key_len, bool *alloced)
{
  if (adlb_has_sub(*sub))
  {
    *key_len = sizeof(*id) + sub->length;
    *key = malloc(*key_len);
    TCL_MALLOC_CHECK(*key);
    *alloced = true;

    memcpy(*key, id, sizeof(*id));
    char* dest = (char*) *key;
    dest += sizeof(*id);
    memcpy(dest, sub->key, sub->length);
  }
  else
  {
    *key = id;
    *key_len = sizeof(*id);
    *alloced = false;
  }

  return TCL_OK;
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
  tcl_adlb_handle handle;
  Tcl_Obj *handle_obj = objv[1];
  rc = ADLB_PARSE_HANDLE(handle_obj, &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[1]));

  adlb_retrieve_refc refcounts = ADLB_RETRIEVE_NO_REFC;
  /* Only decrement if refcounting enabled */
  if  (decr) {
    rc = Tcl_GetIntFromObj(interp, objv[2],
                          &refcounts.decr_self.read_refcount);
    TCL_CHECK_MSG(rc, "requires id!");
  }

  // TODO: will need to avoid using xfer to support large blobs

  // Retrieve the blob data
  adlb_data_type type;
  size_t length;
  int ret_rc = ADLB_Retrieve(handle.id, handle.sub.val, refcounts,
                             &type, xfer, &length);
  CHECK_ADLB_RETRIEVE(ret_rc, handle);

  TCL_CONDITION(type == ADLB_DATA_TYPE_BLOB,
                "type mismatch: expected: %i actual: %i",
                ADLB_DATA_TYPE_BLOB, type);

  // Allocate the local blob
  void* blob = malloc(length);
  TCL_CONDITION(blob != NULL, "Error allocating blob: %zu bytes", length);

  // Copy the blob data
  memcpy(blob, xfer, (size_t)length);

  DEBUG_ADLB("ADD TO CACHE: {%s}\n", Tcl_GetString(handle_obj));
  rc = cache_blob(interp, objc, objv, handle.id, handle.sub.val, blob);
  TCL_CHECK(rc);

  // printf("retrieved blob: [ %p %i ]\n", blob, length);
  rc = ADLB_PARSE_HANDLE_CLEANUP(&handle);
  TCL_CHECK(rc);

  // build blob with original handle - ID or ID/sub
  Tcl_SetObjResult(interp, build_tcl_blob(blob, length, handle_obj));
  return TCL_OK;
}

// Return null on out of memory
static Tcl_Obj *build_tcl_blob(void *data, size_t length, Tcl_Obj *handle)
{
  // Pack and return the blob pointer, length, turbine ID as Tcl list
  int blob_elems = (handle == NULL) ? 2 : 3;

  Tcl_Obj* list[blob_elems];
  list[0] = Tcl_NewPtr(data);
  list[1] = Tcl_NewWideIntObj((Tcl_WideInt) length);
  if (list[0] == NULL || list[1] == NULL)
    return NULL;

  if (handle != NULL)
  {
    Tcl_IncrRefCount(handle);
    list[2] = handle;
    if (list[2] == NULL)
      return NULL;
  }
  return Tcl_NewListObj(blob_elems, list);
}

/*
  Construct a Tcl blob object, which has two representations:
   This handles two cases:
    -> A three element list representing a blob retrieved from the
       data store, in which case we fill in handle, if not NULL
    -> A two element list representing a locally allocated blob,
        in which case we set handle == NULL
 */

static int extract_tcl_blob(Tcl_Interp *interp, Tcl_Obj *const objv[],
                     Tcl_Obj *obj, adlb_blob_t *blob, Tcl_Obj **handle)
{
  int rc;
  Tcl_Obj **elems;
  int elem_count;
  rc = Tcl_ListObjGetElements(interp, obj, &elem_count, &elems);
  TCL_CONDITION(rc == TCL_OK && (elem_count == 2 || elem_count == 3),
                "Error interpreting %s as blob list", Tcl_GetString(obj));

  rc = Tcl_GetPtr(interp, elems[0], &blob->value);
  TCL_CHECK_MSG(rc, "Error extracting pointer from %s",
                Tcl_GetString(elems[0]));

  Tcl_WideInt length;
  rc = Tcl_GetWideIntFromObj(interp, elems[1], &length);
  blob->length = (size_t) length;
  TCL_CHECK_MSG(rc, "Error extracting blob length from %s",
                Tcl_GetString(elems[1]));
  if (elem_count == 2)
  {
    if (handle != NULL)
    {
      *handle = NULL;
    }
  }
  else
  {
    if (handle != NULL)
    {
      *handle = elems[2];
    }
  }
  return TCL_OK;
}

/**
 * Add blob to cache
 * blob: pointer to blob, to take ownership of
 */
static int cache_blob(Tcl_Interp *interp, int objc,
    Tcl_Obj *const objv[], adlb_datum_id id, adlb_subscript sub,
    void *blob)
{
  int rc;

  // Build key for the cache
  void *cache_key;
  size_t cache_key_len;
  bool free_cache_key;
  rc = blob_cache_key(interp, objv, &id, &sub, &cache_key,
                      &cache_key_len, &free_cache_key);
  TCL_CHECK(rc);

  // Link the blob into the cache
  bool b = table_bp_add(&blob_cache, cache_key, cache_key_len, blob);
  if (free_cache_key)
  {
    free(cache_key);
  }
  TCL_CONDITION(b, "Error adding to blob cache");

  return TCL_OK;
}

static int uncache_blob(Tcl_Interp *interp, int objc,
    Tcl_Obj *const objv[], adlb_datum_id id, adlb_subscript sub,
    bool *found_in_cache) {
  // Build key for the cache
  void *cache_key;
  size_t cache_key_len;
  bool free_cache_key;
  int rc = blob_cache_key(interp, objv, &id, &sub,
              &cache_key, &cache_key_len, &free_cache_key);
  TCL_CHECK(rc);
  void* blob;

  *found_in_cache = table_bp_remove(&blob_cache, cache_key,
                                    cache_key_len, &blob);
  if (*found_in_cache)
  {
    free(blob);
  }

  if (free_cache_key)
  {
    free(cache_key);
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
  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[1], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s",
                Tcl_GetString(objv[1]));

  bool found;
  DEBUG_ADLB("LOOKUP IN CACHE: {%s}\n", Tcl_GetString(objv[1]));
  rc = uncache_blob(interp, objc, objv, handle.id,
                    handle.sub.val, &found);
  TCL_CHECK(rc);

  if (adlb_has_sub(handle.sub.val))
  {
    TCL_CONDITION(found, "blob not cached: <%"PRId64">[%.*s]",
        handle.id, (int)handle.sub.val.length,
        (const char*)handle.sub.val.key);
  }
  else
  {
    TCL_CONDITION(found, "blob not cached: <%"PRId64">", handle.id);
  }

  rc = ADLB_PARSE_HANDLE_CLEANUP(&handle);
  TCL_CHECK(rc);

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

  Tcl_Obj *handle_obj;
  rc = extract_tcl_blob(interp, objv, objv[1], &blob, &handle_obj);
  TCL_CHECK(rc);

  if (handle_obj == NULL)
  {
    if (blob.value != NULL)
      free(blob.value);
    return TCL_OK;
  } else {
    //printf("uncache_blob: %s", Tcl_GetString(objv[1]));
    tcl_adlb_handle handle;
    rc = ADLB_PARSE_HANDLE(handle_obj, &handle, true);
    TCL_CHECK_MSG(rc, "Invalid handle %s",
                  Tcl_GetString(objv[1]));

    bool cached;
    rc = uncache_blob(interp, objc, objv, handle.id,
                      handle.sub.val, &cached);
    TCL_CHECK(rc);

    if (!cached && blob.value != NULL)
      // Wasn't managed by cache
      free(blob.value);

    rc = ADLB_PARSE_HANDLE_CLEANUP(&handle);
    TCL_CHECK(rc);
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
                "requires 4 or 5 args!");

  int rc;
  adlb_datum_id id;
  void* pointer;
  int length;
  rc = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK_MSG(rc, "requires id!");
  rc = Tcl_GetPtr(interp, objv[2], &pointer);
  TCL_CHECK_MSG(rc, "requires pointer!");

  // TODO: will need to change to wide int for big blobs
  rc = Tcl_GetIntFromObj(interp, objv[3], &length);
  TCL_CHECK_MSG(rc, "requires length!");

  adlb_refc decr = ADLB_WRITE_REFC;
  if (objc == 5) {
    rc = Tcl_GetIntFromObj(interp, objv[4], &decr.write_refcount);
    TCL_CHECK_MSG(rc, "decr must be int!");
  }

  rc = ADLB_Store(id, ADLB_NO_SUB, ADLB_DATA_TYPE_BLOB,
                  pointer, (size_t) length, decr, ADLB_NO_REFC);
  CHECK_ADLB_STORE(interp, objv, rc, id, ADLB_NO_SUB);

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
    TCL_CHECK(rc);
    memcpy(xfer+(size_t)i*sizeof(double), &v, sizeof(double));
  }

  adlb_refc decr = ADLB_WRITE_REFC;
  if (objc == 4) {
    rc = Tcl_GetIntFromObj(interp, objv[3], &decr.write_refcount);
    TCL_CHECK_MSG(rc, "decr must be int!");

  }
  rc = ADLB_Store(id, ADLB_NO_SUB, ADLB_DATA_TYPE_BLOB,
        xfer, (size_t)length*sizeof(double), decr, ADLB_NO_REFC);
  CHECK_ADLB_STORE(interp, objv, rc, id, ADLB_NO_SUB);

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
    // TODO: should we use 64-bit ints?
    int v;
    rc = Tcl_GetIntFromObj(interp, objs[i], &v);
    TCL_CHECK(rc);
    memcpy(xfer+(size_t)i*sizeof(int), &v, sizeof(int));
  }

  adlb_refc decr = ADLB_WRITE_REFC;
  if (objc == 4) {
    rc = Tcl_GetIntFromObj(interp, objv[3], &decr.write_refcount);
    TCL_CHECK_MSG(rc, "decr must be int!");

  }
  rc = ADLB_Store(id, ADLB_NO_SUB, ADLB_DATA_TYPE_BLOB,
                  xfer, (size_t)length*sizeof(int), decr, ADLB_NO_REFC);
  CHECK_ADLB_STORE(interp, objv, rc, id, ADLB_NO_SUB);

  return TCL_OK;
}

static int
ADLB_Blob_From_Int_List_Cmd(ClientData cdata, Tcl_Interp *interp,
                           int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 2, "Expected 1 arg");
  int rc;

  int length;
  Tcl_Obj** objs;
  rc = Tcl_ListObjGetElements(interp, objv[1], &length, &objs);
  TCL_CHECK_MSG(rc, "requires list!");
  assert(length >= 0);

  // TODO: should we use 64-bit ints?
  size_t blob_size = (size_t)length * sizeof(int);
  int *blob = malloc(blob_size);
  TCL_MALLOC_CHECK(blob);

  for (int i = 0; i < length; i++)
  {
    rc = Tcl_GetIntFromObj(interp, objs[i], &blob[i]);
    TCL_CHECK(rc);
  }

  Tcl_Obj *result = build_tcl_blob(blob, blob_size, NULL);
  TCL_MALLOC_CHECK(blob);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

static int
ADLB_Blob_From_Float_List_Cmd(ClientData cdata, Tcl_Interp *interp,
                           int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc == 2, "Expected 1 arg");
  int rc;

  int length;
  Tcl_Obj** objs;
  rc = Tcl_ListObjGetElements(interp, objv[1], &length, &objs);
  TCL_CHECK_MSG(rc, "requires list!");
  assert(length >= 0);

  size_t blob_size = (size_t)length * sizeof(double);
  double *blob = malloc(blob_size);
  TCL_MALLOC_CHECK(blob);

  for (int i = 0; i < length; i++)
  {
    rc = Tcl_GetDoubleFromObj(interp, objs[i], &blob[i]);
    TCL_CHECK(rc);
  }

  Tcl_Obj *result = build_tcl_blob(blob, blob_size, NULL);
  TCL_MALLOC_CHECK(blob);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   adlb::string2blob <string value> -> blob
 */
static int
ADLB_String2Blob_Cmd(ClientData cdata, Tcl_Interp *interp,
                           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  int length;
  char *data = Tcl_GetStringFromObj(objv[1], &length);
  assert(length >= 0);

  TCL_CONDITION(data != NULL, "failed!");
  int length2 = length+1;

  void *blob = malloc((size_t)length2 * sizeof(char));
  memcpy(blob, data, (size_t)length2);

  Tcl_Obj* list[2];
  list[0] = Tcl_NewPtr(blob);
  list[1] = Tcl_NewIntObj(length2);
  Tcl_Obj* result = Tcl_NewListObj(2, list);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   adlb::blob2string <blob value> -> string
   Convert null-terminated blob to string
 */
static int
ADLB_Blob2String_Cmd(ClientData cdata, Tcl_Interp *interp,
                           int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  adlb_blob_t blob;
  int rc = extract_tcl_blob(interp, objv, objv[1], &blob, NULL);
  TCL_CHECK(rc);

  TCL_CONDITION(blob.length < INT_MAX,
                "blob size must be smaller than INT_MAX");
  TCL_CONDITION(((char*)blob.value)[blob.length-1] == '\0',
                "blob must be null terminated");
  int length = (int) (blob.length-1);
  Tcl_SetObjResult(interp, Tcl_NewStringObj(blob.value, length));
  return TCL_OK;
}

static int
ADLB_Insert_Impl(ClientData cdata, Tcl_Interp *interp,
      int objc, Tcl_Obj *const objv[], adlb_subscript_kind sub_kind)
{
  TCL_CONDITION((objc >= 4),
                "requires at least 4 args!");
  int rc;

  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[1], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[1]));

  rc = ADLB_PARSE_SUB(objv[2], sub_kind, &handle.sub, true, true);
  TCL_CHECK_MSG(rc, "Invalid subscript argument %s",
                    Tcl_GetString(objv[2]));

  // Check for no subscript
  TCL_CONDITION(adlb_has_sub(handle.sub.val), "No subscript");

  int argpos = 3;
  Tcl_Obj *member_obj = objv[argpos++];

  adlb_data_type type;
  adlb_type_extra extra;
  rc = adlb_type_from_obj_extra(interp, objv, objv[argpos++], &type,
                           &extra);
  TCL_CHECK(rc);

  adlb_binary_data member;
  rc = adlb_tclobj2bin(interp, objv, type, extra,
                      member_obj, false, &xfer_buf, &member);

  TCL_CHECK_MSG(rc, "<%"PRId64">[%.*s] failed, could not "
        "extract data!", handle.id, (int)handle.sub.val.length,
        (const char*)handle.sub.val.key);

  DEBUG_ADLB("adlb::insert <%"PRId64">[\"%.*s\"]=<%s>",
     handle.id, (int)handle.sub.val.length,
     (const char*)handle.sub.val.key,
     Tcl_GetStringFromObj(member_obj, NULL));

  adlb_refc decr = ADLB_NO_REFC;
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

  TCL_CONDITION(argpos == objc, "trailing arguments after %i not "
                "consumed", argpos);


  adlb_refc store_rc = ADLB_READ_REFC;
  rc = ADLB_Store(handle.id, handle.sub.val, type,
                  member.data, member.length, decr, store_rc);

  CHECK_ADLB_STORE(interp, objv, rc, handle.id, handle.sub.val);

  // Free if needed
  if (member.data != xfer_buf.data)
    ADLB_Free_binary_data(&member);

  ADLB_PARSE_HANDLE_CLEANUP(&handle);
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
  return ADLB_Insert_Impl(cdata, interp, objc, objv, ADLB_SUB_CONTAINER);
}

/**
   usage: adlb::struct_insert <id> <subscript> <member>
                        <type> [<extra for type>]
                       [<write refcount decr>] [<read refcount decr>]
*/
static int
ADLB_Struct_Insert_Cmd(ClientData cdata, Tcl_Interp *interp,
                       int objc, Tcl_Obj *const objv[])
{
  return ADLB_Insert_Impl(cdata, interp, objc, objv, ADLB_SUB_STRUCT);
}

/**
   usage: adlb::insert_atomic <id> <subscript>
              [<caller read refs>] [<caller write refs>]
              [<outer write decrements>] [<outer read decrements>]
   returns: 0 if the id[subscript] already existed, else 1
*/
static int
ADLB_Insert_Atomic_Cmd(ClientData cdata, Tcl_Interp *interp,
                       int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 3, "Requires at least 3 args");
  int rc;
  bool b, value_present;

  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[1], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[1]));

  rc = ADLB_PARSE_SUB(objv[2], ADLB_SUB_CONTAINER, &handle.sub,
                      true, true);
  TCL_CHECK_MSG(rc, "Invalid subscript argument %s",
                    Tcl_GetString(objv[2]));

  int argpos = 3;

  // Increments/decrements for outer and inner containers
  // (default no extras)
  adlb_retrieve_refc refcounts = ADLB_RETRIEVE_NO_REFC;

  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                    &refcounts.incr_referand.read_refcount);
    TCL_CHECK(rc);
  }
  if (argpos < objc)
  {
    rc = Tcl_GetIntFromObj(interp, objv[argpos++],
                    &refcounts.incr_referand.write_refcount);
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
                           &refcounts.decr_self.read_refcount);
    TCL_CHECK(rc);
  }
  TCL_CONDITION(argpos == objc, "Trailing args starting at %i", argpos);


  DEBUG_ADLB("adlb::insert_atomic: <%"PRId64">[\"%.*s\"]",
             handle.id, (int)handle.sub.val.length,
             (const char*)handle.sub.val.key);
  rc = ADLB_Insert_atomic(handle.id, handle.sub.val, refcounts, &b,
                          &value_present, NULL, NULL, NULL);

  TCL_CONDITION(rc == ADLB_SUCCESS,
        "failed: <%"PRId64">[%.*s]", handle.id,
        (int)handle.sub.val.length, (const char*)handle.sub.val.key);

  ADLB_PARSE_HANDLE_CLEANUP(&handle);

  Tcl_Obj* result = Tcl_NewBooleanObj(b);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

static int
ADLB_Lookup_Impl(Tcl_Interp *interp, int objc, Tcl_Obj *const objv[],
                 adlb_subscript_kind sub_kind, bool spin)
{
  TCL_CONDITION(objc >= 3, "at least 2 arguments!");

  int rc;

  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[1], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[1]));

  rc = ADLB_PARSE_SUB(objv[2], sub_kind, &handle.sub, true, true);
  TCL_CHECK_MSG(rc, "Invalid subscript argument %s",
                    Tcl_GetString(objv[2]));
  // Check for no subscript
  TCL_CONDITION(adlb_has_sub(handle.sub.val), "No subscript");

  DEBUG_ADLB("adlb::lookup <%"PRId64">[\"%.*s\"]", handle.id,
      (int)handle.sub.val.length, (const char*)handle.sub.val.key);

  int argpos = 3;
  adlb_data_type type;
  size_t len;

  // Optional reference decrement argument, defaults to 0
  adlb_retrieve_refc refcounts = ADLB_RETRIEVE_NO_REFC;
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
    rc = ADLB_Retrieve(handle.id, handle.sub.val, refcounts, &type,
                      xfer, &len);
    if (rc != ADLB_SUCCESS && rc != ADLB_NOTHING) // Check outside loop
      break;
  } while (spin && rc == ADLB_NOTHING);

  CHECK_ADLB_RETRIEVE(rc, handle);

  assert(type != ADLB_DATA_TYPE_NULL);

  Tcl_Obj* result = NULL;
  rc = adlb_datum2tclobj(interp, objv, handle.id, type,
                    ADLB_TYPE_EXTRA_NULL, xfer, len, &result);
  TCL_CHECK(rc);

  DEBUG_ADLB("adlb::lookup <%"PRId64">[\"%.*s\"]=<%s>", handle.id,
        (int)handle.sub.val.length, (const char*)handle.sub.val.key,
        Tcl_GetStringFromObj(result, NULL));

  ADLB_PARSE_HANDLE_CLEANUP(&handle);

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   Lookup something in an ADLB container

   usage: adlb::lookup <id> <subscript>
        [<decr readers>] [<incr readers referand>]
        [<decr writers>] [<incr writers referand>]
   decr (readers|writers): decrement reference counts.  Default is zero.
   incr (readers|writers) referand: increment reference counts of referand
   returns the member
*/
static int
ADLB_Lookup_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return ADLB_Lookup_Impl(interp, objc, objv, ADLB_SUB_CONTAINER, false);
}

/**
  Lookup something in an ADLB struct
   usage: adlb::struct_lookup <id> <subscript>
        [<decr readers>] [<incr readers referand>]
        [<decr writers>] [<incr writers referand>]
   subscript: integer, or list of integers for struct indices
   decr (readers|writers): decrement reference counts.  Default is zero.
   incr (readers|writers) referand: increment reference counts of referand
   returns the member
*/
static int
ADLB_Struct_Lookup_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
    return ADLB_Lookup_Impl(interp, objc, objv, ADLB_SUB_STRUCT, false);
}

/**
   usage: adlb::lookup_spin <id> <subscript>
        [<decr readers>] [<decr writers>]
        [<incr readers referand>] [<incr writers referand>]
   decr (readers|writers): decrement reference counts.  Default is zero.
   incr (readers|writers) referand: increment reference counts of referand
   returns the member
*/
static int
ADLB_Lookup_Spin_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
    return ADLB_Lookup_Impl(interp, objc, objv, ADLB_SUB_CONTAINER, true);
}

/**
  usage: adlb::subscribe <handle> <work type>
  returns: 1 if subscribed, 0 if already exists
 */
static int
ADLB_Subscribe_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(3);
  int rc;

  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[1], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[1]));

  int work_type;
  rc = Tcl_GetIntFromObj(interp, objv[2], &work_type);
  TCL_CHECK_MSG(rc, "requires integer work type!");

  int subscribed;
  rc = ADLB_Subscribe(handle.id, handle.sub.val, work_type,
                          &subscribed);

  TCL_CONDITION(rc == ADLB_SUCCESS, "<%"PRId64">[%.*s] failed!",
        handle.id, (int)handle.sub.val.length, (char*)handle.sub.val.key);

  rc = ADLB_PARSE_HANDLE_CLEANUP(&handle);
  TCL_CHECK(rc);

  Tcl_SetObjResult(interp, Tcl_NewIntObj(subscribed));
  return TCL_OK;
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
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%"PRId64"> failed!", id);

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
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%"PRId64"> failed!", id);

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

  // DEBUG_ADLB("adlb::unique: <%"PRId64">", id);

  Tcl_Obj* result = Tcl_NewADLB_ID(id);
  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
   usage: adlb::typeof <id>
*/
static int
ADLB_Typeof_Cmd(ClientData cdata, Tcl_Interp *interp,
		int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);
  int rc;
  adlb_datum_id id;
  rc = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK(rc);

  adlb_data_type type;
  rc = ADLB_Typeof(id, &type);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%"PRId64"> failed!", id);

  // DEBUG_ADLB("adlb::typeof: <%"PRId64"> is: %i\n", id, type);

  const char* type_string = ADLB_Data_type_tostring(type);

  // DEBUG_ADLB("adlb::typeof: <%"PRId64"> is: %s",
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
  int rc;

  rc = Tcl_GetADLB_ID(interp, objv[1], &id);
  TCL_CHECK(rc);

  adlb_data_type key_type, val_type;
  rc = ADLB_Container_typeof(id, &key_type, &val_type);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%"PRId64"> failed!", id);

  const char *key_type_string = ADLB_Data_type_tostring(val_type);
  const char *val_type_string = ADLB_Data_type_tostring(key_type);

  Tcl_Obj* types[2] = {Tcl_NewStringObj(key_type_string, -1),
                       Tcl_NewStringObj(val_type_string, -1)};

  Tcl_Obj* result = Tcl_NewListObj(2, types);
  Tcl_SetObjResult(interp, result);

  return TCL_OK;
}

static int
ADLB_Reference_Impl(ClientData cdata, Tcl_Interp *interp,
                             int objc, Tcl_Obj *const objv[],
                             adlb_subscript_kind sub_kind);

/**
   usage: adlb::container_reference
      <container_id> <subscript> <reference> <reference_type>
      [<reference write decr> [<read refcount transfer>
      [<write refcount transfer>]]]

      reference_type is type of container field
      e.g. ref for plain turbine IDs
*/
static int
ADLB_Container_Reference_Cmd(ClientData cdata, Tcl_Interp *interp,
                             int objc, Tcl_Obj *const objv[])
{
  return ADLB_Reference_Impl(cdata, interp, objc, objv,
                             ADLB_SUB_CONTAINER);
}

/**
   usage: adlb::struct_reference
      <struct_id> <subscript> <reference> <reference_type>
      [<reference write decr> [<read refcount transfer>
      [<write refcount transfer>]]]

      subscript is a list of indices into struct
      reference_type is type of container field
      e.g. ref for plain turbine IDs
*/
static int
ADLB_Struct_Reference_Cmd(ClientData cdata, Tcl_Interp *interp,
                             int objc, Tcl_Obj *const objv[])
{
  return ADLB_Reference_Impl(cdata, interp, objc, objv,
                             ADLB_SUB_STRUCT);
}

static int
ADLB_Create_Nested_Impl(ClientData cdata, Tcl_Interp *interp,
    int objc, Tcl_Obj *const objv[], adlb_data_type type,
    adlb_subscript_kind sub_kind)
{
  TCL_CONDITION(objc >= 4, "Requires at least 3 args");

  int rc;
  int argpos = 1;
  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[argpos++], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[1]));

  rc = ADLB_PARSE_SUB(objv[argpos++], sub_kind, &handle.sub, true, true);
  TCL_CHECK_MSG(rc, "Invalid subscript argument %s",
                    Tcl_GetString(objv[2]));

  // Increments/decrements for outer and inner containers
  // (default no extras)
  adlb_retrieve_refc refcounts = ADLB_RETRIEVE_NO_REFC;

  char* xfer;
  adlb_code ac;

  // Check for no subscript
  TCL_CONDITION_GOTO(adlb_has_sub(handle.sub.val), exit_err,
                    "No subscript");

  adlb_type_extra type_extra;
  if (type == ADLB_DATA_TYPE_NULL) {
    // Get full type info from arg list
    rc = adlb_type_from_array(interp, objv, objv, objc, &argpos, &type,
                              &type_extra);
    TCL_CHECK(rc);
  } else {
    rc = adlb_type_extra_from_array(interp, objv, objv, objc, &argpos,
                              type, &type_extra);
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
                    &refcounts.incr_referand.write_refcount);
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
                           &refcounts.decr_self.read_refcount);
    TCL_CHECK(rc);
  }

  TCL_CONDITION(argpos == objc, "Trailing args starting at %i", argpos);

  log_printf("creating nested %s <%"PRId64">[%.*s]",
    ADLB_Data_type_tostring(type),
    handle.id, (int)handle.sub.val.length, handle.sub.val.key);

  uint64_t xfer_size;
  xfer = tcl_adlb_xfer_buffer(&xfer_size);

  bool created, value_present;
  size_t value_len;
  adlb_data_type outer_value_type;

  // Initial trial at inserting.
  // Refcounts are only applied here if we got back the data
  ac = ADLB_Insert_atomic(handle.id, handle.sub.val,
            refcounts, &created, &value_present, xfer,
            &value_len, &outer_value_type);

  if (ac != ADLB_SUCCESS)
  {
    /*
     * Attempt to provide more informative message about cause of
     * failure.  A specific error can be that we tried to autocreate
     * when there was a (read-only) reference to another array
     * inserted manually.
     * Retry without refcount acquisition.
     */
    ac = ADLB_Retrieve(handle.id, handle.sub.val, ADLB_RETRIEVE_NO_REFC,
              &outer_value_type, xfer, &value_len);
    TCL_CONDITION(ac == ADLB_SUCCESS,
        "unexpected error while retrieving container value");

    adlb_ref retrieved;
    adlb_data_code dc = ADLB_Unpack_ref(&retrieved, xfer, value_len,
                              ADLB_NO_REFC, false);
    TCL_CONDITION_GOTO(dc == ADLB_DATA_SUCCESS, exit_err,
        "malformed reference buffer "
        "of length %zu received from ADLB server", value_len);

    if (retrieved.write_refs <= 0)
    {
      TCL_ERROR_GOTO(exit_err, "Attempted to automatically create datum "
            "at <%"PRId64">[\"%.*s\"], which was already set to "
            "a read-only reference to <%"PRId64">", handle.id,
            (int)handle.sub.val.length, (const char*)handle.sub.val.key,
            retrieved.id);
    }

    TCL_RETURN_ERROR("Unexpected error in "
      "Insert_atomic when attempting to automatically create datum at"
      "<%"PRId64">[\"%.*s\"]", handle.id, (int)handle.sub.val.length,
      (const char*)handle.sub.val.key);
  }

  if (created)
  {
    // Need to create container and insert

    /*
     * Initial refcounts for container passed to caller
     * We set to a fairly high number since this lets us give refcounts
     * from outer container to callers without also touching inner
     * datum.  Remainder will be freed all at once when outer container
     * is closed/garbage collected.
     */
    int init_count = (2 << 24);
    adlb_refc init_refs = { .read_refcount = init_count,
                                 .write_refcount = init_count };
    adlb_create_props props = DEFAULT_CREATE_PROPS;

    props.release_write_refs = turbine_release_write_rc_policy(type);

    props.read_refcount = init_refs.read_refcount +
                          refcounts.incr_referand.read_refcount;
    props.write_refcount = init_refs.write_refcount +
                           refcounts.incr_referand.write_refcount;

    adlb_datum_id new_id;
    ac = ADLB_Create(ADLB_DATA_ID_NULL, type, type_extra, props,
                       &new_id);
    TCL_CONDITION_GOTO(ac == ADLB_SUCCESS, exit_err,
                       "Error while creating nested");

    // ID is only relevant data, so init refcounts to any value
    adlb_ref new_ref = { .id = new_id, .read_refs = 0,
                         .write_refs = 0 };

    // Pack using standard api.  Checks should be mostly optimized out
    adlb_binary_data packed;
    adlb_data_code dc = ADLB_Pack_ref(&new_ref, &packed);
    TCL_CONDITION_GOTO(dc == ADLB_DATA_SUCCESS, exit_err,
                       "Error packing ref");


    // Store and apply remaining refcounts
    ac = ADLB_Store(handle.id, handle.sub.val, ADLB_DATA_TYPE_REF,
                      packed.data,
                      packed.length, refcounts.decr_self, init_refs);
    TCL_CONDITION_GOTO(ac == ADLB_SUCCESS, exit_err,
                      "Error while inserting nested");

    ADLB_Free_binary_data(&packed);

    // Return the ID of the new container
    Tcl_SetObjResult(interp, Tcl_NewADLB_ID(new_id));
  }
  else
  {
    // Wasn't able to create.  Entry may or may not already have value.
    while (!value_present)
    {
      // Need to poll until value exists
      // This will decrement reference counts if it succeeds
      ac = ADLB_Retrieve(handle.id, handle.sub.val, refcounts,
                           &outer_value_type, xfer, &value_len);

      // Unknown cause
      TCL_CONDITION_GOTO(ac == ADLB_SUCCESS || ac == ADLB_NOTHING,
            exit_err, "unexpected error while retrieving container value");
      value_present = (ac == ADLB_SUCCESS);
    }
    TCL_CONDITION_GOTO(outer_value_type == ADLB_DATA_TYPE_REF, exit_err,
            "only works on containers with values of type ref");

    Tcl_Obj* result = NULL;
    adlb_datum2tclobj(interp, objv, handle.id, ADLB_DATA_TYPE_REF,
            ADLB_TYPE_EXTRA_NULL, xfer, value_len, &result);
    Tcl_SetObjResult(interp, result);
  }

  rc = TCL_OK;
  goto cleanup;

exit_err:
  rc = TCL_ERROR;

cleanup:
  ADLB_PARSE_HANDLE_CLEANUP(&handle);
  return rc;
}

/*
  adlb::create_nested <id> <subscript> <type> [<extra for type> ]
              [<caller read refs>] [<caller write refs>]
              [<outer write decrements>] [<outer read decrements>]
   Create a nested datum at subscript of id.
   id: id of a container to create nested datum in
   caller * refs: how many reference counts to give back to caller
 */
static int
ADLB_Create_Nested_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return ADLB_Create_Nested_Impl(cdata, interp, objc, objv,
                      ADLB_DATA_TYPE_NULL, ADLB_SUB_CONTAINER);
}

/*
  adlb::create_nested_container <id> <subscript> <key_type> <val_type>
              [<caller read refs>] [<caller write refs>]
              [<outer write decrements>] [<outer read decrements>]
   Create a nested container at subscript of id.
   id: id of a container to create nested datum in
   caller * refs: how many reference counts to give back to caller
 */
static int
ADLB_Create_Nested_Container_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return ADLB_Create_Nested_Impl(cdata, interp, objc, objv,
                      ADLB_DATA_TYPE_CONTAINER, ADLB_SUB_CONTAINER);
}

/*
  adlb::create_nested_bag <id> <subscript> <val_type>
              [<caller read refs>] [<caller write refs>]
              [<outer write decrements>] [<outer read decrements>]
   Create a nested bag at subscript of id.
   id: id of a container to create nested datum in
   caller * refs: how many reference counts to give back to caller
 */
static int
ADLB_Create_Nested_Bag_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return ADLB_Create_Nested_Impl(cdata, interp, objc, objv,
                      ADLB_DATA_TYPE_MULTISET, ADLB_SUB_CONTAINER);
}

/*
  adlb::struct_create_nested <id> <subscript> <type> [<extra for type> ]
              [<caller read refs>] [<caller write refs>]
              [<outer write decrements>] [<outer read decrements>]
   Create a nested datum at subscript of id.
   id: id of a struct to create nested datum in
   caller * refs: how many reference counts to give back to caller
 */
static int
ADLB_Struct_Create_Nested_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return ADLB_Create_Nested_Impl(cdata, interp, objc, objv,
                      ADLB_DATA_TYPE_NULL, ADLB_SUB_STRUCT);
}

/*
  adlb::struct_create_nested_container <id> <subscript> <key_type> <val_type>
              [<caller read refs>] [<caller write refs>]
              [<outer write decrements>] [<outer read decrements>]
   Create a nested container at subscript of id.
   id: id of a struct to create nested datum in
   caller * refs: how many reference counts to give back to caller
 */
static int
ADLB_Struct_Create_Nested_Container_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return ADLB_Create_Nested_Impl(cdata, interp, objc, objv,
                      ADLB_DATA_TYPE_CONTAINER, ADLB_SUB_STRUCT);
}

/*
  adlb::struct_create_nested_bag <id> <subscript> <val_type>
              [<caller read refs>] [<caller write refs>]
              [<outer write decrements>] [<outer read decrements>]
   Create a nested bag at subscript of id.
   id: id of a struct to create nested datum in
   caller * refs: how many reference counts to give back to caller
 */
static int
ADLB_Struct_Create_Nested_Bag_Cmd(ClientData cdata, Tcl_Interp *interp,
                int objc, Tcl_Obj *const objv[])
{
  return ADLB_Create_Nested_Impl(cdata, interp, objc, objv,
                      ADLB_DATA_TYPE_MULTISET, ADLB_SUB_STRUCT);
}

// container_reference, supporting different subscript formats
static int
ADLB_Reference_Impl(ClientData cdata, Tcl_Interp *interp,
                             int objc, Tcl_Obj *const objv[],
                             adlb_subscript_kind sub_kind)
{
  TCL_CONDITION(objc >= 5 && objc <= 8, "requires 4-7 arguments!");

  int rc;
  tcl_adlb_handle handle;
  rc = ADLB_PARSE_HANDLE(objv[1], &handle, true);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[1]));

  rc = ADLB_PARSE_SUB(objv[2], sub_kind, &handle.sub, true, true);
  TCL_CHECK_MSG(rc, "Invalid subscript %s", Tcl_GetString(objv[2]));
  // Check for no subscript
  TCL_CONDITION(adlb_has_sub(handle.sub.val), "Invalid subscript "
                "argument");

  tcl_adlb_handle ref_handle;
  rc = ADLB_PARSE_HANDLE(objv[3], &ref_handle, false);
  TCL_CHECK_MSG(rc, "Invalid handle %s", Tcl_GetString(objv[3]));

  adlb_data_type ref_type;
  adlb_type_extra extra;
  // ignores extra type info
  rc = adlb_type_from_obj_extra(interp, objv, objv[4], &ref_type,
                           &extra);
  TCL_CHECK(rc);

  // optionally write decrement
  int ref_write_decr = 1;

  if (objc >= 6)
  {
    rc = Tcl_GetIntFromObj(interp, objv[5], &ref_write_decr);
    TCL_CHECK(rc);
  }

  // optionally take num of read/write references to transfer
  adlb_refc transfer_rc = ADLB_READ_REFC;

  if (objc >= 7)
  {
    rc = Tcl_GetIntFromObj(interp, objv[6], &transfer_rc.read_refcount);
    TCL_CHECK(rc);
  }

  if (objc >= 8)
  {
    rc = Tcl_GetIntFromObj(interp, objv[7], &transfer_rc.write_refcount);
    TCL_CHECK(rc);
  }

  rc = ADLB_Container_reference(handle.id, handle.sub.val,
              ref_handle.id, ref_handle.sub.val, ref_type,
              transfer_rc, ref_write_decr);

  ADLB_PARSE_HANDLE_CLEANUP(&handle);
  ADLB_PARSE_HANDLE_CLEANUP(&ref_handle);

  TCL_CONDITION(rc == ADLB_SUCCESS, "<%"PRId64"> failed!", handle.id);
  return TCL_OK;
}

/**
   usage: adlb::container_size <container_id> [ <read decr> ]
                               [ <write decr> ]
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

  adlb_refc decr = ADLB_NO_REFC;
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

  TCL_CONDITION(argpos == objc, "unexpected trailing args at %ith arg",
                argpos);

  int size;
  // DEBUG_ADLB("adlb::container_size: <%"PRId64">",
  //            container_id, size);
  rc = ADLB_Container_size(container_id, &size, decr);
  TCL_CONDITION(rc == ADLB_SUCCESS, "<%"PRId64"> failed!",
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
  int rc;
  adlb_datum_id container_id;
  rc = ADLB_EXTRACT_HANDLE_ID(objv[1], &container_id);
  TCL_CHECK(rc);

  adlb_refc incr = ADLB_WRITE_REFC;
  if (objc == 3)
  {
    rc = Tcl_GetIntFromObj(interp, objv[2], &incr.write_refcount);
    TCL_CHECK_MSG(rc, "Error extracting reference count");
  }

  // DEBUG_ADLB("adlb::write_refcount_incr: <%"PRId64">", container_id);
  rc = ADLB_Refcount_incr(container_id, incr);

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
  int rc;
  adlb_datum_id container_id;
  rc = Tcl_GetADLB_ID(interp, objv[1], &container_id);
  TCL_CHECK(rc);

  int decr_w = 1;
  if (objc == 3)
  {
    rc = Tcl_GetIntFromObj(interp, objv[2], &decr_w);
    TCL_CHECK_MSG(rc, "Error extracting reference count");
  }

  // DEBUG_ADLB("adlb::write_refcount_decr: <%"PRId64">", container_id);
  adlb_refc decr = { .read_refcount = 0, .write_refcount = -decr_w };
  rc = ADLB_Refcount_incr(container_id, decr);

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
  rc = ADLB_EXTRACT_HANDLE_ID(var, &id);
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

 // DEBUG_ADLB("adlb::refcount_incr: <%"PRId64">", id);

  adlb_refc incr = ADLB_NO_REFC;
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

static int
ADLB_Xpt_Enabled_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  int result;
#ifdef ENABLE_XPT
  result = 1;
#else
  result = 0;
#endif
  Tcl_SetObjResult(interp, Tcl_NewIntObj(result));
  return TCL_OK;
}

/**
  Usage: adlb::xpt_init <filename> <flush policy> <max index val size>
  filename: the filename of the checkpoint file.  If empty string,
            checkpointing to file not initialized
  flush policy: no_flush, periodic_flush, or always_flush
  max index val size: maximum size of value to store in index
 */
static int
ADLB_Xpt_Init_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(4);

#ifdef ENABLE_XPT
  const char *filename = Tcl_GetString(objv[1]);
  if (strlen(filename) == 0) {
    filename = NULL; // ADLB interface takes null instead of empty string
  }
  const char *flush_policy_s = Tcl_GetString(objv[2]);
  adlb_xpt_flush_policy flush_policy;
  if (strcmp(flush_policy_s, "no_flush"))
  {
    flush_policy = ADLB_NO_FLUSH;
  }
  else if (strcmp(flush_policy_s, "periodic_flush"))
  {
    flush_policy = ADLB_PERIODIC_FLUSH;
  }
  else if (strcmp(flush_policy_s, "always_flush"))
  {

    flush_policy = ADLB_ALWAYS_FLUSH;
  }
  else
  {
    TCL_RETURN_ERROR("Invalid flush policy: %s", flush_policy_s);
  }

  int max_index_val;
  int rc = Tcl_GetIntFromObj(interp, objv[3], &max_index_val);
  TCL_CHECK(rc);

  adlb_code ac = ADLB_Xpt_init(filename, flush_policy, max_index_val);
  TCL_CONDITION(ac == ADLB_SUCCESS,
                "Error while initializing checkpointing");
  return TCL_OK;
#else
  TCL_RETURN_ERROR("Checkpointing not enabled in Turbine build");
  return TCL_ERROR;
#endif
}

/**
  Usage: adlb::xpt_finalize
 */
static int
ADLB_Xpt_Finalize_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
#ifdef ENABLE_XPT
  TCL_ARGS(1);
  adlb_code ac = ADLB_Xpt_finalize();
  TCL_CONDITION(ac == ADLB_SUCCESS, "Error while finalizing "
                                    "checkpointing");
  return TCL_OK;
#else
  return TCL_OK;
#endif
}

/**
  usage: adlb::xpt_write <key blob> <val blob> <persist mode> <index add>
  persist mode: no_persist, persist, or persist_flush: whether/how to
                persist to file
  index add: int interpreted as boolean: whether to add to index
 */
static int
ADLB_Xpt_Write_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
#ifdef ENABLE_XPT
  TCL_ARGS(5);
  int rc;
  adlb_code ac;

  adlb_blob_t key_blob, val_blob;
  rc = extract_tcl_blob(interp, objv, objv[1], &key_blob, NULL);
  TCL_CHECK(rc);

  rc = extract_tcl_blob(interp, objv, objv[2], &val_blob, NULL);
  TCL_CHECK(rc);

  adlb_xpt_persist persist_mode;
  const char *persist_mode_s = Tcl_GetString(objv[3]);
  if (strcmp(persist_mode_s, "no_persist") == 0)
  {
    persist_mode = ADLB_NO_PERSIST;
  }
  else if (strcmp(persist_mode_s, "persist") == 0)
  {
    persist_mode = ADLB_PERSIST;
  }
  else if (strcmp(persist_mode_s, "persist_flush") == 0)
  {
    persist_mode = ADLB_PERSIST_FLUSH;
  }
  else
  {
    TCL_RETURN_ERROR("Invalid persist mode: %s", persist_mode_s);
  }

  int index_add_i;
  rc = Tcl_GetBooleanFromObj(interp, objv[4], &index_add_i);
  TCL_CHECK(rc);
  bool index_add = (index_add_i != 0);

  ac = ADLB_Xpt_write(key_blob.value, key_blob.length, val_blob.value,
                      val_blob.length, persist_mode, index_add);
  TCL_CONDITION(ac == ADLB_SUCCESS, "Error writing checkpoint");
  return TCL_OK;
#else
  TCL_RETURN_ERROR("Checkpointing not enabled in Turbine build");
  return TCL_ERROR;
#endif
}

/**
  usage: adlb::xpt_lookup <checkpoint key> [ <checkpoint value> ]
  return value: bool indicating whether checkpoint exists
  checkpoint value: name of variable for packed value of checkpoint
                    as blob
  checkpoint key: packed checkpoint key as blob
 */
static int
ADLB_Xpt_Lookup_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
#ifdef ENABLE_XPT
  TCL_CONDITION(objc == 2 || objc == 3, "Must provide 1 or 2 arguments" );
  int rc;
  adlb_code ac;

  adlb_blob_t key;
  rc = extract_tcl_blob(interp, objv, objv[1], &key, NULL);
  TCL_CHECK(rc);

  adlb_binary_data val;
  ac = ADLB_Xpt_lookup(key.value, key.length, &val);
  TCL_CONDITION(ac == ADLB_SUCCESS || ac == ADLB_NOTHING,
                "Error looking up checkpoint");
  bool found = (ac == ADLB_SUCCESS);

  bool outArgProvided = (objc > 2);

  if (found && outArgProvided)
  {
    // put into Tcl blob and put in variable caller requested
    ADLB_Own_data(NULL, &val); // Make sure we own memory
    Tcl_Obj *tclVal = build_tcl_blob(val.caller_data, val.length,
                                     NULL);
    TCL_CONDITION(tclVal != NULL, "Error building blob");
    tclVal = Tcl_ObjSetVar2(interp, objv[2], NULL,
                           tclVal, EMPTY_FLAG);
    TCL_CONDITION(tclVal != NULL, "Error setting output argument %s",
                  Tcl_GetString(objv[2]));

  }
  else if (found)
  {
    // Not returning, so free memory
    ADLB_Free_binary_data(&val);
  }

  Tcl_SetObjResult(interp, Tcl_NewIntObj(found));
  return TCL_OK;
#else
  TCL_RETURN_ERROR("Checkpointing not enabled in Turbine build");
  return TCL_ERROR;
#endif
}


/*
   Pack a TCL container value represented as a TCL dict or array.
   Handles nesting
   Consturct compound type from Tcl arguments .
   argpos: updated to consume multiple type names from command line
 */
static int
get_compound_type(Tcl_Interp *interp, int objc, Tcl_Obj *const objv[],
                int *argpos, compound_type *types)
{
  int rc;

  /* slurp up relevant data types: get all nested containers plus the
   * value type.
   */
  size_t types_size = 16;
  int len = 0;
  adlb_data_type *type_arr = malloc(sizeof(adlb_data_type) * types_size);
  TCL_CONDITION(type_arr != NULL, "Error allocating memory");

  int to_consume = 1; // Min additional number that must be consumed
  adlb_type_extra* extras = malloc(sizeof(adlb_type_extra) * types_size);
  TCL_CONDITION_GOTO(extras != NULL, exit_err, "Error allocating memory");

  // Must consume at least the outermost type
  while (to_consume > 0) {
    TCL_CONDITION_GOTO(*argpos < objc, exit_err,
                       "Consumed past end of arguments");

    if (types_size <= len)
    {
      types_size *= 2;
      type_arr = realloc(type_arr, sizeof(adlb_data_type) * types_size);
      TCL_CONDITION_GOTO(type_arr != NULL, exit_err,
                        "Error allocating memory");

      extras = realloc(extras, sizeof(adlb_type_extra) * types_size);
      TCL_CONDITION_GOTO(extras != NULL, exit_err,
                        "Error allocating memory");
    }

    adlb_data_type curr;
    adlb_type_extra extra;
    rc = adlb_type_from_obj_extra(interp, objv, objv[*argpos], &curr,
                             &extra);
    TCL_CHECK_GOTO(rc, exit_err);

    type_arr[len] = curr;

    if (extra.valid)
    {
      extras[len] = extra;
    }
    else
    {
      extras[len] = ADLB_TYPE_EXTRA_NULL;
    }

    // Make sure we consume more types
    switch (curr)
    {
      case ADLB_DATA_TYPE_CONTAINER:
        assert(to_consume == 1);
        to_consume = 2; // Key and val
        break;
      case ADLB_DATA_TYPE_MULTISET:
        assert(to_consume == 1);
        to_consume = 1; // Val
        break;
      default:
        to_consume--;
        break;
    }

    len++;
    (*argpos)++;
  }

  types->types = type_arr;
  types->extras = extras;
  types->len = len;
  return TCL_OK;

exit_err:
  if (type_arr != NULL)
  {
    free(type_arr);
  }
  if (extras != NULL)
  {
    free(extras);
  }
  return TCL_ERROR;
}

/**
  usage: adlb::xpt_pack (<type> <value>)*
 */
static int
ADLB_Xpt_Pack_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  int rc;
  adlb_data_code dc;

  adlb_buffer packed;
  size_t pos = 0;
  bool using_caller_buf;
  // Caller blob needs to own data, so don't provide a static buffer
  dc = ADLB_Init_buf(NULL, &packed, &using_caller_buf, 2048);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error initializing buffer");

  int argpos = 1;
  int field = 0;
  while (argpos < objc)
  {
    // We might need to pack compound types
    compound_type compound_type;
    rc = get_compound_type(interp, objc, objv, &argpos,
                                 &compound_type);
    TCL_CHECK(rc);

    TCL_CONDITION(argpos < objc,
                  "Last argument missing value");
    Tcl_Obj *val = objv[argpos++];

    DEBUG_ADLB("Packing entry #%i type %s @ byte %zi", field,
                  ADLB_Data_type_tostring(compound_type.types[0]), pos);

    // pack incrementally into buffer
    int ctype_pos = 0;
    rc = adlb_tclobj_bin_append(interp, objv, compound_type, ctype_pos,
            val, true, true, &packed, &using_caller_buf, &pos);
    TCL_CHECK(rc);

    free_compound_type(&compound_type);
    field++;
  }

  Tcl_Obj *packedBlob = build_tcl_blob(packed.data, pos, NULL);
  Tcl_SetObjResult(interp, packedBlob);
  return TCL_OK;
}

/**
  usage: adlb::xpt_unpack (<var name>)* <packed data> (<var type>)
  packed data: tcl blob format
  var type: ADLB types for the packed fields
  The number of var names and types must match
 */
static int
ADLB_Xpt_Unpack_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
  TCL_CONDITION(objc >= 2, "Must have at least 1 arg");
  TCL_CONDITION(objc % 2 == 0, "Must have paired var names and types");
  int rc;
  int fieldCount = (objc - 2) / 2;

  adlb_blob_t packed;
  rc = extract_tcl_blob(interp, objv, objv[fieldCount + 1], &packed,
                        NULL);
  TCL_CHECK(rc);

  size_t packed_pos = 0;

  for (int field = 0; field < fieldCount; field++)
  {
    Tcl_Obj *varName = objv[field + 1];
    Tcl_Obj *typeO = objv[field + fieldCount + 2];

    // Get type of object
    adlb_data_type type;
    adlb_type_extra extra;
    rc = adlb_type_from_obj_extra(interp, objv, typeO, &type, &extra);
    TCL_CHECK(rc);

    // Unpack next entry from buffer
    const void *entry;
    size_t entry_length;
    adlb_data_code dc =
        ADLB_Unpack_buffer(type, packed.value, packed.length,
                           &packed_pos, &entry, &entry_length);
    TCL_CONDITION(dc != ADLB_DATA_DONE, "Hit end of buffer after "
                    "unpacking %i/%i fields", field, fieldCount);
    TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error unpacking field %i "
            "from buffer", field);

    DEBUG_ADLB("Unpacking entry #%i type %s @ byte %zi from blob %p "
                "[%zi bytes] entry: offset %li [%zi bytes]", field,
                ADLB_Data_type_tostring(type), packed_pos, packed.value,
                packed.length, entry - packed.value, entry_length);

    Tcl_Obj *obj;
    rc = adlb_datum2tclobj(interp, objv, ADLB_DATA_ID_NULL,
          type, extra, entry, entry_length, &obj);
    TCL_CHECK(rc);

    // Store result into location caller requested
    obj = Tcl_ObjSetVar2(interp, varName, NULL, obj, EMPTY_FLAG);
    TCL_CONDITION(obj != NULL, "error setting field %s",
                  Tcl_GetString(varName));
  }
  return TCL_OK;
}

/**
  usage: adlb::xpt_reload <checkpoint file name> <loader rank>
                          <total loaders>
  loader rank/total loads: total count of loaders, and this processes
          rank within them, used to divide up work
  returns: statistics about reload.  Dict with "ranks" containing total
            number of ranks.  An entry is added for each rank that was
            reloaded (no entry present if not loaded by this process).
            Each rank entry is a dict with statistics: valid and
            invalid counts.
 */
static int
ADLB_Xpt_Reload_Cmd(ClientData cdata, Tcl_Interp *interp,
                   int objc, Tcl_Obj *const objv[])
{
#ifdef ENABLE_XPT
  TCL_ARGS(4);
  const char *filename = Tcl_GetString(objv[1]);
  int rc;

  int loader_rank;
  int total_loaders;
  rc = Tcl_GetIntFromObj(interp, objv[2], &loader_rank);
  TCL_CHECK(rc);
  TCL_CONDITION(loader_rank >= 0, "loader rank must be non-negative: %i",
                loader_rank);

  rc = Tcl_GetIntFromObj(interp, objv[3], &total_loaders);
  TCL_CHECK(rc);
  TCL_CONDITION(total_loaders > 0, "Must be at least one loader: %i",
                total_loaders);

  adlb_code ac;
  adlb_xpt_load_stats stats;
  ac = ADLB_Xpt_reload(filename, &stats, loader_rank, total_loaders);
  TCL_CONDITION(ac == ADLB_SUCCESS, "Error reloading checkpoint from "
                                                  "file %s", filename);

  Tcl_Obj *stat_dict = Tcl_NewDictObj();

  Tcl_DictObjPut(interp, stat_dict, Tcl_NewStringObj("ranks", -1),
                                     Tcl_NewWideIntObj(stats.ranks));

  Tcl_Obj *valid_key = Tcl_NewStringObj("valid", -1);
  Tcl_Obj *invalid_key = Tcl_NewStringObj("invalid", -1);
  for (int i = 0; i < stats.ranks; i++)
  {
    adlb_xpt_load_rank_stats *rstats = &stats.rank_stats[i];
    if (rstats->loaded)
    {
      Tcl_Obj *rank_dict = Tcl_NewDictObj();
      Tcl_DictObjPut(interp, stat_dict, Tcl_NewIntObj(i), rank_dict);
      Tcl_DictObjPut(interp, rank_dict, valid_key,
                     Tcl_NewIntObj(rstats->valid));
      Tcl_DictObjPut(interp, rank_dict, invalid_key,
                     Tcl_NewIntObj(rstats->invalid));
    }
  }

  free(stats.rank_stats);

  Tcl_SetObjResult(interp, stat_dict);
  return TCL_OK;
#else
  TCL_RETURN_ERROR("Checkpointing not enabled in Turbine build");
  return TCL_ERROR;
#endif
}

/**
   Same as builtin dict create except don't allow duplicates.
   usage: adlb::dict_create key1 val1 key2 val2 ...
 */
static int
ADLB_Dict_Create_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  Tcl_Obj *dict = Tcl_NewDictObj();

  TCL_CONDITION(objc % 2 == 1, "Must have even number of args "
      "(matching keys and values): got odd number %i", objc - 1);

  int rc;

  for (int i = 1; i < objc; i += 2)
  {
    Tcl_Obj *key = objv[i];
    Tcl_Obj *val = objv[i + 1];
    if (i != 1)
    {
      // CHeck for duplicates
      Tcl_Obj *old_val;
      rc = Tcl_DictObjGet(interp, dict, key, &old_val);
      TCL_CHECK(rc);

      TCL_CONDITION(old_val == NULL, "Tried to create dictionary with "
            "duplicate values \"%s\" and \"%s\" for key \"%s\"",
            Tcl_GetString(old_val), Tcl_GetString(val),
            Tcl_GetString(key));
    }

    rc = Tcl_DictObjPut(interp, dict, key, val);
    TCL_CHECK(rc);
  }
  Tcl_SetObjResult(interp, dict);
  return TCL_OK;
}

/**
 * Handle input of forms:
 * - 124 (plain ID) => 124 & no subscript
 * - 1234.123.424.53 (id + struct indices - . separated)
 *    => id=1234 subscript="123.424.53" (not counting null terminator)
 */
int
ADLB_Extract_Handle(Tcl_Interp *interp, Tcl_Obj *const objv[],
        Tcl_Obj *obj, adlb_datum_id *id, const char **subscript,
        size_t *subscript_len)
{
  int rc;
  // Leave interp NULL so we don't get error message there
  rc = Tcl_GetADLB_ID(NULL, obj, id);
  if (rc == TCL_OK)
  {
    *subscript = NULL;
    *subscript_len = 0;
    return TCL_OK;
  }

  int tmp_len;
  const char *str_handle = Tcl_GetStringFromObj(obj, &tmp_len);
  size_t str_handle_len = (size_t) tmp_len;
  TCL_CONDITION(str_handle != NULL, "Error getting string handle");

  // Separate ID from remainder of subscript
  const char *sep = memchr(str_handle, '.', str_handle_len);
  TCL_CONDITION(sep != NULL, "Invalid ADLB handle %s", str_handle);

  size_t prefix_len = (size_t) (sep - str_handle);

  adlb_data_code dc;
  dc = ADLB_Int64_parse(str_handle, prefix_len, id);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Expected first element "
        "in handle to be valid ADLB ID: %s", str_handle);

  // Return subscript
  *subscript = (const char *) sep + 1; // Move past '.'
  // String length of remainder
  *subscript_len = str_handle_len - prefix_len - 1;

  return TCL_OK;
}

int
ADLB_Extract_Handle_ID(Tcl_Interp *interp, Tcl_Obj *const objv[],
        Tcl_Obj *obj, adlb_datum_id *id)
{
  const char *subscript;
  size_t subscript_len;
  return ADLB_Extract_Handle(interp, objv, obj, id, &subscript,
                             &subscript_len);
}


int
ADLB_Parse_Subscript(Tcl_Interp *interp, Tcl_Obj *const objv[],
  Tcl_Obj *obj, adlb_subscript_kind sub_kind, tcl_adlb_sub_parse *parse,
  bool append, bool use_scratch)
{
  int rc;
  if (sub_kind == ADLB_SUB_CONTAINER)
  {
    if (!append || parse->val.length == 0)
    {
      rc = Tcl_GetADLB_Subscript(obj, &parse->val);
      TCL_CHECK(rc);
      parse->buf.data = NULL;
      parse->buf.length = 0;
    }
    else
    {
      adlb_subscript tmp_sub;
      rc = Tcl_GetADLB_Subscript(obj, &tmp_sub);
      TCL_CHECK(rc);

      rc = append_subscript(interp, objv, &parse->val, tmp_sub,
                            &parse->buf);
      TCL_CHECK(rc);
    }
  }
  else
  {
    assert(sub_kind == ADLB_SUB_STRUCT);
    int tmp_len;
    char *subscript = Tcl_GetStringFromObj(obj, &tmp_len);
    size_t subscript_len = (size_t) tmp_len;
    TCL_CONDITION(subscript != NULL, "Could not extract string for "
                  "subscript");
    if (subscript_len == 0)
    {
      if (!append)
      {
        parse->val = ADLB_NO_SUB;
        // Ensure buffer initialized
        parse->buf.data = NULL;
        parse->buf.length = 0;
      }
    }
    else
    {
      if (!append)
      {
        // Initialize buffer
        if (use_scratch)
        {
          parse->buf = tcl_adlb_scratch_buf;
        }
        else
        {
          parse->buf.data = NULL;
          parse->buf.length = 0;
        }
      }

      bool using_scratch = (parse->buf.data == tcl_adlb_scratch);

      rc = PARSE_STRUCT_SUB(subscript, subscript_len, &parse->buf,
                            &parse->val, &using_scratch, append);
      TCL_CHECK(rc);
    }
  }
  return TCL_OK;
}

int
ADLB_Parse_Subscript_Cleanup(Tcl_Interp *interp, Tcl_Obj *const objv[],
                             tcl_adlb_sub_parse *parse)
{
  // If we're using tcl_adlb_scratch, free it
  free_non_scratch(parse->buf);
  return TCL_OK;
}


/**
 * Append a subscript to an existing one
 * Assume that buf is either malloced buffer, or the
 * scratch buffer
 */
static int append_subscript(Tcl_Interp *interp,
      Tcl_Obj *const objv[], adlb_subscript *sub,
      adlb_subscript to_append, adlb_buffer *buf)
{
  bool using_scratch = (buf->data == tcl_adlb_scratch);

  // resize buffer to fit new and old subscript
  adlb_data_code dc = ADLB_Resize_buf(buf, &using_scratch,
                          sub->length + to_append.length);
  TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error resizing");

  if (sub->length > 0)
  {
    if (buf->data != sub->key)
    {
      // if not in buffer, copy old subscript to buffer
      memcpy(buf->data, sub->key, sub->length);
    }
    // overwrite null terminator with '.'
    buf->data[sub->length - 1] = '.';
  }

  // append the new subscript
  memcpy(&buf->data[sub->length], to_append.key, to_append.length);

  sub->key = buf->data;
  sub->length += to_append.length;
  return TCL_OK;
}

/**
 * Parse a Tcl ADLB subscript into a binary ADLB subscript
 * str: string containing Tcl subscript
 * length: remaining length of string
 * adlb_subscript_kind: kind of leading subscript (might be prefix of
 *                      different subscript)
 * buf: buffer to use/return data.  Should be initialized by caller,
 *      optionally with storage that can be used. Initial size
 *      indicates size of buffer given by caller.
 *      Upon return, pointer will be updated if memory allocated in here.
 * TODO: this currently works for some array subscripts too..
 * TODO: but it breaks for e.g. general string subscripts
 * using_caller_buf: if true, storage is owned by caller and shouldn't be
 *                   freed
 * append: if true, append to existing subscript
 */
static int ADLB_Parse_Struct_Subscript(Tcl_Interp *interp,
  Tcl_Obj *const objv[],
  const char *str, size_t length, adlb_buffer *buf, adlb_subscript *sub,
  bool *using_caller_buf, bool append)
{
  adlb_data_code dc;
  /*
   * Let's assume struct subscript, which is a '.'-separated list of
   * integer indices, for now, since this is main use case.
   * ADLB representation is '.'-separated list of text integers,
   * null-terminated.  Since we currently use almost the same
   * representation, just copy it over and ensure it's null terminated.
   * We'll leave validation for the ADLB server
   */

  if (append && sub->length > 0)
  {
    dc = ADLB_Resize_buf(buf, using_caller_buf,
                         sub->length + length + 1);
    TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error expanding buf");

    if (buf->data != sub->key)
    {
      memcpy(buf->data, sub->key, sub->length);
    }

    buf->data[sub->length-1] = '.'; // Replace null terminator

    memcpy(&buf->data[sub->length], str, length);
    buf->data[length] = '\0';

    sub->length += length + 1; // Length includes terminator;
    sub->key = buf->data;
  }
  else
  {
    dc = ADLB_Resize_buf(buf, using_caller_buf, length + 1);
    TCL_CONDITION(dc == ADLB_DATA_SUCCESS, "Error expanding buf");

    memcpy(buf->data, str, length);
    buf->data[length] = '\0';

    sub->length = length + 1; // Length includes terminator;
    sub->key = buf->data;
  }

  return TCL_OK;
}

int
ADLB_Parse_Handle(Tcl_Interp *interp, Tcl_Obj *const objv[],
        Tcl_Obj *obj, tcl_adlb_handle *parse, bool use_scratch)
{
  int rc;
  const char *subscript;
  size_t subscript_len;
  rc = ADLB_EXTRACT_HANDLE(obj, &parse->id, &subscript, &subscript_len);
  TCL_CHECK(rc);

  if (subscript == NULL)
  {
    parse->sub.val = ADLB_NO_SUB;
    // Ensure buffer initialized
    parse->sub.buf.data = NULL;
    parse->sub.buf.length = 0;
  }
  else
  {
    if (use_scratch)
    {
      parse->sub.buf = tcl_adlb_scratch_buf;
    }
    else
    {
      parse->sub.buf.data = NULL;
      parse->sub.buf.length = 0;
    }

    // TODO: container subscripts?

    bool using_scratch = use_scratch;
    rc = PARSE_STRUCT_SUB(subscript, subscript_len,
                        &parse->sub.buf, &parse->sub.val,
                        &using_scratch, false);
    TCL_CHECK(rc);
  }

  return TCL_OK;
}

int
ADLB_Parse_Handle_Cleanup(Tcl_Interp *interp, Tcl_Obj *const objv[],
                          tcl_adlb_handle *parse)
{
  // If we're using tcl_adlb_scratch, free it
  free_non_scratch(parse->sub.buf);
  return TCL_OK;
}

static int
ADLB_Subscript_Impl(ClientData cdata, Tcl_Interp *interp,
     int objc, Tcl_Obj *const objv[], adlb_subscript_kind sub_kind)
{
  TCL_CONDITION(objc >= 2, "Must have at least one argument");

  int rc;
  int old_handle_len;
  char *old_handle = Tcl_GetStringFromObj(objv[1], &old_handle_len);
  assert(old_handle != NULL);

  int subscripts = objc - 2;

  if (sub_kind == ADLB_SUB_CONTAINER)
  {
    TCL_CONDITION(subscripts <= 1, "Only support one level of"
                                   "subscripting for container");
  }
  else
  {
    // Only support two kinds
    assert(sub_kind == ADLB_SUB_STRUCT);
  }

  int new_handle_len = old_handle_len;
  for (int i = 0; i < subscripts; i++)
  {
    int sub_len;
    char *sub = Tcl_GetStringFromObj(objv[i + 2], &sub_len);
    assert(sub != NULL);
    new_handle_len += sub_len + 1;  // subscript plus "." separator
  }

  Tcl_Obj *result = Tcl_NewObj();
  TCL_MALLOC_CHECK(result);

  rc = Tcl_AttemptSetObjLength(result, new_handle_len);
  // TCL_AttemptSetObjLength doesn't use standard Tcl return codes
  TCL_CONDITION(rc == 1, "Error setting object length");

  // Copy in subscripts to object
  char *result_ptr = result->bytes;
  assert(result_ptr != NULL);
  memcpy(result_ptr, old_handle, (size_t)old_handle_len);
  result_ptr += old_handle_len;

  for (int i = 0; i < subscripts; i++)
  {
    int sub_len;
    char *sub = Tcl_GetStringFromObj(objv[i + 2], &sub_len);
    assert(sub != NULL);

    // subscript plus "." separator
    *result_ptr = '.';
    result_ptr++;

    memcpy(result_ptr, sub, (size_t)sub_len);
    result_ptr += sub_len;
  }

  Tcl_SetObjResult(interp, result);
  return TCL_OK;
}

/**
  Build a handle for an id + subscript into a struct.

  adlb::subscript_struct <handle> [<subscript>]*
  handle: either an id, or a handle built by this function
  subscript: a valid subscript into a struct
 */
static int
ADLB_Subscript_Struct_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  return ADLB_Subscript_Impl(cdata, interp, objc, objv, ADLB_SUB_STRUCT);
}

static int
ADLB_Subscript_Container_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  return ADLB_Subscript_Impl(cdata, interp, objc, objv,
                             ADLB_SUB_CONTAINER);
}

/**
   usage: adlb::add_debug_symbol <symbol> <name> <context>
   symbol: integer debug symbol
   name: name associated with debug symbol
   context: additional context string
 */
static int
ADLB_Add_Debug_Symbol_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(4);

  int rc;
  int symbol;
  rc = Tcl_GetIntFromObj(interp, objv[1], &symbol);
  TCL_CHECK_MSG(rc, "symbol must be integer");
  TCL_CONDITION(symbol >= 0, "Symbol must be non-negative");

  const char *name = Tcl_GetString(objv[2]);
  const char *context = Tcl_GetString(objv[3]);
  adlb_dsym_data data = { .name = name, .context = context };

  adlb_code ac = ADLB_Add_dsym((uint32_t)symbol, data);
  TCL_CONDITION(ac == ADLB_SUCCESS, "Error adding debug symbol");

  return TCL_OK;
}

/**
   usage: adlb::debug_symbol <symbol>
   symbol: integer debug symbol
   returns: two element list with two strings for name and context,
            empty strings if none
 */
static int
ADLB_Debug_Symbol_Cmd(ClientData cdata, Tcl_Interp *interp,
               int objc, Tcl_Obj *const objv[])
{
  TCL_ARGS(2);

  int rc;
  int symbol;
  rc = Tcl_GetIntFromObj(interp, objv[1], &symbol);
  TCL_CHECK_MSG(rc, "symbol must be integer");
  TCL_CONDITION(symbol >= 0, "Symbol must be non-negative");

  adlb_dsym_data data = ADLB_Dsym((uint32_t)symbol);
  const char *name = data.name == NULL ? "" : data.name;
  const char *context = data.context == NULL ? "" : data.context;

  Tcl_Obj *result_items[2];
  result_items[0] = Tcl_NewStringObj(name, -1);
  result_items[1] = Tcl_NewStringObj(context, -1);

  Tcl_SetObjResult(interp, Tcl_NewListObj(2, result_items));

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
  int rc;

  // Finalize field objs before ADLB struct type stuff cleared up
  rc = field_name_objs_finalize(interp, objv);
  TCL_CHECK(rc);

  log_printf("ADLB_Finalize_Cmd() start");

  rc = ADLB_Finalize();
  if (rc != ADLB_SUCCESS)
    printf("WARNING: ADLB_Finalize() failed!\n");
  TCL_ARGS(2);
  int b;
  Tcl_GetBooleanFromObj(interp, objv[1], &b);

  if (must_comm_free)
    MPI_Comm_free(&adlb_comm);

  adlb_comm_init = false;
  adlb_init = false;

  log_printf("MPI_Finalize start");
  if (b)
    MPI_Finalize();
  log_printf("MPI_Finalize stop");
  turbine_debug_finalize();

  rc = blob_cache_finalize();
  TCL_CHECK(rc);

  log_printf("ADLB_Finalize_Cmd() stop");

  return TCL_OK;
}

static void blob_free_callback(const void *key, size_t key_len,
                               void *blob)
{
  free(blob);
}

static int blob_cache_finalize(void)
{
  // Free table structure and any contained blobs
  table_bp_free_callback(&blob_cache, false, blob_free_callback);
  return TCL_OK;
}

/**
   Shorten object creation lines.  "adlb::" namespace is prepended
 */
#define ADLB_NAMESPACE "adlb::"
#define COMMAND(tcl_function, c_function) \
    Tcl_CreateObjCommand(interp, ADLB_NAMESPACE tcl_function, \
                         c_function, NULL, NULL);

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
  COMMAND("init_comm", ADLB_Init_Comm_Cmd);
  COMMAND("init",      ADLB_Init_Cmd);
  COMMAND("declare_struct_type", ADLB_Declare_Struct_Type_Cmd);
  COMMAND("is_struct_type", ADLB_Is_Struct_Type_Cmd);
  COMMAND("server",    ADLB_Server_Cmd);
  COMMAND("comm_rank", ADLB_CommRank_Cmd);
  COMMAND("comm_size", ADLB_CommSize_Cmd);
  COMMAND("comm_dup",  ADLB_CommDup_Cmd);
  COMMAND("comm_get",  ADLB_CommGet_Cmd);
  COMMAND("barrier",   ADLB_Barrier_Cmd);
  COMMAND("worker_barrier", ADLB_Worker_Barrier_Cmd);
  COMMAND("worker_rank", ADLB_Worker_Rank_Cmd);
  COMMAND("amserver",  ADLB_AmServer_Cmd);
  // COMMAND("size",      ADLB_Size_Cmd);
  COMMAND("servers",   ADLB_Servers_Cmd);
  COMMAND("workers",   ADLB_Workers_Cmd);
  COMMAND("hostmap_lookup",   ADLB_Hostmap_Lookup_Cmd);
  COMMAND("hostmap_list",     ADLB_Hostmap_List_Cmd);
  COMMAND("get_priority",   ADLB_Get_Priority_Cmd);
  COMMAND("reset_priority", ADLB_Reset_Priority_Cmd);
  COMMAND("set_priority",   ADLB_Set_Priority_Cmd);
  COMMAND("leaders",   ADLB_Leaders_Cmd);
  COMMAND("put",       ADLB_Put_Cmd);
  COMMAND("spawn",     ADLB_Spawn_Cmd);
  COMMAND("get",       ADLB_Get_Cmd);
  COMMAND("iget",      ADLB_Iget_Cmd);
  COMMAND("create",    ADLB_Create_Cmd);
  COMMAND("multicreate",ADLB_Multicreate_Cmd);
  COMMAND("create_globals",ADLB_Create_Globals_Cmd);
  COMMAND("locate",    ADLB_Locate_Cmd);
  COMMAND("exists",    ADLB_Exists_Cmd);
  COMMAND("exists_sub", ADLB_Exists_Sub_Cmd);
  COMMAND("closed", ADLB_Closed_Cmd);
  COMMAND("store",     ADLB_Store_Cmd);
  COMMAND("retrieve",  ADLB_Retrieve_Cmd);
  COMMAND("retrieve_decr",  ADLB_Retrieve_Decr_Cmd);
  COMMAND("acquire_ref",  ADLB_Acquire_Ref_Cmd);
  COMMAND("acquire_write_ref",  ADLB_Acquire_Write_Ref_Cmd);
  COMMAND("acquire_sub_ref",  ADLB_Acquire_Sub_Ref_Cmd);
  COMMAND("acquire_sub_write_ref",  ADLB_Acquire_Sub_Write_Ref_Cmd);
  COMMAND("enumerate", ADLB_Enumerate_Cmd);
  COMMAND("retrieve_blob", ADLB_Retrieve_Blob_Cmd);
  COMMAND("retrieve_decr_blob", ADLB_Retrieve_Blob_Decr_Cmd);
  COMMAND("blob_free",  ADLB_Blob_Free_Cmd);
  COMMAND("local_blob_free",  ADLB_Local_Blob_Free_Cmd);
  COMMAND("store_blob", ADLB_Store_Blob_Cmd);
  COMMAND("store_blob_floats", ADLB_Blob_store_floats_Cmd);
  COMMAND("store_blob_ints", ADLB_Blob_store_ints_Cmd);
  COMMAND("blob_from_float_list", ADLB_Blob_From_Float_List_Cmd);
  COMMAND("blob_from_int_list", ADLB_Blob_From_Int_List_Cmd);
  COMMAND("string2blob", ADLB_String2Blob_Cmd);
  COMMAND("blob2string", ADLB_Blob2String_Cmd);
  COMMAND("enable_read_refcount",  ADLB_Enable_Read_Refcount_Cmd);
  COMMAND("refcount_incr", ADLB_Refcount_Incr_Cmd);
  COMMAND("read_refcount_incr", ADLB_Read_Refcount_Incr_Cmd);
  COMMAND("read_refcount_decr", ADLB_Read_Refcount_Decr_Cmd);
  COMMAND("write_refcount_incr", ADLB_Write_Refcount_Incr_Cmd);
  COMMAND("write_refcount_decr", ADLB_Write_Refcount_Decr_Cmd);
  COMMAND("insert",    ADLB_Insert_Cmd);
  COMMAND("struct_insert",    ADLB_Struct_Insert_Cmd);
  COMMAND("insert_atomic", ADLB_Insert_Atomic_Cmd);
  COMMAND("lookup",    ADLB_Lookup_Cmd);
  COMMAND("struct_lookup",    ADLB_Struct_Lookup_Cmd);
  COMMAND("lookup_spin", ADLB_Lookup_Spin_Cmd);
  COMMAND("subscribe",  ADLB_Subscribe_Cmd);
  COMMAND("lock",      ADLB_Lock_Cmd);
  COMMAND("unlock",    ADLB_Unlock_Cmd);
  COMMAND("unique",    ADLB_Unique_Cmd);
  COMMAND("typeof",    ADLB_Typeof_Cmd);
  COMMAND("container_typeof",    ADLB_Container_Typeof_Cmd);
  COMMAND("container_reference", ADLB_Container_Reference_Cmd);
  COMMAND("container_size",      ADLB_Container_Size_Cmd);
  COMMAND("struct_reference", ADLB_Struct_Reference_Cmd);
  COMMAND("create_nested", ADLB_Create_Nested_Cmd);
  COMMAND("create_nested_container", ADLB_Create_Nested_Container_Cmd);
  COMMAND("create_nested_bag", ADLB_Create_Nested_Bag_Cmd);
  COMMAND("struct_create_nested", ADLB_Struct_Create_Nested_Cmd);
  COMMAND("struct_create_nested_container",
                        ADLB_Struct_Create_Nested_Container_Cmd);
  COMMAND("struct_create_nested_bag", ADLB_Struct_Create_Nested_Bag_Cmd);
  COMMAND("xpt_enabled", ADLB_Xpt_Enabled_Cmd);
  COMMAND("xpt_init", ADLB_Xpt_Init_Cmd);
  COMMAND("xpt_finalize", ADLB_Xpt_Finalize_Cmd);
  COMMAND("xpt_write", ADLB_Xpt_Write_Cmd);
  COMMAND("xpt_lookup", ADLB_Xpt_Lookup_Cmd);
  COMMAND("xpt_pack", ADLB_Xpt_Pack_Cmd);
  COMMAND("xpt_unpack", ADLB_Xpt_Unpack_Cmd);
  COMMAND("xpt_reload", ADLB_Xpt_Reload_Cmd);
  COMMAND("dict_create", ADLB_Dict_Create_Cmd);
  COMMAND("subscript_struct", ADLB_Subscript_Struct_Cmd);
  COMMAND("subscript_container", ADLB_Subscript_Container_Cmd);
  COMMAND("add_debug_symbol", ADLB_Add_Debug_Symbol_Cmd);
  COMMAND("debug_symbol", ADLB_Debug_Symbol_Cmd);
  COMMAND("fail",      ADLB_Fail_Cmd);
  COMMAND("abort",     ADLB_Abort_Cmd);
  COMMAND("finalize",  ADLB_Finalize_Cmd);

  // Export all commands
  Tcl_Namespace *ns = Tcl_FindNamespace(interp,
          ADLB_NAMESPACE, NULL, TCL_GLOBAL_ONLY);
  Tcl_Export(interp, ns, "*", true);
}
