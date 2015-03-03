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

// Swift/Turbine builtins defined here

// HACK: have preprocessor ignore this: now automatically included
#define BUILTINS_SWIFT

#ifndef BUILTINS_SWIFT
#define BUILTINS_SWIFT

// External type
type external void;

@pure
(void o) makeVoid () "turbine" "0.0.2" "make_void";
@pure
(void o) make_void () "turbine" "0.0.2" "make_void";

// Location values
// TODO: naming? convert to enum?
LocationStrictness HARD = LocationStrictness("HARD");
LocationStrictness SOFT = LocationStrictness("SOFT");

LocationAccuracy RANK = LocationAccuracy("RANK");
LocationAccuracy NODE = LocationAccuracy("NODE");

// Arithmetic
@pure @minmax @builtin_op=POW_INT
(float o) pow_integer(int base, int exponent) "turbine" "0.0.2" "pow_integer";
@pure @minmax @builtin_op=POW_INT
(float o) pow(int base, int exponent) "turbine" "0.0.2" "pow_integer";
@pure @builtin_op=POW_FLOAT
(float o) pow_float(float base, float exponent) "turbine" "0.0.2" "pow_float";
@pure @builtin_op=POW_FLOAT
(float o) pow(float base, float exponent) "turbine" "0.0.2" "pow_float";

// This is used by the string+ concatenation operator
// Take strings, and automatically convert numeric to string
@pure @builtin_op=STRCAT
(string o) strcat(string|int|float... args) "turbine" "0.0.2" "strcat";

@pure
(int n) length(string s)
"turbine" "0.0.2"
[ "set <<n>> [ string length <<s>> ]" ];


// This is used by the string/ directory catenation operator
@pure @builtin_op=DIRCAT
(string o) dircat(string... args) "turbine" "0.0.2" "dircat";

@pure @commutative @builtin_op=XOR
(boolean o) xor (boolean i1, boolean i2) "turbine" "0.0.2" "neq_integer";

// Min/max variants
@pure @minmax @builtin_op=MAX_INT
(int o) max_integer(int i1, int i2) "turbine" "0.0.2" "max_integer";
@pure @minmax @builtin_op=MAX_INT
(int o) max(int i1, int i2) "turbine" "0.0.2" "max_integer";
@pure @minmax @builtin_op=MIN_INT
(int o) min_integer(int i1, int i2) "turbine" "0.0.2" "min_integer";
@pure @minmax @builtin_op=MIN_INT
(int o) min(int i1, int i2) "turbine" "0.0.2" "min_integer";
@pure @minmax @builtin_op=MAX_FLOAT
(float o) max_float(float i1, float i2) "turbine" "0.0.2" "max_float";
@pure @minmax @builtin_op=MAX_FLOAT
(float o) max(float i1, float i2) "turbine" "0.0.2" "max_float";
@pure @minmax @builtin_op=MIN_FLOAT
(float o) min_float(float i1, float i2) "turbine" "0.0.2" "min_float";
@pure @minmax @builtin_op=MIN_FLOAT
(float o) min(float i1, float i2) "turbine" "0.0.2" "min_float";

// Conversions to strings
@pure @builtin_op=INTTOSTR
(string o) fromint(int i)  "turbine" "0.0.2" "fromint";
@pure @builtin_op=INTTOSTR
(string o) toString(int i)  "turbine" "0.0.2" "fromint";
@pure @builtin_op=FLOATTOSTR
(string o) fromfloat(float i)  "turbine" "0.0.2" "fromfloat";
@pure @builtin_op=FLOATTOSTR
(string o) toString(float i)  "turbine" "0.0.2" "fromfloat";

// Parsing of strings
@pure
(int o) toint(string i) {
  o = parseInt(i, 10);
}
@pure @builtin_op=PARSE_INT
(int o) parseInt(string i, int base=10) "turbine" "0.0.2" "parse_int";
@pure @builtin_op=PARSE_FLOAT
(float o) tofloat(string i)  "turbine" "0.0.2" "tofloat";
@pure @builtin_op=PARSE_FLOAT
(float o) parseFloat(string i)  "turbine" "0.0.2" "tofloat";

// Numeric conversions
@pure @builtin_op=INTTOFLOAT
(float o) itof(int i) "turbine"  "0.0.2" "itof";
@pure @builtin_op=INTTOFLOAT
(float o) toFloat(int i) "turbine"  "0.0.2" "itof";
@pure @builtin_op=FLOATTOINT
(int o) toInt(float i) "turbine" "0.0.2" "floattoint";

// I/O
(void o) trace (int|float|string|boolean... args) "turbine" "0.0.2" "trace"
    [ "turbine::trace_impl <<args>>" ];
(void o) sleep_trace (float secs, int|float|string|boolean... args) "turbine" "0.0.2"
                                                            "sleep_trace";

// Container operations
@pure @implements=range
(int res[]) range(int start, int end) "turbine" "0.0.2" "range"
  [ "set <<res>> [ turbine::build_range_dict <<start>> <<end>> 1 ]" ];
@pure @implements=range_step
(int res[]) range_step(int start, int end, int step) "turbine" "0.0.2" "range_step"
  [ "set <<res>> [ turbine::build_range_dict <<start>> <<end>> <<step>> ]" ];
@pure @implements=range_float
(float res[]) range_float(float start, float end) "turbine" "0.0.2" "range_float"
  [ "set <<res>> [ turbine::build_range_float_dict <<start>> <<end>> 1.0 ]" ];
@pure @implements=range_float_step
(float res[]) range_float_step(float start, float end, float step) "turbine" "0.0.2" "range_float_step"
  [ "set <<res>> [ turbine::build_range_float_dict <<start>> <<end>> <<step>> ]" ];
@pure @implements=size
<T> (int n) size(T A[]) "turbine" "0.0.5" "container_size";
@pure @implements=contains
<K, V> (boolean o) contains(V A[K], K key) "turbine" "0.0.5" "contains";

<K, V> (boolean o) exists(V A[K], K key) "turbine" "0.7.0" "exists";

@pure @implements=size
<T> (int n) bag_size(bag<T> B) "turbine" "0.0.5" "container_size"
  [ "set <<n>> [ turbine::container_size_local <<B>> 1 ]" ];

@pure @implements=size
<T> (int n) bagSize(bag<T> B) "turbine" "0.0.5" "container_size"
  [ "set <<n>> [ turbine::container_size_local <<B>> 1 ]" ];

// Updateable variables
(updateable_float o) init_updateable(float i) "turbine" "0.0.2" "init_updateable";

// Information about cluster
// pure because it won't change during program execution
@pure
(int n) adlb_servers() "turbine" "0.0.2" "adlb_servers_future"
    [ "set <<n>> [ turbine::adlb_servers ]" ];

// Do not optimize this- it is for synchronization tests
(int o) zero(void v) "turbine" "0.0.4" "zero";

/*
 deprecated: engines no longer exist
 */
@pure
(int n) turbine_engines() "turbine" "0.0.2"
    [ "set <<n>> 0" ];
@pure
(int n) turbine_workers() "turbine" "0.0.2" "turbine_workers_future"
    [ "set <<n>> [ turbine::turbine_workers ]" ];

// Basic file ops

// filename has a special implementation hardcoded in compiler
@pure @stc_intrinsic=FILENAME
(string n) filename(file x) "turbine" "0.0.2" "";

@pure @implements=input_file
(file f) input(string filename) "turbine" "0.0.2" "input_file" [
  "turbine::input_file_local <<f>> <<filename>>"
];

@pure @implements=input_file
(file f) input_file(string filename) "turbine" "0.0.2" "input_file" [
  "turbine::input_file_local <<f>> <<filename>>"
];

@pure @implements=input_url
(url f) input_url(string url) "turbine" "0.0.2" "input_url" [
  "turbine::input_url_local <<f>> <<url>>"
];

@pure @stc_intrinsic=FILENAME
(string n) urlname(url x) "turbine" "0.0.2" "";

// Substitute for C ternary operator:
@pure
(int o) ternary(boolean b, int i1, int i2) "turbine" "0.0.2"
[ "if { <<b>> } { set <<o>> <<i1>> } else { set <<o>> <<i2>> }" ];

// Get internal representation of type
@pure
<T> (string o) repr(T i) "turbine" "0.4.0" [
  "set <<o>> <<i>>" // Use Tcl string conversion
];

@pure
<T> (string O[]) array_repr(T I[]) "turbine" "0.4.0" [
  "set <<O>> <<I>>" // Use Tcl string conversion
];

// Implement % operator
@builtin_op=SPRINTF
(string o) __sprintf_op__(string fmt, int|float|string|boolean... args)
"turbine" "0.0.2" "sprintf";

#endif
