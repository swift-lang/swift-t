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

/*
 *  Simple binary heap with integer keys and integer values.
 *
 *  Implements a min-heap
 *
 *  Tim Armstrong, 2012-2014
 */

#ifndef __HEAP_IU32_H
#define __HEAP_IU32_H

#include <stdint.h>

#define HEAP_KEY_T int
#define HEAP_VAL_T uint32_t
#define HEAP_PFX heap_iu32_

#include "heap-template.h"

#undef HEAP_KEY_T
#undef HEAP_VAL_T
#undef HEAP_PFX

#endif // __HEAP_IU32_H
