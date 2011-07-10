/*
 * tools.h
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#ifndef TOOLS_H
#define TOOLS_H

/**
   Determine the length of an array of pointers
 */
int array_length(void** array);

#define append(string, args...) string += sprintf(string, ## args)

#endif /* TOOLS_H_ */
