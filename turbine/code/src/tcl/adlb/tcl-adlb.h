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

#ifndef TCL_ADLB_H
#define TCL_ADLB_H

#include <tcl.h>

void tcl_adlb_init(Tcl_Interp* interp);

extern int ADLB_curr_priority;

extern MPI_Comm adlb_comm;

extern int adlb_comm_size;

extern int adlb_comm_rank;

/* Return a pointer to a shared buffer */
char *tcl_adlb_xfer_buffer(uint64_t *buf_size);

int adlb_type_from_obj(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj* obj, adlb_data_type *type);

int adlb_type_from_obj_extra(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj* obj, adlb_data_type *type,
                         adlb_type_extra *extra);

int adlb_type_from_array(Tcl_Interp *interp, Tcl_Obj *const objv[],
        Tcl_Obj *const array[], int len, int *ix,
        adlb_data_type *type, adlb_type_extra *extra);

int adlb_type_extra_from_array(Tcl_Interp *interp, Tcl_Obj *const objv[],
        Tcl_Obj *const array[], int len, int *ix,
        adlb_data_type type, adlb_type_extra *extra);

int
adlb_tclobj2datum(Tcl_Interp *interp, Tcl_Obj *const objv[],
      adlb_data_type type, adlb_type_extra extra,
      Tcl_Obj *obj, bool own_pointers,
      adlb_datum_storage *result, bool *alloced);

int
adlb_tclobj2bin(Tcl_Interp *interp, Tcl_Obj *const objv[],
                adlb_data_type type, adlb_type_extra extra,
                Tcl_Obj *obj, bool canonicalize,
                const adlb_buffer *caller_buffer,
                adlb_binary_data* result);
int
adlb_datum2tclobj(Tcl_Interp *interp, Tcl_Obj *const objv[],
            adlb_datum_id id, adlb_data_type type, adlb_type_extra extra,
            const void *data, size_t length, Tcl_Obj** result);

int
adlb_parse_strictness(Tcl_Interp *interp, Tcl_Obj *obj,
      adlb_target_strictness *strictness);

int
adlb_parse_accuracy(Tcl_Interp *interp, Tcl_Obj *obj,
      adlb_target_accuracy *accuracy);

/**
  Different ways of interpreting subscripts
 */
typedef enum {
  ADLB_SUB_NONE,
  ADLB_SUB_CONTAINER, // Tcl string representation
  ADLB_SUB_STRUCT,    // Integer index, encoded as space-separated list
} adlb_subscript_kind;

/**
 * Data structures for parsing handles
 */

typedef struct {
  adlb_subscript val;
  adlb_buffer buf; // Buffer used for storing subscript, if any
} tcl_adlb_sub_parse;

typedef struct {
  adlb_datum_id id;
  tcl_adlb_sub_parse sub;
} tcl_adlb_handle;

int
ADLB_Extract_Handle(Tcl_Interp *interp, Tcl_Obj *const objv[],
        Tcl_Obj *obj, adlb_datum_id *id, const char **subscript,
        size_t *subscript_len);

// Extract only ID from handle, ignore rest
int
ADLB_Extract_Handle_ID(Tcl_Interp *interp, Tcl_Obj *const objv[],
        Tcl_Obj *obj, adlb_datum_id *id);

// Helper macros to pass interp, objv
#define ADLB_EXTRACT_HANDLE(obj, id, sub, len) \
        ADLB_Extract_Handle(interp, objv, obj, id, sub, len)

#define ADLB_EXTRACT_HANDLE_ID(obj, id) \
    ADLB_Extract_Handle_ID(interp, objv, obj, id)

/**
 * Function to parse ADLB handle
 * use_scratch: if true, uses tcl_adlb_scratch buffer until cleanup
 */
int
ADLB_Parse_Handle(Tcl_Interp *interp, Tcl_Obj *const objv[],
        Tcl_Obj *obj, tcl_adlb_handle *parse, bool use_scratch);

/**
 * Release resources allocated
 */
int
ADLB_Parse_Handle_Cleanup(Tcl_Interp *interp, Tcl_Obj *const objv[],
                          tcl_adlb_handle *parse);

/**
 * Function to parse ADLB subscript.,
 * append: if append is true, append to existing subscript in parse
 *         including a separator.  Currently assumes that
 *         subscript was initialized by ADLB_Parse_handle for memory
 *         management purposes
 * use_scratch: if true, uses tcl_adlb_scratch buffer until cleanup
 */
int
ADLB_Parse_Subscript(Tcl_Interp *interp, Tcl_Obj *const objv[],
  Tcl_Obj *obj, adlb_subscript_kind sub_kind, tcl_adlb_sub_parse *parse,
  bool append, bool use_scratch);

// Convenience macros to avoid passing interp, etc. explicitly
#define ADLB_PARSE_HANDLE(obj, parse, use_scratch) \
    ADLB_Parse_Handle(interp, objv, obj, parse, use_scratch)

#define ADLB_PARSE_HANDLE_CLEANUP(parse) \
    ADLB_Parse_Handle_Cleanup(interp, objv, parse)

#define ADLB_PARSE_SUB(obj, sub_kind, parse, append, use_scratch) \
    ADLB_Parse_Subscript(interp, objv, obj, sub_kind, parse, \
                         append, use_scratch)

#define ADLB_PARSE_SUB_CLEANUP(parse) \
    ADLB_Parse_Subscript_Cleanup(interp, objv, parse)

/**
 * Define policy for which types should automatically release write
 * refcounts on close. This is generally compound types that allow
 * mutation of inner referenced data through the outer one
 */
static inline bool
turbine_release_write_rc_policy(adlb_data_type type)
{
  return ADLB_Data_is_compound(type);
}

#endif
