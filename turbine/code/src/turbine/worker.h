
/*
 * worker.h
 *
 *  Created on: Aug 16, 2013
 *      Author: wozniak
 */

#ifndef WORKER_H
#define WORKER_H

turbine_code turbine_worker_loop(Tcl_Interp* interp,
                                 void* buffer,
                                 int buffer_size,
                                 int work_type);

#endif
