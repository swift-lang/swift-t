
/**
 *  C-UTILS
 *
 *  Created on: Dec 31, 2011
 *      Author: wozniak
 *
 *  Administrative functionality for c-utils project
 * */

#ifndef C_UTILS_H
#define C_UTILS_H

#include <version.h>

static void c_utils_version(version* output)
{
  version_parse(output, "0.0.2");
}

#endif
