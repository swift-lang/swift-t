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

// STATS.SWIFT

// Container numerical aggregate functions

#ifndef STATS_SWIFT
#define STATS_SWIFT

(int result) sum_integer(int A[])
"turbine" "0.0.2" "sum_integer";
(int result) sum(int A[])
"turbine" "0.0.2" "sum_integer";

(float result) sum_float(float A[])
"turbine" "0.0.4" "sum_float";
(float result) sum(float A[])
"turbine" "0.0.4" "sum_float";

(float result) avg(int A[])
"turbine" "0.0.2" "avg";
(float result) avg(float A[])
"turbine" "0.0.2" "avg";

// Population standard deviation
(float result) std(int|float A[])
"turbine" "0.0.2" "std";

(float mean, float std) stats(int|float A[])
"turbine" "0.0.2" "stats";

(int n, float mean, float M2) statagg(int|float A[])
"turbine" "0.0.2" "statagg";

// Aggregate partial statistics
type PartialStats {
  int n; /* number of samples */
  float mean;
  float M2; /* Variance * n_samples */
}

(int n, float mean, float std) stat_combine(PartialStats A[])
"turbine" "0.0.2" "stat_combine";

#endif
