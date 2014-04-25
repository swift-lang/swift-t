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

#ifndef __XLB_DEBUG_SYMBOLS
#define __XLB_DEBUG_SYMBOLS

#include "adlb-defs.h"

/*
 * Init and finalize: should be called before using debug symbols.
 */
adlb_code xlb_debug_symbols_init(void);
void xlb_debug_symbols_finalize(void);

#endif // __XLB_DEBUG_SYMBOLS
