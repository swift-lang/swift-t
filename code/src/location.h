
/*
 * location.h
 *
 *  Implements hostmap and rankmap features.
 *
 *  Created on: Feb 4, 2015
 *      Author: wozniak
 */

#ifndef LOCATION_H
#define LOCATION_H

bool xlb_location_init(bool am_server);
void xlb_location_finalize(void);

const char *xlb_rankmap_lookup(int rank);

#endif
