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

/** JSON.SWIFT
 *
 *  JSON functionality for Swift/T- relies on Python
 *  See turbine_helpers.py for implementations
 */

import python;

(string t) json_type(file f, string path)
{
  wait (f)
  {
    t = python_persist("from turbine_helpers import *",
                       "json_type('%s','%s')" %
                       (filename(f), path));
  }
}

(string t) json_list_length(file f, string path)
{
  wait (f)
  {
    t = python_persist("from turbine_helpers import *",
                       "json_list_length('%s','%s')" %
                       (filename(f), path));
  }
}

(string t) json_get(string J, string path)
{
    t = python_persist("from turbine_helpers import *",
                       "json_get('%s','%s')" %
                       (J, path));
}

(string t) json_dict_entries(file f, string path)
{
  wait (f)
  {
    t = python_persist("from turbine_helpers import *",
                       "json_dict_entries('%s','%s')" %
                       (filename(f), path));
  }
}
