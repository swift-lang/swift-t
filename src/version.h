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

/**
 *  VERSION FUNCTIONALITY
 *
 *  Created on: Dec 31, 2011
 *      Author: wozniak
 * */

#ifndef VERSION_H
#define VERSION_H

#include <stdbool.h>

typedef struct
{
  int major;
  int minor;
  int revision;
} version;

void version_init(version* v, int mjr, int mnr, int rev);

bool version_parse(version* v, const char* s);

int version_cmp(const version* v1, const version* v2);

void version_require(const char* package,
                     const version* package_version,
                     const char* dependency,
                     const version* dependency_version,
                     const version* required_version);

int version_to_string(char* output, const version* v);

#endif
