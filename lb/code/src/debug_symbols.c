/*
 * Copyright 2014 University of Chicago and Argonne National Laboratory
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

#include "debug_symbols.h"

#include "adlb-defs.h"
#include "adlb.h"

#include "checks.h"
#include "debug.h"

#include <table_lp.h>

static const adlb_dsym_data NULL_DATA =
{
  .name = NULL,
  .context = NULL
};

typedef struct
{
  char *name;
  char *context;
} symbol_table_entry;

static bool dsyms_init = false;

/*
 * Map from adlb_dsym to strings.
 * Key type is wider than needed, but this shouldn't be a problem.
 */
static struct table_lp dsyms;

static void dsym_free_cb(unused int64_t key, void *data)
{
  symbol_table_entry *entry = data;
  free(entry->name);
  free(entry->context);
  free(entry);
}

adlb_code xlb_dsyms_init(void)
{
  assert(!dsyms_init);
  bool ok = table_lp_init(&dsyms, 1024);

  ADLB_CHECK_MSG(ok, "Error initialising debug symbols");

  dsyms_init = true;
  return ADLB_SUCCESS;
}

void xlb_dsyms_finalize(void)
{
  valgrind_assert(dsyms_init);
  table_lp_free_callback(&dsyms, false, dsym_free_cb);

  dsyms_init = false;
}

adlb_code ADLBP_Add_dsym(adlb_dsym symbol,
                                 adlb_dsym_data data)
{
  ADLB_CHECK_MSG(dsyms_init, "Debug symbols module not init");
  ADLB_CHECK_MSG(symbol != ADLB_DSYM_NULL, "Cannot add "
      "ADLB_DSYM_NULL as debug symbol for %s:%s",
      data.name, data.context);
  ADLB_CHECK_MSG(data.name != NULL, "name for debug symbol was NULL");
  ADLB_CHECK_MSG(data.context != NULL, "context for debug symbol was NULL");

  // free existing entry if needed
  symbol_table_entry *prev_entry;
  if (table_lp_remove(&dsyms, symbol, (void **)&prev_entry))
  {
    DEBUG("Overwriting old symbol entry %"PRIu32"=%s:%s",
         symbol, prev_entry->name, prev_entry->context);
    free(prev_entry->name);
    free(prev_entry->context);
    free(prev_entry);
  }

  symbol_table_entry *e = malloc(sizeof(symbol_table_entry));
  ADLB_CHECK_MALLOC(e);

  e->name = strdup(data.name);
  ADLB_CHECK_MALLOC(e->name);

  e->context = strdup(data.context);
  ADLB_CHECK_MALLOC(e->context);

  bool ok = table_lp_add(&dsyms, symbol, e);
  ADLB_CHECK_MSG(ok, "Unexpected error adding debug symbol to table");

  return ADLB_SUCCESS;
}

adlb_dsym_data ADLBP_Dsym(adlb_dsym symbol)
{
  if (!dsyms_init)
  {
    return NULL_DATA;
  }

  symbol_table_entry *data;
  if (table_lp_search(&dsyms, symbol, (void**)&data))
  {
    adlb_dsym_data result = { .name = data->name,
                              .context = data->context };
    return result;
  }
  return NULL_DATA;
}
