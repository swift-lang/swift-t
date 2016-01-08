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

void
version_init(version* v, int major, int minor, int revision)
{
  v->major    = major;
  v->minor    = minor;
  v->revision = revision;
}

bool
version_parse(version* v, const char* s)
{
  int count = sscanf(s, "%i.%i.%i",
                     &v->major, &v->minor, &v->revision);
  return (count == 3);
}

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

int
version_to_string(char* output, const version* v)
{
  assert(output != NULL);

  int result = sprintf(output, "%i.%i.%i",
                       v->major, v->minor, v->revision);
  return result;
}
