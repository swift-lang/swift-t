
/*
 * steal.h
 *
 *  Created on: Aug 20, 2012
 *      Author: wozniak
 */

#ifndef STEAL_H
#define STEAL_H

#include <stdbool.h>

/**
   Are there any other servers?
   Are we allowed to steal yet?
 */
bool steal_allowed(void);

/**
   Issue sync() and steal.
   @return result true if stole something, else false
 */
adlb_code steal(bool* result);

#endif
