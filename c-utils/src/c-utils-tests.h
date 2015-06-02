/*
 * Copyright 2015 University of Chicago and Argonne National Laboratory
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
  Utilities for tests.
 */

#ifndef _CUTILS_TESTS_H
#define _CUTILS_TESTS_H

#include <stdlib.h>

#define ASSERT_TRUE(cond) { \
  if (!(cond)) { \
    printf("Assert failed at %s:%i: " #cond "\n", __FILE__, __LINE__); \
    exit(1); \
} }

#define ASSERT_TRUE_MSG(cond, fmt, args...) { \
  if (!(cond)) { \
    printf("Assert failed at %s:%i: " #cond "\n", __FILE__, __LINE__); \
    printf(fmt "\n", ##args); \
    exit(1); \
} }

#endif //_CUTILS_TESTS_H
