
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
