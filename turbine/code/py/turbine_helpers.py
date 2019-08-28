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

# TURBINE_HELPERS.PY

# Python helpers for JSON module

import json

# Type classes for comparison:
type_str  = "x".__class__
type_list =  [].__class__
type_dict =  {}.__class__

def set_key_type(k):
    """ Convert to integer if possible """
    try:
        result = int(k)
    except ValueError:
        result = k
    return result

# def json_path(filename, path):
#     """ Reusable function to search a JSON tree """
#     fp = open(filename, "r")
#     J = json.load(fp)

def json_path(s, path):
    """ Reusable function to search a JSON tree """
    J = json.loads(s)
    P = path.split(",")
    for p in P:
        k = set_key_type(p)
        J = J[k]
    return J

def json_type(filename, path):
    """ Obtain the type of the entry at given path in the JSON tree """
    global type_str, type_list, type_dict
    J = json_path(filename, path)
    c = J.__class__
    if c == type_str:
        return "string"
    elif c == type_list:
        return "list"
    elif c == type_dict:
        return "dict"
    else:
        raise "ERROR"

def json_dict_entries(filename, path):
    """ Assume dict and return all keys at given path """
    J = json_path(filename, path)
    L = []
    for i in J.keys():
        L.append(i)
    result = ",".join(L)
    return result

def json_list_length(filename, path):
    """ Assume list and return length of it """
    J = json_path(filename, path)
    return str(len(J))

def json_get(filename, path):
    """ Return whatever is at the given path (usually scalar) """
    J = json_path(filename, path)
    return str(J)

