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

@pure @builtin_op=FLOOR
(float o) floor           (float i) "turbine"  "0.0.2" "floor";
@pure @builtin_op=CEIL
(float o) ceil            (float i) "turbine"  "0.0.2" "ceil";
@pure @builtin_op=ROUND
(float o) round           (float i) "turbine"  "0.0.2" "round";
@pure @builtin_op=LOG
(float o) log           (float i) "turbine"  "0.0.2" "log_e";
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

#endif
