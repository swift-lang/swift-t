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

/**
   Initialize version v with given numbers
*/
void version_init(version* v, int mjr, int mnr, int rev);

/**
   Initialize version v by parsing given string
   E.g., "1.2.3"
*/
bool version_parse(version* v, const char* s);

/**
   The version_cmp() function compares the two versions v1 and v2.  It
   returns an integer less than, equal to, or greater than zero if v1
   is found, respectively, to be less than, to match, or be greater
   than v2
*/
int version_cmp(const version* v1, const version* v2);

/**
   Automate version comparison
   Requires that dependency_version meets required_version
   If the version check fails, this calls exit(1)
   @param package Name of the calling package
                  Used in the error message
   @param package_version Version of the calling package
                  Used in the error message
   @param dependency Name of the required package
                     Used in the error message
   @param dependency_version Actual version of the required package
   @param required_version   Required version of the required package
 */
void version_require(const char* package,
                     const version* package_version,
                     const char* dependency,
                     const version* dependency_version,
                     const version* required_version);

/**
   Obtain string representation of version number, e.g., "1.2.3"
   Inverse of version_parse()
   @param output Should point to enough space for the version string
                 64 characters is always sufficient
*/
int version_to_string(char* output, const version* v);

#endif
