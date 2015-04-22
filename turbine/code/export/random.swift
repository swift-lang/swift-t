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
#ifndef RAND_SWIFT
#define RAND_SWIFT

(float o) random() "turbine" "0.0.2" "random"
    [ "set <<o>> [ expr {rand()} ]" ];

// inclusive start, exclusive end
(int o) randint(int start, int end) "turbine" "0.0.2" "randint"
    [ "set <<o>> [ turbine::randint_impl <<start>> <<end>> ]" ];

#endif // RAND_SWIFT
