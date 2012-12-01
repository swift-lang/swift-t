
/*
 * server.h
 *
 *  Created on: Jun 14, 2012
 *      Author: wozniak
 */

#ifndef SERVER_H
#define SERVER_H

/** Time of last activity: used to determine shutdown */
extern double xlb_time_last_action;

/** Are we currently trying to sync with another server?
    Prevents nested syncs, which we do not support */
extern bool xlb_server_sync_in_progress;

/** Did we just get rejected when attempting to server sync? */
extern bool server_sync_retry;

/**
   When was the last time we tried to steal?  In seconds.
   Updated by steal()
 */
extern double xlb_steal_last;

adlb_code xlb_server_init(void);

int xlb_map_to_server(int worker);

// ADLB_Server prototype is in adlb.h

/**
   @param source MPI rank of allowable client:
                 usually MPI_ANY_SOURCE unless syncing
 */
adlb_code xlb_serve_one(int source);

/**
 * Serve several requests
 */
adlb_code xlb_serve_several();

adlb_code xlb_serve_server(int source);

adlb_code xlb_shutdown_worker(int worker);

bool xlb_server_check_idle_local(void);

bool xlb_server_shutting_down(void);

adlb_code xlb_server_shutdown(void);

adlb_code xlb_server_fail(int code);

/**
   Did we fail?  If so, obtain fail code.
   Given code may be NULL if caller does not require the code
 */
adlb_code xlb_server_failed(bool* aborted, int* code);

#endif
