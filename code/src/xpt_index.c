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
#include "xpt_index.h"

#ifdef XLB_ENABLE_XPT

adlb_code xlb_xpt_index_init(void)
{
  return ADLB_ERROR;
}

adlb_code xlb_xpt_index_lookup(const void *key, int key_len,
                               xpt_index_entry *res)
{
  return ADLB_ERROR;
}

adlb_code xlb_xpt_index_add(const void *key, int key_len,
                            const xpt_index_entry *entry)
{
  return ADLB_ERROR;
}

#endif // XLB_ENABLE_XPT
