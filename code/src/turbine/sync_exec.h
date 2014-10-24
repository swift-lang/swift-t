
/*
 * sync_exec.h
 *
 *  Created on: Oct 24, 2014
 *      Author: wozniak
 */

#ifndef SYNC_EXEC_H
#define SYNC_EXEC_H

#include <stdbool.h>

bool exec_system(const char* cmd, int* exitcode);

#endif
