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

/**
 * turbine-checks.h
 *
 *  Created on: Jun 17, 2014
 *      Author: armstrong
 *
 * Check utilities for checking turbine return codes
 */

#ifndef __TURBINE_CHECKS_H
#define __TURBINE_CHECKS_H

#define turbine_check(code) if (code != TURBINE_SUCCESS) return code;

#define turbine_check_verbose(code) \
    turbine_check_verbose_impl(code, __FILE__, __LINE__)

#define turbine_check_verbose_impl(code, file, line)    \
  { if (code != TURBINE_SUCCESS)                        \
    {                                                   \
      char output[TURBINE_CODE_STRING_MAX];             \
      turbine_code_tostring(output, code);              \
      printf("turbine error: %s\n", output);            \
      printf("\t at: %s:%i\n", file, line);             \
      return code;                                      \
    }                                                   \
  }

#define turbine_condition(condition, code, format, args...) \
  { if (! (condition))                                      \
    {                                                       \
       printf(format, ## args);                             \
       return code;                                         \
    }}


#define EXEC_CHECK(code) { \
  if (code != TURBINE_EXEC_SUCCESS) return code; }

#define EXEC_CHECK_MSG(code, err_code, fmt, args...) { \
  if (code != TURBINE_EXEC_SUCCESS) { \
      fprintf(stderr, "CHECK FAILED: %s:%i\n", __FILE__, __LINE__);   \
      fprintf(stderr, fmt "\n", ##args); \
      return err_code; }}

#endif // __TURBINE_CHECKS_H
