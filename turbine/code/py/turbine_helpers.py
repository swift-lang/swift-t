# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

# TURBINE HELPERS PY

# Python helpers for JSON and mpi4py

### JSON STUFF

import json
import sys

# Type classes for comparison:
_zero  = 0
_zerof = 0.0
type_str   = "x".__class__
type_int   =  _zero.__class__
type_float =  _zerof.__class__
type_list  =  [].__class__
type_dict  =  {}.__class__
type_none  = None.__class__

def set_key_type(k):
    """ Convert to integer if possible """
    try:
        result = int(k)
    except ValueError:
        result = k
    return result

class JSON_Exception(Exception):
    pass

def json_path(J, path):
    """ Reusable function to search a JSON tree """
    J = json.loads(J)
    P = path.split(",")
    for p in P:
        if len(p) > 0:
            k = set_key_type(p)
            if k not in J:
                msg = "key '%s' not found" % k
                print("turbine: json_path(): " + msg)
                sys.stdout.flush()
                raise JSON_Exception(msg)
            J = J[k]
    return J

def json_type(J, path):
    """ Obtain the type of the entry at given path in the JSON tree """
    J = json_path(J, path)
    c = J.__class__
    if c == type_str:
        return "string"
    elif c == type_int:
        return "int"
    elif c == type_float:
        return "float"
    elif c == type_list:
        return "array"
    elif c == type_dict:
        return "object"
    elif c == type_none:
        return "null"
    else:
        raise Exception("json_type: ERROR class='%s'" % str(c))

def json_object_names(J, path):
    """ Assume dict and return all names at given path """
    J = json_path(J, path)
    L = []
    for i in J.keys():
        L.append(i)
    result = ",".join(L)
    return result

def json_array_size(J, path):
    """ Assume list and return length of it """
    J = json_path(J, path)
    return str(len(J))

def json_get(J, path):
    """ Return whatever is at the given path (usually scalar) """
    try:
        J = json_path(J, path)
    except JSON_Exception as e:
        print("turbine: json_get(): " + str(e))
        sys.stdout.flush()
        raise Exception("json_get(): failed for '%s'" % path)
    if J is None:
        return "null"
    return str(J)


### MPI4PY STUFF

# For mpi4py tasks:
task_comm = "__UNSET__"

def get_task_comm():
    from mpi4py import MPI
    import ctypes

    # print("turbine_helpers.task_comm: %i" % task_comm)
    # sys.stdout.flush()

    mpi4py_comm = MPI.Intracomm()
    if   MPI._sizeof(MPI.Comm) == ctypes.sizeof(ctypes.c_int):
        # MPICH
        comm_int = ctypes.c_int
        mpi4py_comm_ptr = comm_int.from_address(MPI._addressof(mpi4py_comm))
        mpi4py_comm_ptr.value = task_comm
    elif MPI._sizeof(MPI.Comm) == ctypes.sizeof(ctypes.c_void_p):
        # OpenMPI
        comm_pointer = ctypes.c_void_p
        mpi4py_comm = MPI.Intracomm()
        handle = comm_pointer.from_address(MPI._addressof(mpi4py_comm))
        handle.value = task_comm

    return mpi4py_comm
