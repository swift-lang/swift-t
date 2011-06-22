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

#include <list.h>
#include <lnlist.h>
#include <ltable.h>

#include "src/turbine/turbine.h"

//const int TURBINE_NAME_MAX = 128;
//const int TURBINE_ARGS_MAX = 128;

typedef enum
{
  TD_UNSET, TD_SET
} td_status;

typedef enum
{
  TURBINE_TYPE_FILE
} turbine_type;

typedef struct
{
  turbine_type type;
  turbine_datum_id id;
  td_status status;
  union
  {
    struct
    {
      char* path;
    } file;
  } data;
  struct lnlist listeners;
} turbine_datum;

typedef enum
{
  TR_WAITING, TR_READY, TR_RUNNING, TR_DONE
} tr_status;

typedef struct
{
  turbine_transform_id id;
  turbine_transform transform;
  tr_status status;
} tr;

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
   Running trs
   Map from tr id to tr
 */
struct ltable trs_running;

/**
   Map from turbine_datum_id to turbine_datum
*/
struct ltable tds;

#define TURBINE_DEBUG(format, args...) turbine_debug(format, ## args)
static void turbine_debug(char* format, ...)
{
  va_list va;
  va_start(va,format);
  vprintf(format, va);
  printf("\n");
  va_end(va);
}

turbine_code
turbine_init()
{
  struct ltable* result;
  result = ltable_init(&trs_waiting, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;
  list_init(&trs_ready);
  ltable_init(&trs_running, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;
  result = ltable_init(&tds, 1024*1024);
  if (!result)
    return TURBINE_ERROR_OOM;
  return TURBINE_SUCCESS;
}

static turbine_code
td_register(turbine_datum* datum)
{
  if (ltable_search(&tds, datum->id))
    return TURBINE_ERROR_DOUBLE_DECLARE;
  bool result = ltable_add(&tds, datum->id, datum);
  if (!result)
    return TURBINE_ERROR_OOM;
  return TURBINE_SUCCESS;
}

static turbine_datum*
td_get(turbine_datum_id id)
{
  return ltable_search(&tds, id);
}

turbine_code
turbine_datum_file_create(turbine_datum_id id, char* path)
{
  turbine_datum* result = malloc(sizeof(turbine_datum));
  if (!result)
    return TURBINE_ERROR_OOM;
  result->type = TURBINE_TYPE_FILE;
  result->data.file.path = strdup(path);
  result->id = id;
  result->status = TD_UNSET;
  lnlist_init(&result->listeners);
  turbine_code code = td_register(result);
  return code;
}

turbine_code
turbine_filename(turbine_datum_id id, char* output)
{
  turbine_datum* td = ltable_search(&tds, id);
  if (td == NULL)
    return TURBINE_ERROR_NOT_FOUND;

  strcpy(output, td->data.file.path);
  return TURBINE_SUCCESS;
}

static turbine_code
tr_create(turbine_transform* transform, tr** t)
{
  assert(transform->name);
  assert(transform->executor);

  tr* result = malloc(sizeof(tr));

  result->transform.name = strdup(transform->name);
  result->transform.executor = strdup(transform->executor);

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
  free(t->transform.executor);
  if (t->transform.input)
    free(t->transform.input);
  if (t->transform.output)
    free(t->transform.output);
  free(t);
}

static bool is_ready(tr* t);

static void subscribe(turbine_transform* transform,
                      turbine_transform_id id);

turbine_code
turbine_rule_add(turbine_transform_id id,
                 turbine_transform* transform)
{
  tr* new_tr;
  turbine_code code = tr_create(transform, &new_tr);
  turbine_debug("turbine_rule_add: %li", id);
  turbine_check(code);
  new_tr->id = id;
  if (is_ready(new_tr))
  {
    list_add(&trs_ready, new_tr);
  }
  else
  {
    ltable_add(&trs_waiting, id, new_tr);
    subscribe(transform, id);
  }
  return TURBINE_SUCCESS;
}

static void
subscribe(turbine_transform* transform, turbine_transform_id id)
{
  for (int i = 0; i < transform->inputs; i++)
  {
    turbine_datum_id dd = transform->input[i];
    turbine_datum* td = ltable_search(&tds, dd);
    assert(td);
    lnlist_add(&td->listeners, id);
  }
}

static turbine_datum_id unique = 0;

turbine_code
turbine_new(turbine_datum_id* id)
{
  while (true)
  {
    if (! ltable_contains(&tds, unique))
      break;
    unique++;
  }
  *id = unique;
  unique++;
  return TURBINE_SUCCESS;
}

/**
   Push transforms that are ready into trs_ready
*/
turbine_code
turbine_rules_push()
{
  struct list tmp;
  list_init(&tmp);
  for (int i = 0; i < trs_waiting.capacity; i++)
    for (struct llist_item* item = trs_waiting.array[i]->head; item;
         item = item->next)
    {
      tr* t = item->data;
      assert(t);
      if (is_ready(t))
        list_add(&tmp, t);
    }

  tr* t;
  while ((t = list_poll(&tmp)))
  {
    void* c = ltable_remove(&trs_waiting, t->id);
    assert(c);
    list_add(&trs_ready, t);
  }

  return TURBINE_SUCCESS;
}

turbine_code
turbine_ready(int count, turbine_transform_id* output,
              int* result)
{
  int i = 0;
  void* v;
  turbine_debug("turbine_ready:");
  while (i < count &&
         (v = list_poll(&trs_ready)))
  {
    tr* t = (tr*) v;
    ltable_add(&trs_running, t->id, t);
    output[i++] = t->id;
    turbine_debug("\t %li\n", t->id);
  }
  *result = i;
  return TURBINE_SUCCESS;
}

static turbine_code
td_close(turbine_datum* datum)
{
  datum->status = TD_SET;
  return TURBINE_SUCCESS;
}

static bool
is_ready(tr* t)
{
  for (int i = 0; i < t->transform.inputs; i++)
  {
    turbine_datum_id id = t->transform.input[i];
    turbine_datum* d = ltable_search(&tds, id);
    if (d->status == TD_UNSET)
      return false;
  }
  return true;
}

/**
   Move trs that are ready to run from waiting to ready
   Note: cannot modify list while iterating over it
 */
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
    if (is_ready(t))
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
turbine_close(turbine_datum_id id)
{
  TURBINE_DEBUG("turbine_close: %li", id);
  turbine_datum* td = ltable_search(&tds, id);
  assert(td);
  turbine_code code = td_close(td);
  turbine_check(code);
  notify_waiters(td);
  return TURBINE_SUCCESS;
}

turbine_code
turbine_executor(turbine_transform_id id, char* executor)
{
  tr* transform = ltable_search(&trs_running, id);
  if (!transform)
      return TURBINE_ERROR_NOT_FOUND;

  strcpy(executor, transform->transform.executor);
  return TURBINE_SUCCESS;
}

turbine_code
turbine_complete(turbine_transform_id id)
{
  TURBINE_DEBUG("turbine_complete: %li", id);
  tr* t = ltable_remove(&trs_running, id);
  assert(t);
  for (int i = 0; i < t->transform.outputs; i++)
  {
    turbine_code code = turbine_close(t->transform.output[i]);
    turbine_check(code);
  }
  tr_free(t);

  return TURBINE_SUCCESS;
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
    case TURBINE_ERROR_DOUBLE_WRITE:
      result = sprintf(output, "TURBINE_ERROR_DOUBLE_WRITE");
      break;
    case TURBINE_ERROR_NOT_FOUND:
      result = sprintf(output, "TURBINE_ERROR_NOT_FOUND");
      break;
    case TURBINE_ERROR_COMMAND:
      result = sprintf(output, "TURBINE_ERROR_COMMAND");
      break;
    case TURBINE_ERROR_UNKNOWN:
      result = sprintf(output, "TURBINE_ERROR_UNKNOWN");
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
  }
  return result;
}

int
turbine_data_tostring(char* output, int length, turbine_datum_id id)
{
  int t;
  int result = 0;
  char* p = output;

  turbine_datum* d = td_get(id);

  t = snprintf(p, length, "%li:", d->id);
  result += t;
  length -= t;
  p      += t;

  t = td_tostring(p, length, d);
  result += t;
  length -= t;
  p      += t;

  char* status = (d->status == TD_UNSET) ? "UNSET" : "SET";
  t = snprintf(p, length, "%s", status);
  result += t;

  return result;
}

void turbine_finalize()
{}
