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

// FILES.SWIFT

/*
  We use temporary variables named l:t for "library-temporary".
*/

#ifndef FILES_SWIFT
#define FILES_SWIFT

// Same as input file, but pretend is impure so it won't be cached
@implements=uncached_input_file
(file f) unsafe_uncached_input_file(string filename) "turbine" "0.0.2" "input_file";

@pure
(file t[]) glob(string s)
"turbine" "0.0.2" "do_glob";

@pure
@dispatch=WORKER
(string t) read(file f)
    "turbine" "0.0.2" "file_read"
    [ "set <<t>> [ turbine::file_read_local <<f>> ]" ];

@pure
@dispatch=WORKER
(file t) write(string s)
    "turbine" "0.0.2" "file_write"
    [ "turbine::file_write_local <<t>> <<s>>" ];

@pure
(string t) file_type(file f)
"turbine" "0.0.2"
[ "set <<t>> [ file type [ lindex <<f>> 0 ] ]" ];

@pure
(string t) file_type_string(string f)
"turbine" "1.0"
[ "set <<t>> [ file type <<f>> ]" ];

(boolean o) file_exists(string f)
"turbine" "0.1"
[ "set <<o>> [ file exists <<f>> ]" ];

(int o) file_mtime(string f)
"turbine" "0.1" [ "set <<o>> [ turbine::file_mtime_impl <<f>> ]" ];

@pure
(string s[]) file_lines(file f, string comment="#")
    "turbine" "0.1"
  [ "set <<s>> [ turbine::file_lines_impl <<f>> <<comment>> ] " ];

@pure
(string s[]) file_chunks(file f, string delimiter="----",
                         string comment="#")
    "turbine" "0.1"
  [ "set <<s>> [ turbine::file_chunks_impl <<f>> <<delimiter>> <<comment>> ] " ];

@pure
(string d) dirname_string(string p)
"turbine" "0.0"
[ "set <<d>> [ file dirname <<p>> ]" ];

@pure
(string d) dirname(file p)
"turbine" "0.0"
[ "set <<d>> [ file dirname <<p>> ]" ];

@pure
(string f) basename_string(string p)
"turbine" "0.0"
[ "set <<f>> [ file tail <<p>> ]" ];

@pure
(string f) basename(file p)
"turbine" "0.0"
[ "set l:t [ lindex <<p>> 0 ] ; set <<f>> [ file tail ${l:t} ]" ];

@pure
(string f) rootname_string(string p)
"turbine" "0.0"
[ "set <<f>> [ file rootname <<p>> ]" ];

@pure
(string f) rootname(file p)
"turbine" "0.0"
[ "set l:t [ lindex <<p>> 0 ] ; set <<f>> [ file rootname ${l:t} ]" ];

@pure
(string f) extension_string(string p)
"turbine" "0.0"
[ "set <<f>> [ file extension <<p>> ]" ];

@pure
(string f) extension(file p)
"turbine" "0.0"
[ "set l:t [ lindex <<p>> 0 ] ; set <<f>> [ file extension ${l:t} ]" ];

(file o)
write_array_string(string a[], int chunk)
"turbine" "1.0" "write_array_string";

(file o)
write_array_string_ordered(string a[])
"turbine" "1.0" "write_array_string_ordered";

(string s)
mktemp_string()
"turbine" "1.0" "mktemp_string";

(file o)
mktemp()
{
  o = input(mktemp_string());
}

@pure
(string f)
realpath_string(string p)
"turbine" "1.0"
[ "set <<f>> [ file normalize <<p>> ]" ];

@pure
(file f)
realpath(file p)
{
  f = input(realpath_string(filename(p)));
}

#endif // FILES_SWIFT
