
/*
 * handlers.h
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 *
 *  ADLB Server: RPC handlers
 */

#ifndef HANDLERS_H
#define HANDLERS_H

#include <stdbool.h>

#include "messaging.h"

void handlers_init(void);

bool handler_valid(adlb_tag tag);

adlb_code handle(adlb_tag tag, int from_rank);

#endif
