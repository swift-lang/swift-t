
/**
 * log.h
 *
 *  Created on: Aug 16, 2011
 *      Author: wozniak
 */

#ifndef LOG_H
#define LOG_H

#include <stdbool.h>

#include "c-utils-config.h"

void   log_init(void);
void   log_enabled(bool b);
void   log_normalize(void);
double log_time(void);
void   log_finalize(void);

/**
   Allow user to eliminate this function call
*/
#if DISABLE_LOG==1
#define log_printf(...) ;
#else
void log_printf(char* format, ...);
#endif

#endif
