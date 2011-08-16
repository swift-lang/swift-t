
/**
 * log.h
 *
 *  Created on: Aug 16, 2011
 *      Author: wozniak
 */

#ifndef LOG_H_
#define LOG_H_

void   log_init(void);
void   log_normalize(void);
double log_time(void);
void   log_printf(char* format, ...);
void   log_finalize(void);

#endif
