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

// IO.SWIFT

#ifndef IO_SWIFT
#define IO_SWIFT

@dispatch=WORKER
(void z)
printf(string fmt, int|float|string|boolean... args)
    "turbine" "0.0.2" "printf"
    [ "set <<z>> [ turbine::printf_local <<fmt>> <<args>> ]" ];

#endif // IO_SWIFT
