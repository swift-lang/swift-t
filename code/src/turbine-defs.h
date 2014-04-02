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

/**
 * TURBINE DEFS
 *
 * This file defines Turbine symbols in terms of those used by ADLB
 *
 * Some of these values are sent over the wire, such as error code
 * numbers, etc.  They must be kept in sync
 * */

#include <adlb-defs.h>
#include <adlb_types.h>

#ifndef TURBINE_DEFS_H
#define TURBINE_DEFS_H

typedef struct
{
  char* name;
} turbine_entry;

// TODO: remove unneeded codes
typedef enum
{
  TURBINE_SUCCESS = ADLB_DATA_SUCCESS,
  /** Out of memory */
  TURBINE_ERROR_OOM = ADLB_DATA_ERROR_OOM,
  /** Attempt to declare the same thing twice */
  TURBINE_ERROR_DOUBLE_DECLARE = ADLB_DATA_ERROR_DOUBLE_DECLARE,
  /** Attempt to set the same datum twice */
  TURBINE_ERROR_DOUBLE_WRITE = ADLB_DATA_ERROR_DOUBLE_WRITE,
  /** Attempt to read an unset value */
  TURBINE_ERROR_UNSET = ADLB_DATA_ERROR_UNSET,
  /** Data set not found */
  TURBINE_ERROR_NOT_FOUND = ADLB_DATA_ERROR_NOT_FOUND,
  /** Parse error in number scanning */
  TURBINE_ERROR_NUMBER_FORMAT = ADLB_DATA_ERROR_NUMBER_FORMAT,
  /** Invalid input */
  TURBINE_ERROR_INVALID = ADLB_DATA_ERROR_INVALID,
  /** Attempt to read/write ID_NULL */
  TURBINE_ERROR_NULL = ADLB_DATA_ERROR_NULL,
  /** Attempt to operate on wrong data type */
  TURBINE_ERROR_TYPE = ADLB_DATA_ERROR_TYPE,
  /** Turbine function given insufficient output storage */
  TURBINE_ERROR_STORAGE,
  /** Called function when Turbine uninitialized */
  TURBINE_ERROR_UNINITIALIZED,
  /** Error in call to ADLB */
  TURBINE_ERROR_ADLB,
  /** Error in when calling external task */
  TURBINE_ERROR_EXTERNAL,
  /** Unknown error */
  TURBINE_ERROR_UNKNOWN = ADLB_DATA_ERROR_UNKNOWN,
} turbine_engine_code;

/**
   The maximal length of a Turbine rule name string
 */
#define TURBINE_NAME_MAX 128

#endif
