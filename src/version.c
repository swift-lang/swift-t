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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#include "src/version.h"

/**
   Initialize version v with given numbers
*/
void
version_init(version* v, int major, int minor, int revision)
{
  v->major    = major;
  v->minor    = minor;
  v->revision = revision;
}

/**
   Initialize version v by parsing given string
   E.g., "1.2.3"
*/
bool
version_parse(version* v, const char* s)
{
  int count = sscanf(s, "%i.%i.%i",
                     &v->major, &v->minor, &v->revision);
  return (count == 3);
}

/**
   The version_cmp() function compares the two versions v1 and v2.  It
   returns an integer less than, equal to, or greater than zero if v1
   is found, respectively, to be less than, to match, or be greater
   than v2
*/
int
version_cmp(const version* v1, const version* v2)
{
  // major comparison
  if (v1->major < v2->major)
    return -1;
  if (v1->major > v2->major)
    return 1;

  // minor comparison
  if (v1->minor < v2->minor)
    return -1;
  if (v1->minor > v2->minor)
    return 1;

  // revision comparison
  if (v1->revision < v2->revision)
    return -1;
  if (v1->revision > v2->revision)
    return 1;

  return 0;
}

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
void
version_require(const char* package,
                const version* package_version,
                const char* dependency,
                const version* dependency_version,
                const version* required_version)
{
  int c = version_cmp(dependency_version, required_version);
  if (c < 0)
  {
    char pvs[64];
    char rvs[64];
    char dvs[64];
    version_to_string(pvs, package_version);
    version_to_string(rvs, required_version);
    version_to_string(dvs, dependency_version);
    printf("package %s %s requires %s %s but found version: %s\n",
           package, pvs, dependency, rvs, dvs);
    exit(1);
  }
}

/**
   Obtain string representation of version number, e.g., "1.2.3"
   Inverse of version_parse()
   @param output Should point to enough space for the version string
                 64 characters is always sufficient
*/
int
version_to_string(char* output, const version* v)
{
  assert(output != NULL);

  int result = sprintf(output, "%i.%i.%i",
                       v->major, v->minor, v->revision);
  return result;
}
