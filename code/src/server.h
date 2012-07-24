
/*
 * server.h
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#ifndef SERVER_H
#define SERVER_H

extern double time_last_action;

adlb_code xlb_server_init(void);

int xlb_map_to_server(int worker);

// ADLB_Server prototype is in adlb.h

adlb_code xlb_serve_one(void);

adlb_code xlb_shutdown_worker(int worker);

bool xlb_server_check_idle_local(void);

bool xlb_server_shutting_down(void);

adlb_code xlb_server_shutdown(void);

#endif
