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

// Assertions

#ifndef ASSERT_SWIFT
#define ASSERT_SWIFT

@assertion @builtin_op=ASSERT
(void o) assert(boolean condition, string msg) "turbine" "0.0.2" "assert";
@assertion @builtin_op=ASSERT_EQ
(void o) assertEqual(string|int|float|boolean v1, string|int|float|boolean v2,
               string msg) "turbine" "0.0.2" "assertEqual";
@assertion
(void o) assertLT(string|int|float|boolean v1, string|int|float|boolean v2,
               string msg) "turbine" "0.0.2" "assertLT";
@assertion
(void o) assertLTE(string|int|float|boolean v1, string|int|float|boolean v2,
               string msg) "turbine" "0.0.2" "assertLTE";

#endif
