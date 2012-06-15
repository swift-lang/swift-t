
/*
 * server.h
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#ifndef SERVER_H
#define SERVER_H

extern double time_last_action;

adlb_code adlb_server_init(void);

int adlb_map_to_server(int worker);

adlb_code ADLBP_Server(long max_memory);

#endif
