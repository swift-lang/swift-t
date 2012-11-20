
/*
 * steal.h
 *
 *  Created on: Aug 20, 2012
 *      Author: wozniak
 */

#ifndef STEAL_H
#define STEAL_H

#include <stdbool.h>

bool steal_allowed(void);

adlb_code steal(bool* result);

#endif
