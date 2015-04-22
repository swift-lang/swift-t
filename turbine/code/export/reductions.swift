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

// REDUCTIONS.SWIFT

// Container aggregate functions

#ifndef REDUCTIONS_SWIFT
#define REDUCTIONS_SWIFT

(float result) array_max_float(float A[])
"turbine" "0.4.0" "array_max_float";

(float result) array_min_float(float A[])
"turbine" "0.4.0" "array_min_float";

(int result[]) reduce_sum_integer(int A[][])
"turbine" "0.0.6" "reduce_sum_integer";

(string result[]) reduce_splice_string(string S[][])
"turbine" "0.0.6" "reduce_splice_string";

#endif
