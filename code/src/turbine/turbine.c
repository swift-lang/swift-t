
/**
 * turbine.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 *
 * TD means Turbine Datum, which is a variable id stored in ADLB
 * */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>

#include <adlb.h>

#include <c-utils.h>
#include <list.h>
#include <table.h>
#include <table_lp.h>
#include <tools.h>
// #include <exm-string.h>

#include "src/util/debug.h"

#include "src/turbine/turbine.h"

typedef enum
{
  /** Waiting for inputs */
  TRANSFORM_WAITING,
  /** Inputs ready */
  TRANSFORM_READY,
  /** Application level has started the action */
  TRANSFORM_RUNNING,
  /** Application level has completed the action */
  TRANSFORM_DONE
} transform_status;

/**
   In-memory structure resulting from Turbine rule statement
 */
typedef struct
{
  turbine_transform_id id;
  /** Name for human debugging */
  char* name;
  /** Tcl string to evaluate when inputs are ready */
  char* action;
  /** Number of inputs */
  int inputs;
  /** Array of input TDs */
  turbine_datum_id* input_list;
  turbine_action_type action_type;
  /** Index of next subscribed input (starts at 0) */
  int blocker;
  transform_status status;
} transform;

/**
   Has turbine_init() been called successfully?
*/
static bool initialized = false;

/**
   Waiting transforms
   Map from transform id to transform
 */
struct table_lp transforms_waiting;

/**
   Ready transforms
 */
struct list transforms_ready;

/**
   Running transforms
   Map from transform ID to transform
 */
struct table_lp transforms_running;

/**
   TD inputs blocking their transforms
   Map from TD ID to list of transforms
 */
struct table_lp td_blockers;

#define turbine_check(code) if (code != TURBINE_SUCCESS) return code;

#define turbine_check_verbose(code) \
    turbine_check_verbose_impl(code, __FILE__, __LINE__)

#define turbine_check_verbose_impl(code, file, line)    \
  { if (code != TURBINE_SUCCESS)                        \
    {                                                   \
      char output[64];                                  \
      turbine_code_tostring(output, code);              \
      printf("turbine error: %s\n", output);            \
      printf("\t at: %s:%i\n", file, line);             \
      return code;                                      \
    }                                                   \
  }

/**
   Globally unique transform ID for rule_new().
   Starts at mpi_rank, incremented by mpi_size, thus unique
 */
static long transform_unique_id = -1;

static int mpi_size = -1;

#define turbine_condition(condition, code, format, args...) \
  { if (! (condition))                                      \
    {                                                       \
       printf(format, ## args);                             \
       return code;                                         \
    }}

static void
check_versions()
{
  version tv, av, rav, cuv, rcuv;
  turbine_version(&tv);
  ADLB_Version(&av);
  // Required ADLB version:
  version_parse(&rav, "0.0.2");
  c_utils_version(&cuv);
  // Required c-utils version:
  version_parse(&rcuv, "0.0.2");
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

turbine_code
turbine_init(int amserver, int rank, int size)
{
  check_versions();

  gdb_check(rank);

  if (amserver)
    return TURBINE_SUCCESS;

  mpi_size = size;
  transform_unique_id = rank+mpi_size;

  bool result;
  result = table_lp_init(&transforms_waiting, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;

  list_init(&transforms_ready);
  result = table_lp_init(&transforms_running, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;
  result = table_lp_init(&td_blockers, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;
  initialized = true;
  return TURBINE_SUCCESS;
}

void turbine_version(version* output)
{
#ifndef TURBINE_VERSION
#error TURBINE_VERSION must be set by the build system!
#endif
  version_parse(output, TURBINE_VERSION);
}

static inline long
make_unique_id()
{
  long result = transform_unique_id;
  transform_unique_id += mpi_size;
  return result;
}

static inline turbine_code
transform_create(const char* name,
                 int inputs, const turbine_datum_id* input_list,
                 turbine_action_type action_type,
                 const char* action,
                 transform** result)
{
  assert(name);
  assert(action);

  transform* T = malloc(sizeof(transform));

  T->id = make_unique_id();
  T->name = strdup(name);
  T->action_type = action_type;
  T->action = strdup(action);
  T->blocker = 0;

  if (inputs > 0)
  {
    T->input_list =
        malloc(inputs*sizeof(turbine_datum_id));
    if (! T->input_list)
      return TURBINE_ERROR_OOM;
  }
  else
    T->input_list = NULL;

  T->inputs = inputs;
  for (int i = 0; i < inputs; i++)
    T->input_list[i] = input_list[i];

  T->status = TRANSFORM_WAITING;

  *result = T;
  return TURBINE_SUCCESS;
}

static inline void
transform_free(transform* T)
{
  free(T->name);
  free(T->action);
  if (T->input_list)
    free(T->input_list);
  free(T);
}

static inline void
subscribe(turbine_transform_id id, int* result)
{
  ADLB_Subscribe(id, result);
}

static int transform_tostring(char* output,
                              transform* transform);

#ifdef ENABLE_DEBUG_TURBINE
#define DEBUG_TURBINE_RULE(transform, id) {         \
    char tmp[1024];                                     \
    transform_tostring(tmp, transform);                 \
    DEBUG_TURBINE("rule: %s {%li}", tmp, id);     \
  }
#else
#define DEBUG_TURBINE_RULE_ADD(transform, id)
#endif

static bool progress(transform* T);
static void rule_inputs(transform* T);

turbine_code
turbine_rule(const char* name,
             int inputs, const turbine_datum_id* input_list,
             turbine_action_type action_type,
             const char* action,
             turbine_transform_id* id)
{
  transform* T = NULL;
  turbine_code code = transform_create(name, inputs, input_list,
                                       action_type, action, &T);
  *id = T->id;
  DEBUG_TURBINE_RULE(T, *id);
  turbine_check(code);

  rule_inputs(T);

  bool subscribed = progress(T);

  if (subscribed)
  {
    table_lp_add(&transforms_waiting, *id, T);
  }
  else
  {
    list_add(&transforms_ready, T);
  }

  return TURBINE_SUCCESS;
}

turbine_code declare_datum(turbine_datum_id id,
                           struct list_l** result);

/**
   Record that this transform is blocked by its inputs
*/
static void
rule_inputs(transform* T)
{
  for (int i = 0; i < T->inputs; i++)
  {
    turbine_datum_id id = T->input_list[i];
    struct list_l* L = table_lp_search(&td_blockers, id);
    if (L == NULL)
      declare_datum(id, &L);
    list_l_add(L, T->id);
  }
}

/**
   Remove the transforms from waiting and add to ready list
   Empties given list along the way
 */
static void
add_to_ready(struct list* tmp)
{
  transform* T;
  while ((T = list_poll(tmp)))
  {
    void* c = table_lp_remove(&transforms_waiting, T->id);
    assert(c);
    list_add(&transforms_ready, T);
  }
}

/**
   Push transforms that are ready into trs_ready
   @return
*/
turbine_code
turbine_rules_push()
{
  // Temporary holding list for transforms moving into ready list
  struct list tmp;
  list_init(&tmp);

  for (int i = 0; i < transforms_waiting.capacity; i++)
    for (struct list_lp_item* item = transforms_waiting.array[i]->head; item;
         item = item->next)
    {
      transform* T = item->data;
      assert(T);
      bool subscribed = progress(T);
      if (!subscribed)
      {
        DEBUG_TURBINE("not subscribed on: %li\n", T->id);
        list_add(&tmp, T);
      }
    }

  add_to_ready(&tmp);

  return TURBINE_SUCCESS;
}

/**
   Declare a new data id
   @param result If non-NULL, return the new blocked list here
 */
turbine_code
declare_datum(turbine_datum_id id, struct list_l** result)
{
  assert(initialized);
  // DEBUG_TURBINE("declare: %li\n", id);
  struct list_l* blocked = list_l_create();
  if (table_lp_contains(&td_blockers, id))
    return TURBINE_ERROR_DOUBLE_DECLARE;
  table_lp_add(&td_blockers, id, blocked);
  if (result != NULL)
    *result = blocked;
  return TURBINE_SUCCESS;
}

/**
   Obtain the list of trs ready to run
 */
turbine_code
turbine_ready(int count, turbine_transform_id* output,
              int* result)
{
  int i = 0;
  void* v;
  DEBUG_TURBINE("ready:");
  while (i < count && (v = list_poll(&transforms_ready)))
  {
    transform* T = (transform*) v;
    table_lp_add(&transforms_running, T->id, T);
    output[i] = T->id;
    DEBUG_TURBINE("\t %li", output[i]);
    i++;
  }
  *result = i;
  return TURBINE_SUCCESS;
}

/**
   @param action Pointer into Turbine memory
                 Use this before calling turbine_complete
 */
turbine_code
turbine_action(turbine_transform_id id,
               turbine_action_type* action_type, char** action)
{
  if (id == TURBINE_ID_NULL)
    return TURBINE_ERROR_NULL;

  transform* T = table_lp_search(&transforms_running, id);
  if (!T)
    return TURBINE_ERROR_NOT_FOUND;

  *action = T->action;
  *action_type = T->action_type;

  DEBUG_TURBINE("action: {%li} %s: %s", id, T->name, T->action);
  return TURBINE_SUCCESS;
}

/**
   Indicate a transform action is no longer running
   Free the transform
 */
turbine_code
turbine_complete(turbine_transform_id id)
{
  if (id == TURBINE_ID_NULL)
    return TURBINE_ERROR_NULL;

  transform* T = table_lp_remove(&transforms_running, id);
  assert(T);
  DEBUG_TURBINE("complete: {%li} %s", id, T->name);
  transform_free(T);
  return TURBINE_SUCCESS;
}

/**
   TODO: This does not remove tds from td_blockers!
 */
turbine_code
turbine_close(turbine_datum_id id)
{
  // Look up transforms that this td was blocking
  struct list_l* L = table_lp_search(&td_blockers, id);
  if (L == NULL)
    // We don't have any rules that block on this td
    return TURBINE_SUCCESS;

  // Temporary holding spot for transforms moving into ready list
  struct list tmp;
  list_init(&tmp);

  // Try to make progress on those transforms
  for (struct list_l_item* item = L->head; item; item = item->next)
  {
    turbine_transform_id transform_id = item->data;
    transform* T = table_lp_search(&transforms_waiting, transform_id);
    if (!T)
      continue;

    bool subscribed = progress(T);
    if (!subscribed)
    {
      DEBUG_TURBINE("ready: {%li}", transform_id);
      list_add(&tmp, T);
    }
  }

  add_to_ready(&tmp);

  return TURBINE_SUCCESS;
}

static bool
progress(transform* T)
{
  int subscribed = 0;
  while (T->blocker < T->inputs)
  {
    turbine_datum_id td = T->input_list[T->blocker];
    subscribe(td, &subscribed);
    if (!subscribed)
      T->blocker++;
    else
      break;
  }
  return subscribed;
}

/**
   @param output Should point to good storage for output,
   at least 64 chars
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
    default:
      sprintf(output, "<could not convert code to string>");
      break;
  }
  return result;
}

static int
transform_tostring(char* output, transform* t)
{
  int result = 0;
  char* p = output;

  append(p, "%s:%i ", t->name, t->action_type);
  append(p, "(");
  for (int i = 0; i < t->inputs; i++)
  {
    append(p, "%li", t->input_list[i]);
    if (i < t->inputs-1)
      append(p, " ");
  }
  append(p, ")");

  result = p - output;
  return result;
}

static void
info_waiting()
{
  printf("WAITING TRANSFORMS: %i\n", transforms_waiting.size);
  char buffer[1024];
  for (int i = 0; i < transforms_waiting.capacity; i++)
    for (struct list_lp_item* item = transforms_waiting.array[i]->head;
         item; item = item->next)
    {
      transform* t = item->data;
      int c = sprintf(buffer, "%6li  ", t->id);
      transform_tostring(buffer+c, t);
      printf("TRANSFORM: %s\n", buffer);
    }
}

void
turbine_finalize()
{
  if (transforms_waiting.size != 0)
    info_waiting();
}
