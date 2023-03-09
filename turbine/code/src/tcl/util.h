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
 * TCL/UTIL
 *
 * Various utilities for C-based Tcl extensions
 * */

#pragma once

#include <tcl.h>

#include <stdint.h>

#include "src/turbine/turbine.h"

#define EMPTY_FLAG 0

/**
   Check that the user gave us the correct number of arguments
   objc should be equal to count.  If not, fail.
   Note that in Tcl, the command name counts as an argument
*/
#define TCL_ARGS(count_expr) {                                  \
    int count = count_expr;                                     \
    if (objc != count) {                                        \
      char* tmp = Tcl_GetStringFromObj(objv[0], NULL);          \
      printf("command %s requires %i arguments, received %i\n", \
             tmp, count, objc);                                 \
      return TCL_ERROR;                                         \
    }                                                           \
  }

/**
   Check that the user gave us the correct number of arguments
   for this subcommand
   objc should be equal to count.  If not, fail.
   Note that in Tcl, the command name counts as an argument
   For example, for command "string length s":
            command=string, objv[0]=length, objv[1]=s, count=2
*/
#define TCL_ARGS_SUB(command, count) {                                       \
    if (objc != count) {                                        \
      char* tmp = Tcl_GetStringFromObj(objv[0], NULL);          \
      printf("command " #command                                \
             " %s requires %i arguments, received %i\n",        \
             tmp, count, objc);                                 \
      return TCL_ERROR;                                         \
    }                                                           \
  }

/**
   Obtain array of long integers from Tcl list
   @param interp The Tcl interpreter
   @param list The Tcl list
   @param max The maximal output size
   @param output Where to write the output
   @param count The actual output size
*/
turbine_code turbine_tcl_long_array(Tcl_Interp* interp,
                                    Tcl_Obj* list, int max,
                                    int64_t* output, int* count);

/**
   Obtain array of string from Tcl list
   @param interp The Tcl interpreter
   @param list The Tcl list
   @param max The maximal output size
   @param output Where to write the output
   @param count The actual output size
*/
turbine_code turbine_tcl_string_array(Tcl_Interp* interp,
                                      Tcl_Obj* list, int max,
                                      char** output, int* count);

void turbine_tcl_condition_failed(Tcl_Interp* interp, Tcl_Obj* command,
                          const char* format, ...)
  __attribute__ ((format (printf, 3, 4)));

/**
   Convenience function to set name=value
 */
void turbine_tcl_set_string(Tcl_Interp* interp,
                            const char* name, const char* value);

/**
   Convenience function to set name=value
 */
void turbine_tcl_set_integer(Tcl_Interp* interp,
                             const char* name, int value);

/**
   Convenience function to get Tcl value
 */
void turbine_tcl_get_integer(Tcl_Interp* interp,
                             const char* name, int* value);

/**
   Convenience function to set name=value
 */
void turbine_tcl_set_long(Tcl_Interp* interp,
                          const char* name, long value);

/**
   Convenience function to set name=value
 */
void turbine_tcl_set_wideint(Tcl_Interp* interp, const char* name,
                             int64_t value);

/**
   Convenience function to set key=value in dict
 */
void turbine_tcl_dict_put(Tcl_Interp* interp, Tcl_Obj* dict,
                          const char* key, Tcl_Obj* value);

/**
   Convenience function to get key=value from dict
 */
void
turbine_tcl_dict_get(Tcl_Interp* interp, Tcl_Obj* dict,
                     const char* key, Tcl_Obj** value);

/**
   Convenience function to construct Tcl list of strings
 */
Tcl_Obj* turbine_tcl_list_new(int count, char const *const * strings);

/**
   Convenience function to construct Tcl list of integers
 */
Tcl_Obj* turbine_tcl_list_from_array_ints(Tcl_Interp *interp,
                                          const int* vals,
                                          int count);

/**
   Return error message for cleaner handling.
   Message is created from concatenating args.
   Return value is error code.
 */
int turbine_user_error(Tcl_Interp* interp, int nargs, Tcl_Obj *args[]);

/**
   C-friendly version of turbine_user_error.
   Accepts printf-style arguments for error message.
   Return value is error code.
 */
int turbine_user_errorv(Tcl_Interp* interp, const char* fmt, ...);

/**
   Print error message and return a Tcl error
   Requires Tcl_Interp interp and Tcl_Obj* objv in scope
 */
#define TCL_RETURN_ERROR(format, args...)                        \
  {                                                              \
    turbine_tcl_condition_failed(interp, objv[0], format, ## args); \
    return TCL_ERROR;                                            \
  }
// (set-window-width 80)
/**
   Print error message and jump to label
   Requires Tcl_Interp interp and Tcl_Obj* objv in scope
 */
#define TCL_ERROR_GOTO(label, format, args...)                   \
  {                                                              \
    turbine_tcl_condition_failed(interp, objv[0], format, ## args); \
    goto label;                                                  \
  }

/*
   Tcl checks follow.  Note that these are disabled by NDEBUG.
   Thus, they should never do anything in a correct Turbine program.
 */
#ifndef NDEBUG

#define TCL_CHECK(rc) { if (rc != TCL_OK) { return TCL_ERROR; }}

#define TCL_CHECK_GOTO(rc, label) { if (rc != TCL_OK) { goto label; }}

/**
   If rc is not TCL_OK, return a Tcl error
   Disabled by NDEBUG
 */
#define TCL_CHECK_MSG(rc, format, args...)                        \
  if (rc != TCL_OK) {                                             \
    TCL_RETURN_ERROR(format, ## args);                            \
  }

#define TCL_CHECK_MSG_GOTO(rc, label, format, args...)            \
  if (rc != TCL_OK) {                                             \
    TCL_ERROR_GOTO(label, format, ## args);                       \
  }


/**
   If condition is false, return a Tcl error
   Disabled by NDEBUG
   Requires Tcl_Interp interp and Tcl_Obj* objv in scope
 */
#define TCL_CONDITION(condition, format, args...)                \
  if (!(condition)) {                                            \
    TCL_RETURN_ERROR(format, ## args);                           \
  }

#define TCL_CONDITION_GOTO(condition, label, format, args...)    \
  if (!(condition)) {                                            \
    TCL_ERROR_GOTO(label, format, ## args);                      \
  }

#else

#define TCL_CHECK(rc) ((void)(rc));
#define TCL_CHECK_GOTO(rc, label) ((void)(rc));
#define TCL_CHECK_MSG(rc, format, args...) ((void)(rc));
#define TCL_CHECK_MSG_GOTO(rc, label, format, args...) ((void)(rc));
#define TCL_CONDITION(condition, format, args...) ((void)(condition));
#define TCL_CONDITION_GOTO(condition, label, format, args...) \
                      ((void)(condition));

#endif

#define TCL_MALLOC_CHECK(ptr) \
  TCL_CONDITION(ptr != NULL, "Allocating memory failed")

#define TCL_MALLOC_CHECK_GOTO(ptr, label) \
  TCL_CONDITION_GOTO(ptr != NULL, label, "Allocating memory failed")

/* Helper functions for specific int types */
static inline Tcl_Obj *Tcl_NewADLBInt(adlb_int_t val)
{
  return Tcl_NewWideIntObj((Tcl_WideInt)val);
}

static inline Tcl_Obj *Tcl_NewADLB_ID(adlb_datum_id val)
{
  return Tcl_NewWideIntObj((Tcl_WideInt)val);
}

static inline Tcl_Obj *Tcl_NewPtr(void *ptr)
{
  // Long is always large enough to fit pointer in
  return Tcl_NewLongObj((long)ptr);
}

#define Tcl_NewConstString(str) Tcl_NewStringObj((str), strlen(str))

/**
 * Attempt to extract int of appropriate width for ADLB
 * interp: if non-NULL, leave error message here
 */
static inline int Tcl_GetADLBInt(Tcl_Interp *interp, Tcl_Obj *objPtr,
                                 adlb_int_t *intPtr)
{
  // Sanity check for pointer conversion
  assert(sizeof(adlb_int_t) == sizeof(Tcl_WideInt));
  return Tcl_GetWideIntFromObj(interp, objPtr, (Tcl_WideInt*)intPtr);
}

/**
 * Attempt to extract ADLB data ID
 * interp: if non-NULL, leave error message here
 */
static inline int Tcl_GetADLB_ID(Tcl_Interp *interp, Tcl_Obj *objPtr,
                                 adlb_datum_id *intPtr)
{
  // Sanity check for pointer conversion
  assert(sizeof(adlb_int_t) == sizeof(Tcl_WideInt));
  return Tcl_GetWideIntFromObj(interp, objPtr, (Tcl_WideInt*)intPtr);
}

/**
 * Attempt to extract pointer stored in Tcl obj
 * interp: if non-NULL, leave error message here
 */
static inline int Tcl_GetPtr(Tcl_Interp *interp, Tcl_Obj *objPtr,
                                 void **ptr)
{
  long ptrVal;
  int rc = Tcl_GetLongFromObj(interp, objPtr, &ptrVal);
  TCL_CHECK(rc);
  *ptr = (void *) ptrVal;
  return TCL_OK;
}

/**
   Extract ADLB subscript from string (assume string subscript).
   Returned subscript will have pointer to Tcl-interp-owned string
 */
static inline int Tcl_GetADLB_Subscript(Tcl_Obj* objPtr, adlb_subscript* sub)
{
  int keylen;
  sub->key = Tcl_GetStringFromObj(objPtr, &keylen);
  if (sub->key == NULL)
  {
    // Couldn't extract string
    return TCL_ERROR;
  }
  sub->length = ((size_t)keylen) + 1; // Account for null terminator
  return TCL_OK;
}

/**
 * Parse the subscript part of an ADLB handle, ie. suffixed to an ID.
 * Handle subscripts of forms:
 * - "" => no subscript
 * - ".123.424.53" (struct indices - each introduced by .)
 * - "[5]test " - arbitrary subscript prefixed by length
 *                (to allow for future support for multiple subscripts
 *                 with binary data)
 */
static inline int
adlb_subscript_convert(Tcl_Interp* interp, Tcl_Obj* const objv[],
                       const char* str, size_t length,
                       adlb_subscript* sub, bool* alloced)
{
  if (length == 0)
  {
    *sub = ADLB_NO_SUB;
    *alloced = false;
  }
  else if (str[0] == '.')
  {
    // Include everything after '.'
    sub->key = &str[1];
    sub->length = length; // include null terminator, exclude .
    *alloced = false;
  }
  else if (str[0] == '[')
  {
    char *endstr;
    long sublen = strtol(&str[1], &endstr, 10);
    TCL_CONDITION(endstr[0] == ']' && sublen >= 0,
        "Invalid prefixed length in subscript %.*s", (int)length, str);
    const char *sub_start = &endstr[1];
    sub->key = sub_start;
    // TODO: don't handle multiple subscripts yet
    long exp_sublen = (long)length - (sub_start - str);
    TCL_CONDITION(sublen == exp_sublen,
      "Invalid subscript length: expected to be rest of string (%li), "
      "but was %li", exp_sublen, sublen);
    sub->length = (size_t)sublen;
    *alloced = false;
  }
  else
  {
    TCL_RETURN_ERROR("Invalid subscript: %.*s", (int)length, str);
  }
  return TCL_OK;
}
