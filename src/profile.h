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

#ifndef PROFILE_H
#define PROFILE_H

#ifndef ENABLE_PROFILE
#define ENABLE_PROFILE 1
#endif

#if ENABLE_PROFILE == 1

void profile_init(int size);
void profile_entry(double timestamp, const char* message);
void profile_write(int rank, FILE* file);
void profile_finalize(void);

#else

#define profile_init(s)     ;
#define profile_entry(m,t)  ;
#define profile_write(r,f)  ;
#define profile_finalize()  ;

#endif

#endif
