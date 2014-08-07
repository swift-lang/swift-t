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

// STRING.SWIFT

#ifndef STRING_SWIFT
#define STRING_SWIFT

@pure
(int n) strlen(string s)
"turbine" "0.0.2"
[ "set <<n>> [ string length <<s>> ]" ];

@pure @builtin_op=SUBSTRING
(string o) substring(string s, int start, int length)
"turbine" "0.0.2" "substring";

@pure
(string t[]) split(string s, string delimiter)
"turbine" "0.0.2" "split"
[ "set <<t>> [ turbine::split_impl <<s>> <<delimiter>> ]" ];

@builtin_op=SPRINTF
(string o) sprintf(string fmt, int|float|string|boolean... args)
"turbine" "0.0.2" "sprintf";

/* find: returns first index of substring in string, or -1 if not found */
@pure
(int o) find(string s, string substring, int start_index, int end_index)
  "turbine" "0.0.1" "find"
  [ "set <<o>> [ turbine::find_impl <<s>> <<substring>> <<start_index>> <<end_index>> ]" ];

/* string_count: number of non-overlapping occurences of substring in string */
@pure
(int o) string_count(string s, string substring, int start_index, int end_index)
  "turbine" "0.0.1" "count"
  [ "set <<o>> [ turbine::count_impl <<s>> <<substring>> <<start_index>> <<end_index>> ]" ];

/* returns true if string is a decimal integer within range of Swift's
  int type */
@pure
(boolean o) isint(string s)
  "turbine" "0.0.1" "isint"
  [ "set <<o>> [ turbine::isint_impl <<s>> ]" ];

/* replace first occurence of substring with rep_string */
@pure
(string o) replace (string s, string substring, string rep_string, int start_index)
  "turbine" "0.0.1" "replace"
  [ "set <<o>> [ turbine::replace_impl <<s>> <<substring>> <<rep_string>> <<start_index>> ]" ];

/* replace all occurences of substring with rep_string */
@pure
(string o) replace_all (string s, string substring, string rep_string, int start_index)
  "turbine" "0.0.1" "replace_all"
  [ "set <<o>> [ turbine::replace_all_impl <<s>> <<substring>> <<rep_string>> <<start_index>> ]" ];

@pure
(string o) trim (string s)
  "turbine" "0.0.1"
  [ "set <<o>> [ string trim <<s>> ]" ];

@pure
(int h) hash (string s)
  "turbine" "0.0.1"
  [ "set <<h>> [ c_utils::hash <<s>> ]" ];

@pure
(string s) string_from_floats(float F[]) {
  // Assume internal repr give correctly formatted floats
  s = string_join(array_repr(F), ",");
}

@pure (string s) string_join(string A[], string separator)
  "turbine" "0.4.0" // "string_join"
  [ "set <<s>> [ turbine::string_join_impl <<A>> <<separator>> ]" ];

#endif
