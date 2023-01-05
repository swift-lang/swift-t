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

// Mathematical functions

#ifndef MATH_SWIFT
#define MATH_SWIFT

float PI = 3.14159265358979323846;
float E = 2.7182818284590452354;

@pure @builtin_op=FLOOR
(float o) floor           (float i) "turbine"  "0.0.2" "floor";
@pure @builtin_op=CEIL
(float o) ceil            (float i) "turbine"  "0.0.2" "ceil";
@pure @builtin_op=ROUND
(float o) round           (float i) "turbine"  "0.0.2" "round";
@pure @builtin_op=LOG
@pure @builtin_op=EXP
(float o) exp           (float i) "turbine"  "0.0.2" "exp";
@pure @builtin_op=SQRT
(float o) sqrt          (float i) "turbine"  "0.0.2" "sqrt";
@pure @builtin_op=IS_NAN
(boolean o) is_nan      (float i) "turbine"  "0.0.2" "is_nan";
@pure @builtin_op=IS_NAN
(boolean o) isNaN     (float i) "turbine"  "0.0.2" "is_nan";
@pure @builtin_op=ABS_INT
(int o)   abs_integer (int i)   "turbine"  "0.0.2" "abs_integer";
@pure @builtin_op=ABS_INT
(int o)   abs         (int i)   "turbine"  "0.0.2" "abs_integer";
@pure @builtin_op=ABS_FLOAT
(float o) abs_float   (float i) "turbine"  "0.0.2" "abs_float";
@pure @builtin_op=ABS_FLOAT
(float o) abs         (float i) "turbine"  "0.0.2" "abs_float";

@pure
(float o) cbrt (float i) {
  o = pow(i, 1.0/3.0);
}

@pure @builtin_op=LOG
(float o) log (float i) "turbine" "0.0.2" "log_e";
@pure @builtin_op=LOG
(float o) ln  (float x) "turbine" "0.7.0" "log_e";
@pure
(float o) log2(float i)
"turbine"  "1.0"
[
  // Save on evaluation of divisor: http://wiki.tcl.tk/819
  "set _i <<i>> ; set <<o>> [ expr {log(\\$_i) / [expr log(2)]} ]"
 ];
@pure
(float o) log10 (float x) "turbine" "0.7.0" [
  "set <<o>> [ turbine::log10_impl <<x>> ]"
];

@pure
(float o) log (float x, float base) "turbine" "0.7.0" [
  "set <<o>> [ turbine::log_base_impl <<x>> <<base>> ]"
];

@pure
(float o) sin (float x) "turbine" "0.7.0" [
  "set <<o>> [ ::tcl::mathfunc::sin <<x>> ]"
];

@pure
(float o) cos (float x) "turbine" "0.7.0" [
  "set <<o>> [ ::tcl::mathfunc::cos <<x>> ]"
];

@pure
(float o) tan (float x) "turbine" "0.7.0" [
  "set <<o>> [ ::tcl::mathfunc::tan <<x>> ]"
];

@pure
(float o) asin (float x) "turbine" "0.7.0" [
  "set <<o>> [ ::tcl::mathfunc::asin <<x>> ]"
];

@pure
(float o) acos (float x) "turbine" "0.7.0" [
  "set <<o>> [ ::tcl::mathfunc::acos <<x>> ]"
];

@pure
(float o) atan (float x) "turbine" "0.7.0" [
  "set <<o>> [ ::tcl::mathfunc::atan <<x>> ]"
];

@pure
(float o) atan2 (float y, float x) "turbine" "0.7.0" [
  "set <<o>> [ ::tcl::mathfunc::atan2 <<y>> <<x>> ]"
];

#endif
