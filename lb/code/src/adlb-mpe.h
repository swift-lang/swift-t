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
 * adlb-mpe.h
 *
 *  Created on: Jun 11, 2012
 *      Author: wozniak
 */

#ifndef ADLB_MPE_H
#define ADLB_MPE_H

// MPE variables for server profiling
#if ADLB_MPE_ENABLED==1
#include <mpe.h>

#define MPE_SVR_LOGGING 1

//extern int mpe_svr_put_start, mpe_svr_put_end;
//extern int mpe_svr_create_start, mpe_svr_create_end;
//extern int mpe_svr_store_start, mpe_svr_store_end;
//extern int mpe_svr_retrieve_start, mpe_svr_retrieve_end;
//extern int mpe_svr_subscribe_start, mpe_svr_subscribe_end;
//extern int mpe_svr_close_start, mpe_svr_close_end;
//extern int mpe_svr_unique_start, mpe_svr_unique_end;
//extern int mpe_svr_reserve_start, mpe_svr_reserve_end;
//extern int mpe_svr_get_start, mpe_svr_get_end;

// #define MPE_LOG_EVENT(e) { printf("e: %i\n", e); MPE_Log_event(e,0,NULL); }

#else

#define MPE_LOG_EVENT(e) 0;

#endif

#endif
