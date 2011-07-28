
#include "inlist.h"
#include "list.h"
#include "lnlist.h"

typedef long turbine_datum_id;

typedef enum
{
  TD_UNSET, TD_SET
} td_status;

typedef enum
{
  TURBINE_TYPE_FILE,
  TURBINE_TYPE_CONTAINER,
  TURBINE_TYPE_INTEGER,
  TURBINE_TYPE_STRING
} turbine_type;

typedef enum
{
  TURBINE_ENTRY_KEY,
  TURBINE_ENTRY_FIELD
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
  td_status status;
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
  struct inlist listeners;
} turbine_datum;

typedef enum
{
  TURBINE_SUCCESS,
  /** Out of memory */
  TURBINE_ERROR_OOM,
  /** Attempt to declare the same thing twice */
  TURBINE_ERROR_DOUBLE_DECLARE,
  /** Attempt to set the same datum twice */
  TURBINE_ERROR_DOUBLE_WRITE,
  /** Attempt to read an unset value */
  TURBINE_ERROR_UNSET,
  /** Data set not found */
  TURBINE_ERROR_NOT_FOUND,
  /** Parse error in number scanning */
  TURBINE_ERROR_NUMBER_FORMAT,
  /** Invalid input */
  TURBINE_ERROR_INVALID,
  /** Attempt to read/write TURBINE_ID_NULL */
  TURBINE_ERROR_NULL,
  /** Attempt to operate on wrong data type */
    TURBINE_ERROR_TYPE,
  /** Unknown error */
  TURBINE_ERROR_UNKNOWN,
} turbine_code;

#define TURBINE_ID_NULL 0

#define turbine_string_totype(type, type_string)                \
  if (strcmp(type_string, "file") == 0)                         \
    type = TURBINE_TYPE_FILE;                                   \
  else if (strcmp(type_string, "string") == 0)                  \
    type = TURBINE_TYPE_STRING;                                 \
  else if (strcmp(type_string, "integer") == 0)                 \
    type = TURBINE_TYPE_INTEGER;                                \
  else                                                          \
  {                                                             \
    printf("unknown type: %s\n", type_string);                  \
    exit(1);                                                    \
  }
