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
#ifdef XLB_ENABLE_XPT
#include "adlb-xpt.h"

adlb_code adlb_xpt_init(const char *filename, adlb_xpt_flush_policy fp,
                        int max_index_val)
{
  // TODO
  return ADLB_ERROR;
}

adlb_code adlb_xpt_write(const void *key, int key_len, const void *val,
                        int val_len, adlb_xpt_persist persist)
{
  // TODO
  return ADLB_ERROR;
}

adlb_code adlb_xpt_lookup(const void *key, int key_len, adlb_binary_data *result)
{
  // TODO
  return ADLB_ERROR;
}

adlb_code adlb_xpt_reload(const char *filename)
{
  // TODO
  return ADLB_ERROR;
}




#endif // XLB_ENABLE_XPT
