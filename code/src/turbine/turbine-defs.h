
/**
 * TURBINE DEFS
 *
 * This file defines Turbine symbols in terms of those used by ADLB
 *
 * Some of these values are sent over the wire, such as error code
 * numbers, etc.  They must be kept in sync
 * */

#include <adlb-defs.h>

typedef adlb_datum_id turbine_datum_id;

typedef enum
{
  TD_UNSET = ADLB_DATA_UNSET,
  TD_SET = ADLB_DATA_SET
} turbine_status;

// This enum must be kept in the same order as adlb_type
typedef enum
{
  TURBINE_TYPE_NULL = 0,
  TURBINE_TYPE_INTEGER   = ADLB_DATA_TYPE_INTEGER,
  TURBINE_TYPE_FLOAT     = ADLB_DATA_TYPE_FLOAT,
  TURBINE_TYPE_STRING    = ADLB_DATA_TYPE_STRING,
  TURBINE_TYPE_BLOB      = ADLB_DATA_TYPE_BLOB,
  TURBINE_TYPE_FILE      = ADLB_DATA_TYPE_FILE,
  TURBINE_TYPE_CONTAINER = ADLB_DATA_TYPE_CONTAINER
} turbine_type;

typedef enum
{
  TURBINE_ENTRY_KEY   = ADLB_DATA_ENTRY_KEY,
  TURBINE_ENTRY_FIELD = ADLB_DATA_ENTRY_FIELD
} turbine_entry_mode;

typedef struct
{
  turbine_entry_mode type;
  char* name;
} turbine_entry;

typedef struct
{
  turbine_type type;
  turbine_datum_id id;
  turbine_status status;
  union
  {
    struct
    {
      char* path;
    } file;
    struct
    {
      turbine_entry_mode mode;
      /** type of container keys */
      turbine_type type;
      struct list members;
    } container;
    struct
    {
      long value;
    } integer;
    struct
    {
      char* value;
      int length;
    } string;
  } data;
  struct list_i listeners;
} turbine_datum;

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
  /** Unknown error */
  TURBINE_ERROR_UNKNOWN = ADLB_DATA_ERROR_UNKNOWN,
} turbine_code;

#define TURBINE_ID_NULL        ADLB_DATA_ID_NULL
#define TURBINE_SUBSCRIPT_MAX  ADLB_DATA_SUBSCRIPT_MAX
#define TURBINE_DATA_MAX       ADLB_DATA_MAX

#define turbine_string_totype adlb_data_string_totype
