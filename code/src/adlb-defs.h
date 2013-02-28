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
 * Basic definitions used by the ADLB Data module
 * */

#ifndef ADLB_DEFS_H
#define ADLB_DEFS_H

#include <list_i.h>
#include <list.h>
#include <list_l.h>

/**
   ADLB common return codes
   The only real error condition is ADLB_ERROR
   Cf. ADLB_IS_ERROR()
 */
typedef enum
{
 ADLB_SUCCESS  =  1,
 ADLB_ERROR    = -1,
 /** Rejected: e.g., out of memory */
 ADLB_REJECTED = -2,
 /** Normal shutdown */
 ADLB_SHUTDOWN = -3,
 /** No error but indicate nothing happened */
 ADLB_NOTHING = -4
} adlb_code;

/**
   Identifier for all ADLB data module user data
 */
typedef long adlb_datum_id;

/**
   Status vector for Turbine variables
 */
typedef unsigned char adlb_data_status;

/** SET: Whether a value has been stored in future */
#define ADLB_DATA_SET_MASK ((adlb_data_status)0x1)
#define ADLB_DATA_SET(status) ((status & ADLB_DATA_SET_MASK) != 0)

/** PERMANENT: Whether garbage collection is disabled for data item */
#define ADLB_DATA_PERMANENT_MASK ((adlb_data_status)0x2)
#define ADLB_DATA_PERMANENT(status) ((status & ADLB_DATA_PERMANENT_MASK) != 0)

/**
   User data types
 */
typedef enum
{
  ADLB_DATA_TYPE_NULL = 0,
  ADLB_DATA_TYPE_INTEGER,
  ADLB_DATA_TYPE_FLOAT,
  ADLB_DATA_TYPE_STRING,
  ADLB_DATA_TYPE_BLOB,
  ADLB_DATA_TYPE_CONTAINER
} adlb_data_type;


typedef enum
{
  ADLB_READ_REFCOUNT,
  ADLB_WRITE_REFCOUNT,
  ADLB_READWRITE_REFCOUNT, // Used to specify that op should affect both
} adlb_refcount_type;

typedef struct
{
  int read_refcount;
  int write_refcount;
  bool permanent;
} adlb_create_props;

extern adlb_create_props DEFAULT_CREATE_PROPS;

/**
   User data
 */
typedef struct
{
  adlb_data_type type;
  adlb_data_status status;
  int read_refcount; // Number of open read refs
  int write_refcount; // Number of open write refs
  union
  {
    struct
    {
      long value;
    } INTEGER;
    struct
    {
      double value;
    } FLOAT;
    struct
    {
      char* value;
      int length;
    } STRING;
    struct
    {
      void* value;
      int length;
    } BLOB;
    struct
    {
      char* path;
    } FILE;
    struct
    {
      /** type of container keys */
      adlb_data_type type;
      /** Map from subscript to member TD */
      struct table* members;
    } CONTAINER;
  } data;
  struct list_i listeners;
} adlb_datum;

/**
   Common return codes
 */
typedef enum
{
  ADLB_DATA_SUCCESS,
  /** Out of memory */
  ADLB_DATA_ERROR_OOM,
  /** Attempt to declare the same thing twice */
  ADLB_DATA_ERROR_DOUBLE_DECLARE,
  /** Attempt to set the same datum twice */
  ADLB_DATA_ERROR_DOUBLE_WRITE,
  /** Attempt to read an unset value */
  ADLB_DATA_ERROR_UNSET,
  /** Data set not found */
  ADLB_DATA_ERROR_NOT_FOUND,
  /** Parse error in number scanning */
  ADLB_DATA_ERROR_NUMBER_FORMAT,
  /** Invalid input */
  ADLB_DATA_ERROR_INVALID,
  /** Attempt to read/write ADLB_DATA_ID_NULL */
  ADLB_DATA_ERROR_NULL,
  /** Attempt to operate on wrong data type */
  ADLB_DATA_ERROR_TYPE,
  /** Slot count fell below 0 */
  ADLB_DATA_ERROR_SLOTS_NEGATIVE,
  /** Exceeded some implementation-defined limit */
  ADLB_DATA_ERROR_LIMIT,
  /** Unknown error */
  ADLB_DATA_ERROR_UNKNOWN,
} adlb_data_code;

//// Miscellaneous symbols:
#define ADLB_RANK_ANY  -100
#define ADLB_RANK_NULL -200
#define ADLB_TYPE_ANY  -300
#define ADLB_TYPE_NULL -400

/** The adlb_datum_id of nothing */
#define ADLB_DATA_ID_NULL 0

/** The maximal string length of a container subscript */
#define ADLB_DATA_SUBSCRIPT_MAX 1024

/** The maximal string length of a container member string value */
#define ADLB_DATA_MEMBER_MAX 1024

/** The maximal length of an ADLB datum (string, blob, etc.) */
#define ADLB_DATA_MAX (20*1024*1024)

/** Maximum size for a given ADLB transaction */
#define ADLB_PAYLOAD_MAX ADLB_DATA_MAX

/**
   The ASCII control character Record Separator
   Used to glue strings together in a big buffer
 */
#define RS 30

#endif
