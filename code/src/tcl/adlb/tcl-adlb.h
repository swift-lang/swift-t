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

extern int adlb_comm_rank;

/* Return a pointer to a shared buffer */
char *tcl_adlb_xfer_buffer(int *buf_size);

int type_from_obj(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj* obj, adlb_data_type *type);

int type_from_obj_extra(Tcl_Interp *interp, Tcl_Obj *const objv[],
                         Tcl_Obj* obj, adlb_data_type *type,
                         bool *has_extra, adlb_type_extra *extra);


int
adlb_data_to_tcl_obj(Tcl_Interp *interp, Tcl_Obj *const objv[], adlb_datum_id id,
                adlb_data_type type, const adlb_type_extra *extra,
                const void *data, int length, Tcl_Obj** result);

int
tcl_obj_to_adlb_data(Tcl_Interp *interp, Tcl_Obj *const objv[],
                adlb_data_type type, const adlb_type_extra *extra,
                Tcl_Obj *obj, const adlb_buffer *caller_buffer,
                adlb_binary_data* result);

#endif
