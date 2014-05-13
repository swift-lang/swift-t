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

static const adlb_debug_symbol_data NULL_DATA = { .name = NULL,
                                                  .context = NULL };

typedef struct {
  char *name;
  char *context;
} symbol_table_entry;

static bool debug_symbols_init = false;

/*
 * Map from adlb_debug_symbol to strings.
 * Key type is wider than needed, but this shouldn't be a problem.
 */
static struct table_lp debug_symbols;

void debug_symbol_free_cb(int64_t key, void *data)
{
  free(data);
}

adlb_code xlb_debug_symbols_init(void)
{
  assert(!debug_symbols_init);
  bool ok = table_lp_init(&debug_symbols, 1024);

  CHECK_MSG(ok, "Error initialising debug symbols");

  debug_symbols_init = true;
  return ADLB_DATA_SUCCESS;
}

void xlb_debug_symbols_finalize(void)
{
  assert(debug_symbols_init);
  table_lp_free_callback(&debug_symbols, false, debug_symbol_free_cb);
  
  debug_symbols_init = false;
}

adlb_code ADLBP_Add_debug_symbol(adlb_debug_symbol symbol,
                                 adlb_debug_symbol_data data)
{
  CHECK_MSG(debug_symbols_init, "Debug symbols module not init");
  CHECK_MSG(symbol != ADLB_DEBUG_SYMBOL_NULL, "Cannot add "
      "ADLB_DEBUG_SYMBOL_NULL as debug symbol for %s:%s",
      data.name, data.context);
  CHECK_MSG(data.name != NULL, "name for debug symbol was NULL");
  CHECK_MSG(data.context != NULL, "context for debug symbol was NULL");
  
  // free existing entry if needed
  symbol_table_entry *prev_entry;
  if (table_lp_remove(&debug_symbols, symbol, (void **)&prev_entry))
  {
    DEBUG("Overwriting old symbol entry %"PRIu32"=%s:%s",
         symbol, prev_entry->name, prev_entry->context);
    free(prev_entry->name);
    free(prev_entry->context);
    free(prev_entry);
  }

  symbol_table_entry *e = malloc(sizeof(symbol_table_entry));
  ADLB_MALLOC_CHECK(e);

  e->name = strdup(data.name);
  ADLB_MALLOC_CHECK(e->name);
  
  e->context = strdup(data.context);
  ADLB_MALLOC_CHECK(e->context);
  
  bool ok = table_lp_add(&debug_symbols, symbol, e);
  CHECK_MSG(ok, "Unexpected error adding debug symbol to table");

  return ADLB_SUCCESS;
}

adlb_debug_symbol_data ADLBP_Debug_symbol(adlb_debug_symbol symbol)
{
  if (!debug_symbols_init)
  {
    return NULL_DATA;
  }

  symbol_table_entry *data;
  if (table_lp_search(&debug_symbols, symbol, (void**)&data))
  {
    adlb_debug_symbol_data result = { .name = data->name,
                              .context = data->context };
    return result;
  }
  return NULL_DATA;
}
