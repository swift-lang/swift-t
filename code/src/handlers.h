
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

void xlb_handlers_init(void);

/**
   Just check that this is a tag known to the handlers
 */
bool xlb_handler_valid(adlb_tag tag);

adlb_code xlb_handle(adlb_tag tag, int from_rank);

void xlb_requestqueue_recheck();

#endif
