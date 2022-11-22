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

typedef adlb_datum_id turbine_datum_id;

typedef enum
{
  TURBINE_TYPE_NULL      = 0,
  TURBINE_TYPE_INTEGER   = ADLB_DATA_TYPE_INTEGER,
  TURBINE_TYPE_FLOAT     = ADLB_DATA_TYPE_FLOAT,
  TURBINE_TYPE_STRING    = ADLB_DATA_TYPE_STRING,
  TURBINE_TYPE_BLOB      = ADLB_DATA_TYPE_BLOB,
  TURBINE_TYPE_REF       = ADLB_DATA_TYPE_REF,
  TURBINE_TYPE_CONTAINER = ADLB_DATA_TYPE_CONTAINER,
  TURBINE_TYPE_MULTISET  = ADLB_DATA_TYPE_MULTISET,
  TURBINE_TYPE_STRUCT    = ADLB_DATA_TYPE_STRUCT,
} turbine_type;

typedef struct
{
  char* name;
} turbine_entry;

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
  /** Error when doing internal I/O */
  TURBINE_ERROR_IO,
  /** Unknown error */
  TURBINE_ERROR_UNKNOWN = ADLB_DATA_ERROR_UNKNOWN,
} turbine_code;

/** The NULL TD */
#define TURBINE_ID_NULL        ADLB_DATA_ID_NULL

/** Indicates any MPI rank */
#define TURBINE_RANK_ANY ADLB_RANK_ANY

/**
   The maximal storage of a container subscript
 */
#define TURBINE_SUBSCRIPT_MAX ADLB_DATA_SUBSCRIPT_MAX

/**
   The maximal storageof a Turbine rule name string
 */
#define TURBINE_NAME_MAX 128

/**
   The maximal length of a datum (string, blob, etc.)
 */
#define TURBINE_DATA_MAX ADLB_DATA_MAX

/**
   The maximal storage of a Turbine action string
 */
#define TURBINE_ACTION_MAX 1024

#define turbine_string_totype adlb_data_string_totype

#endif
