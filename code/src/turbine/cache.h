
/*
 * cache.h
 *
 *  Created on: Sep 4, 2012
 *      Author: wozniak
 *
 *  Local cache for variables in ADLB data store
 *  This is initialized and finalized by the Turbine C layer
 */

#ifndef CACHE_H
#define CACHE_H

#include "turbine-defs.h"

void turbine_cache_init(int size, unsigned long max_memory);

bool turbine_cache_check(turbine_datum_id td);

turbine_code turbine_cache_retrieve(turbine_datum_id td,
                                    turbine_type* type,
                                    void** result, int* length);

turbine_code turbine_cache_store(turbine_datum_id td,
                                 turbine_type type,
                                 void* data, int length);

void turbine_cache_finalize(void);

#endif
