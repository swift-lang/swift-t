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

// Parsers

(string t) json_type(string J, string path)
{
  t = python_persist("from turbine_helpers import *",
                     "json_type('''%s''','''%s''')" %
                     (J, path));
}

(string t) json_get(string J, string path)
{
    t = python_persist("from turbine_helpers import *",
                       "json_get('''%s''','''%s''')" %
                       (J, path));
}

(int t) json_get_int(string J, string path)
{
  s = json_get(J, path);
  t = string2int(s);
}

(float t) json_get_float(string J, string path)
{
  s = json_get(J, path);
  t = string2float(s);
}

(boolean t) json_get_boolean(string J, string path)
{
  s = json_get(J, path);
  t = string2boolean(s);
}

(int t) json_array_size(string J, string path)
{
  s = python_persist("from turbine_helpers import *",
                     "json_array_size('''%s''','''%s''')" %
                     (J, path));
  t = string2int(s);
}

(string t) json_object_names(string J, string path)
{
  t = python_persist("from turbine_helpers import *",
                     "json_object_names('''%s''','''%s''')" %
                     (J, path));
}

// Encoders

(string o) json_arrayify(string text)
{
  o = "[" + text + "]";
}

(string o) json_objectify(string text)
{
  o = "{" + text + "}";
}

(string o) json_encode_array(int|float|string|boolean... args) // OK
"turbine" "1.2.3" "json_encode_array";

(string o) json_encode_array_contents(int|float|string|boolean... args) // OK
"turbine" "1.2.3" "json_encode_array_contents";

(string o) json_encode_array_retype(string types[],
                                    int|float|string|boolean... args) // OK
"turbine" "1.2.3" "json_encode_array_retype";

(string o) json_encode_array_contents_retype(string types[],
                                             int|float|string|boolean... args) // OK
"turbine" "1.2.3" "json_encode_array_contents_retype";

(string o) json_encode_array_format(string format,
                                    int|float|string|boolean... args) // OK
"turbine" "1.2.3" "json_encode_array_format";

(string o) json_encode_array_contents_format(string format,
                                             int|float|string|boolean... args) // OK
"turbine" "1.2.3" "json_encode_array_contents_format";

(string o) json_encode_object(string names[], int|float|string|boolean... args) // OK
"turbine" "1.2.3" "json_encode_object";

(string o) json_encode_object_contents(string names[], int|float|string|boolean... args) // OK
"turbine" "1.2.3" "json_encode_object_contents";

(string o) json_encode_object_retype(string names[], string types[],
                                     int|float|string|boolean... args) // OK
"turbine" "1.2.3" "json_encode_object_retype";

(string o) json_encode_object_contents_retype(string names[], string types[],
                                              int|float|string|boolean... args) // OK
"turbine" "1.2.3" "json_encode_object_contents_retype";

(string o) json_encode_object_format(string names[], string format,
                                    int|float|string|boolean... args)
"turbine" "1.2.3" "json_encode_object_format";

(string o) json_encode_object_contents_format(string names[], string format,
                                              int|float|string|boolean... args)
"turbine" "1.2.3" "json_encode_object_contents_format";
