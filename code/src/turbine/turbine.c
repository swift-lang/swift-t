/*
 * turbine.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>

#include <adlb.h>

#include "src/util/debug.h"
#include "src/util/tools.h"
#include "src/util/hashtable.h"
#include "src/util/list.h"
#include "src/util/lnlist.h"
#include "src/util/longlist.h"
#include "src/util/ltable.h"

#include "src/turbine/turbine.h"

//const int TURBINE_NAME_MAX = 128;
//const int TURBINE_ARGS_MAX = 128;

/**
   Reusable buffer for data transfer
*/
static char xfer[1024*1024];

typedef enum
{
  TR_WAITING, TR_READY, TR_RUNNING, TR_DONE
} tr_status;

typedef struct
{
  turbine_transform_id id;
  turbine_transform transform;
  /** Index of next subscribed input (starts at 0) */
  int blocker;
  tr_status status;
} tr;

/**
   Has turbine_init() been called successfully?
*/
bool initialized = false;

/**
   Waiting trs
   Map from tr id to tr
 */
struct ltable trs_waiting;

/**
   Ready trs
 */
struct list trs_ready;

/**
   Running TRs
   Map from TR ID to TR
 */
struct ltable trs_running;

/**
   Inputs blocking their TRs
   Map from TD ID to list of TRs
 */
struct ltable blockers;

/*
static void turbine_check_msg_impl(turbine_code code,
                                   const char* format, ...);
*/

/**
   Unique transform ID for rule_new().
 */
static long unique_transform = 1;

#define turbine_condition(condition, code, format, args...) \
  { if (! (condition))                                      \
    {                                                       \
       printf(format, ## args);                             \
       return code;                                         \
    }}

turbine_code
turbine_init(int amserver)
{
  if (amserver)
    return TURBINE_SUCCESS;

  struct ltable* table;
  table = ltable_init(&trs_waiting, 1024*1024);
  if (!table)
    return TURBINE_ERROR_OOM;
  list_init(&trs_ready);
  ltable_init(&trs_running, 1024*1024);
  if (!table)
    return TURBINE_ERROR_OOM;
  ltable_init(&blockers, 1024*1024);
  initialized = true;
  return TURBINE_SUCCESS;
}

/*
static int
type_tostring(char* output, turbine_type type)
{
  int result = -1;
  switch(type)
  {
    case TURBINE_TYPE_FILE:
      result = sprintf(output, "file");
      break;
    case TURBINE_TYPE_CONTAINER:
      result = sprintf(output, "container");
      break;
    case TURBINE_TYPE_INTEGER:
      result = sprintf(output, "integer");
      break;
    case TURBINE_TYPE_STRING:
      result = sprintf(output, "string");
      break;
    default:
      sprintf(output, "<unknown type>");
  }

  return result;
}
*/

static turbine_code
tr_create(turbine_transform* transform, tr** t)
{
  assert(transform->name);
  assert(transform->action);

  tr* result = malloc(sizeof(tr));

  result->transform.name = strdup(transform->name);
  result->transform.action = strdup(transform->action);
  result->blocker = 0;

  if (transform->inputs > 0)
  {
    result->transform.input =
        malloc(transform->inputs*sizeof(turbine_datum_id));
    if (! result->transform.input)
      return TURBINE_ERROR_OOM;
  }
  else
    result->transform.input = NULL;

  if (transform->outputs > 0)
  {
    result->transform.output =
        malloc(transform->outputs*sizeof(turbine_datum_id));
    if (! result->transform.output)
      return TURBINE_ERROR_OOM;
  }
  else
    result->transform.output = NULL;

  result->transform.inputs = transform->inputs;
  for (int i = 0; i < transform->inputs; i++)
    result->transform.input[i] = transform->input[i];

  result->transform.outputs = transform->outputs;
  for (int i = 0; i < transform->outputs; i++)
    result->transform.output[i] = transform->output[i];

  result->status = TR_WAITING;

  *t = result;
  return TURBINE_SUCCESS;
}

static void
tr_free(tr* t)
{
  free(t->transform.name);
  free(t->transform.action);
  if (t->transform.input)
    free(t->transform.input);
  if (t->transform.output)
    free(t->transform.output);
  free(t);
}

// static turbine_code is_ready(tr* t, bool* result);

static void subscribe(turbine_transform_id id,
                      int* result)
{
  ADLB_Subscribe(id, result);
}

static int transform_tostring(char* output,
                              turbine_transform* transform);

#ifdef ENABLE_DEBUG_TURBINE
#define DEBUG_TURBINE_RULE_ADD(transform, id) {         \
    char tmp[1024];                                     \
    transform_tostring(tmp, transform);                 \
    DEBUG_TURBINE("rule_add: %s {%li}\n", tmp, id);     \
  }
#else
#define DEBUG_TURBINE_RULE_ADD(transform, id)
#endif

static bool progress(tr* transform);

static void rule_inputs(tr* transform);

turbine_code
turbine_rule_add(turbine_transform_id id,
                 turbine_transform* transform)
{
  if (id == TURBINE_ID_NULL)
    return TURBINE_ERROR_NULL;

  tr* new_tr;
  turbine_code code = tr_create(transform, &new_tr);
  DEBUG_TURBINE_RULE_ADD(transform, id);
  turbine_check(code);
  new_tr->id = id;
  new_tr->blocker = 0;

  rule_inputs(new_tr);

  bool subscribed = progress(new_tr);

  if (subscribed)
  {
    ltable_add(&trs_waiting, id, new_tr);
  }
  else
  {
    list_add(&trs_ready, new_tr);
  }

  if (id >= unique_transform)
    unique_transform = id+1;

  return TURBINE_SUCCESS;
}

/**
   Record that this rule is blocked by its inputs
*/
static void
rule_inputs(tr* transform)
{

  for (int i = 0; i < transform->transform.inputs; i++)
  {
    turbine_datum_id id = transform->transform.input[i];
    struct longlist* L = ltable_search(&blockers, id);
    // turbine_condition(L != NULL, TURBINE_ERROR_NOT_FOUND,
    //                  "rule_add: could not find: <%li>\n", id);
    if (L == NULL)
      turbine_declare(id, &L);
    longlist_add(L, transform->id);
  }
}

turbine_code
turbine_rule_new(turbine_transform_id *id)
{
  *id = unique_transform++;
  return TURBINE_SUCCESS;
}

/**
   Remove the transforms from waiting and add to ready list
   Empties given list along the way
 */
static void
add_to_ready(struct list* tmp)
{
  tr* t;
  // ltable_dumpkeys(&trs_waiting);
  while ((t = list_poll(tmp)))
  {
    void* c = ltable_remove(&trs_waiting, t->id);
    assert(c);
    list_add(&trs_ready, t);
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

  for (int i = 0; i < trs_waiting.capacity; i++)
    for (struct llist_item* item = trs_waiting.array[i]->head; item;
         item = item->next)
    {
      tr* t = item->data;
      assert(t);
      bool subscribed = progress(t);
      if (!subscribed)
      {
        DEBUG_TURBINE("not subscribed on: %li\n", t->id);
        list_add(&tmp, t);
      }
    }

  add_to_ready(&tmp);

  return TURBINE_SUCCESS;
}

/**
   @param result If non-NULL, return the new blocked list here
 */
turbine_code
turbine_declare(turbine_datum_id id, struct longlist** result)
{
  assert(initialized);
  DEBUG_TURBINE("declare: %li\n", id);
  struct longlist* blocked = longlist_create();
  if (ltable_contains(&blockers, id))
    return TURBINE_ERROR_DOUBLE_DECLARE;
  ltable_add(&blockers, id, blocked);
  if (result != NULL)
    *result = blocked;
  return TURBINE_SUCCESS;
}

turbine_code
turbine_ready(int count, turbine_transform_id* output,
              int* result)
{
  int i = 0;
  void* v;
  DEBUG_TURBINE("ready:\n");
  while (i < count && (v = list_poll(&trs_ready)))
  {
    tr* t = (tr*) v;
    ltable_add(&trs_running, t->id, t);
    output[i] = t->id;
    DEBUG_TURBINE("\t %li\n", output[i]);
    i++;
  }
  *result = i;
  return TURBINE_SUCCESS;
}

turbine_code
turbine_entry_set(turbine_entry* entry,
                  const char* type, const char* name)
{
  if (strcmp(type, "field"))
    entry->type = TURBINE_ENTRY_FIELD;
  else if (strcmp(type, "key"))
    entry->type = TURBINE_ENTRY_KEY;
  else
  {
    printf("unknown entry type: %s\n", type);
    assert(false);
  }
  strcpy(entry->name, name);
  return TURBINE_SUCCESS;
}

/*
static turbine_code
is_ready(tr* t, bool* result)
{
  for (int i = 0; i < t->transform.inputs; i++)
  {
    turbine_datum_id id = t->transform.input[i];
    bool status;
    int error = progress()
    assert(error == ADLB_SUCCESS);
    if (!status)
    {
      *result = false;
      return TURBINE_SUCCESS;
    }
  }
  *result = true;
  return TURBINE_SUCCESS;
}
*/
/**
   Move trs that are ready to run from waiting to ready
   Note: cannot modify list while iterating over it
 */
/*
static void
notify_waiters(turbine_datum* td)
{
  struct list tmp;
  list_init(&tmp);

  // Put trs that are ready into tmp
  for (struct lnlist_item* item = td->listeners.head; item;
       item = item->next)
  {
    turbine_transform_id id = item->data;
    tr* t = ltable_search(&trs_waiting, id);
    assert(t);
    bool result;
    turbine_code code = is_ready(t, &result);
    turbine_check_msg(code, "unknown input in rule: %s\n",
                      t->transform.name);
    if (result)
      list_add(&tmp, t);
  }

  // Put items from tmp into ready
  while (tmp.size > 0)
  {
    tr *t = list_pop(&tmp);
    void* c = ltable_remove(&trs_waiting, t->id);
    assert(c);
    list_add(&trs_ready, t);
  }
}
*/

/*
static int
id_cmp(void* id1, void* id2)
{
  long* l1 = id1;
  long* l2 = id2;
  if (*l1 < *l2)
    return -1;
  else if (*l1 > *l2)
    return 1;
  return 0;
}
*/

turbine_code
turbine_action(turbine_transform_id id, char* action)
{
  if (id == TURBINE_ID_NULL)
    return TURBINE_ERROR_NULL;

  tr* t = ltable_search(&trs_running, id);
  if (!t)
      return TURBINE_ERROR_NOT_FOUND;

  strcpy(action, t->transform.action);

  DEBUG_TURBINE("action: {%li} %s: %s\n",
                id, t->transform.name, action);
  return TURBINE_SUCCESS;
}

turbine_code
turbine_complete(turbine_transform_id id)
{
  if (id == TURBINE_ID_NULL)
    return TURBINE_ERROR_NULL;

  tr* t = ltable_remove(&trs_running, id);
  assert(t);
  DEBUG_TURBINE("complete: {%li} %s\n", id, t->transform.name);
  for (int i = 0; i < t->transform.outputs; i++)
  {
    turbine_code code = turbine_close(t->transform.output[i]);
    turbine_check(code);
  }
  tr_free(t);

  return TURBINE_SUCCESS;
}

turbine_code
turbine_close(turbine_datum_id id)
{
  // DEBUG_TURBINE("turbine_close()...\n");
  // ltable_dumpkeys(&trs_waiting);

  // Look up what this td was blocking
  struct longlist* L = ltable_search(&blockers, id);
  assert(L);

  // Temporary holding spot for transforms moving into ready list
  struct list tmp;
  list_init(&tmp);

  // Try to make progress on those transforms
  for (struct longlist_item* item = L->head; item; item = item->next)
  {
    turbine_transform_id tr_id = item->data;
    tr* transform = ltable_search(&trs_waiting, tr_id);
    if (!transform)
      continue;

    bool subscribed = progress(transform);
    if (!subscribed)
    {
      DEBUG_TURBINE("ready: {%li}\n", tr_id);
      list_add(&tmp, transform);
    }
  }

  add_to_ready(&tmp);

  return TURBINE_SUCCESS;
}

static bool
progress(tr* transform)
{
  // DEBUG_TURBINE("progress: {%li} %s\n",
  //              transform->id, transform->transform.name);

  int subscribed = 0;
  while (transform->blocker < transform->transform.inputs)
  {
    turbine_datum_id tid =
        transform->transform.input[transform->blocker];
    subscribe(tid, &subscribed);
    if (!subscribed)
      transform->blocker++;
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

static int td_tostring(char* output, int length, turbine_datum* td)
{
  int result;
  switch (td->type)
  {
    case TURBINE_TYPE_FILE:
      result = snprintf(output, length, "file:/%s",
                        td->data.file.path);
      break;
    case TURBINE_TYPE_CONTAINER:
      result = snprintf(output, length, "container");
      break;
    default:
      puts("unknown turbine_datum type!");
      assert(false);
      break;
  }
  return result;
}

static turbine_code
td_get(turbine_datum_id id, turbine_datum* td)
{
  int length;
  int error = ADLB_Retrieve(id, xfer, &length);
  assert(error == ADLB_SUCCESS);

  char type_string[32];
  char* p = strchr(xfer, ':');
  strncpy(type_string, xfer, xfer-p);
  type_string[xfer-p] = '\0';

  turbine_type type;
  turbine_string_totype(type, type_string);
  td->type = type;
  td->status = TD_SET;

  switch (type)
  {
  case TURBINE_TYPE_FILE:
    break;
  case TURBINE_TYPE_CONTAINER:
    break;
  case TURBINE_TYPE_INTEGER:
    sscanf(p+1, "%li", &td->data.integer.value);
    break;
  case TURBINE_TYPE_STRING:
    td->data.string.value = strdup(p+1);
    td->data.string.length = strlen(td->data.string.value);
  }

  return TURBINE_SUCCESS;
}

int
turbine_data_tostring(char* output, int length, turbine_datum_id id)
{
  int t;
  int result = 0;
  char* p = output;

  turbine_datum td;
  turbine_code code = td_get(id, &td);
  assert(code == TURBINE_SUCCESS);

  t = snprintf(p, length, "%li:", id);
  result += t;
  length -= t;
  p      += t;

  t = td_tostring(p, length, &td);
  result += t;
  length -= t;
  p      += t;

  char* status = (td.status == TD_UNSET) ? "UNSET" : "SET";
  t = snprintf(p, length, "%s", status);
  result += t;

  return result;
}

static int
transform_tostring(char* output, turbine_transform* transform)
{
  int result = 0;
  char* p = output;

  append(p, "%s ", transform->name);
  append(p, "(");
  for (int i = 0; i < transform->inputs; i++)
  {
    append(p, "%li", transform->input[i]);
    if (i < transform->inputs-1)
      append(p, " ");
  }
  append(p, ")->(");

  for (int i = 0; i < transform->outputs; i++)
  {
    append(p, "%li", transform->output[i]);
    if (i < transform->outputs-1)
      append(p, " ");
  }
  append(p, ")");

  result = p - output;
  return result;
}

static void
info_waiting()
{
  printf("WAITING TRANSFORMS: %i\n", trs_waiting.size);
  char buffer[1024];
  for (int i = 0; i < trs_waiting.capacity; i++)
    for (struct llist_item* item = trs_waiting.array[i]->head;
         item; item = item->next)
    {
      tr* t = (tr*) item->data;
      transform_tostring(buffer, &t->transform);
      printf("TRANSFORM: %s\n", buffer);
    }
}

void
turbine_finalize()
{
  if (trs_waiting.size != 0)
    info_waiting();
}

/*
static void
turbine_check_msg_impl(turbine_code code, const char* format, ...)
{
  char buffer[1024];
  char* p = &buffer[0];
  va_list ap;
  p += sprintf(p, "\n%s", "turbine error: ");
  va_start(ap, format);
  p += vsprintf(buffer, format, ap);
  va_end(ap);
  turbine_code_tostring(p, code);
  printf("%s\n", buffer);
  fflush(NULL);
}
*/
