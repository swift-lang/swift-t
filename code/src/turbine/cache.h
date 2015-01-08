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

/**
   If size==0, the cache is disabled
 */
void turbine_cache_init(int size, unsigned long max_memory);

bool turbine_cache_check(turbine_datum_id td);

turbine_code turbine_cache_retrieve(turbine_datum_id td,
                                    turbine_type* type,
                                    void** result, size_t* length);

turbine_code turbine_cache_store(turbine_datum_id td,
                                 turbine_type type,
                                 void* data, size_t length);

void turbine_cache_finalize(void);

#endif
