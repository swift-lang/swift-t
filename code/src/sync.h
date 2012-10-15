
/*
 * sync.h
 *
 *  Created on: Aug 20, 2012
 *      Author: wozniak
 */

#ifndef SYNC_H
#define SYNC_H

#include "adlb-defs.h"

/**
   Avoids server-to-server deadlocks by synchronizing with target
   server rank.  An MPI deadlock may be caused by two processes
   sending an RPC to each other simultaneously.  The sync
   functionality avoids this by doing special MPI probes and
   breaking ties by allowing the higher rank process to always win

   After returning from this, this calling process may issue one RPC
   on the target process

   This is used for all server-to-server RPCs, including Put, Store,
   Close, Steal, and Shutdown
 */
adlb_code xlb_sync(int target);

#endif
