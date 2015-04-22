/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */


/*
 * mpe-tools.h
 *
 *  Created on: Aug 28, 2012
 *      Author: wozniak
 *
 *  Tools to simplify use of MPE
 */

#ifndef MPE_TOOLS_H
#define MPE_TOOLS_H

#ifdef ENABLE_MPE

#include <stdio.h>

#include <mpe.h>

/**
   Declare event pairs
   Note: these names must be conventional for use with our macros
   The convention is: xlb_mpe_[svr|dmn|wkr]?_<OP>_[start|end]
*/

#define extern_declare_pair(component, function) \
  extern int xlb_mpe_##component##_##function##_start, \
  xlb_mpe_##component##_##function##_end;

// Events for servers and workers:
extern_declare_pair(all, init);
extern_declare_pair(all, finalize);

// Server handler events:
// The server is servicing some request
extern_declare_pair(svr, busy);
// Task operations:
extern_declare_pair(svr, put);
extern_declare_pair(svr, dput);
extern_declare_pair(svr, get);
extern_declare_pair(svr, sync);
extern_declare_pair(svr, steal);
extern_declare_pair(svr, shutdown);
// Data module:
extern_declare_pair(svr, create);
extern_declare_pair(svr, multicreate);
extern_declare_pair(svr, subscribe);
extern_declare_pair(svr, store);
extern_declare_pair(svr, retrieve);
extern_declare_pair(svr, insert);
extern_declare_pair(svr, lookup);

// Server daemon events (steal, shutdown):
extern_declare_pair(dmn, steal);
extern_declare_pair(dmn, sync);
extern_declare_pair(dmn, shutdown);

// Client calls:
// Task operations:
extern_declare_pair(wkr, put);
extern_declare_pair(wkr, get);
// Data module:
extern_declare_pair(wkr, unique);
extern_declare_pair(wkr, create);
extern_declare_pair(wkr, multicreate);
extern_declare_pair(wkr, subscribe);
extern_declare_pair(wkr, store);
extern_declare_pair(wkr, retrieve);
extern_declare_pair(wkr, subscribe);
extern_declare_pair(wkr, close);
extern_declare_pair(wkr, insert);
extern_declare_pair(wkr, lookup);
extern_declare_pair(wkr, exists);
extern_declare_pair(wkr, get_refcounts);

// Info event:
extern int xlb_mpe_svr_info;

void xlb_mpe_setup(void);

/** Do x only if ENABLE_MPE is set */
#define MPE(x) x;

#define MPE_INFO(e,fmt,args...) { \
  char t[32];                \
  snprintf(t, 32, fmt, ## args);      \
  printf("INFO: %s\n", t);\
  MPE_Log_event(e,0,t);}

#else

/** MPE is not enabled - noop */
#define MPE(x)
/** MPE is not enabled - noop */
#define MPE_INFO(e,msg...)

#endif

/** Log event if MPE is enabled */
#define MPE_LOG(e) MPE(MPE_Log_bare_event(e));

#endif
