
/*
 * worker.h
 *
 *  Created on: Aug 16, 2013
 *      Author: wozniak
 */

#ifndef WORKER_H
#define WORKER_H

// extern char* turbine_worker_command;
extern char** turbine_worker_args;
extern char*  turbine_worker_stdin_file;
extern char*  turbine_worker_stdout_file;
extern char*  turbine_worker_stderr_file;

turbine_code turbine_worker_loop(Tcl_Interp* interp,
                                 void* buffer,
                                 int buffer_size,
                                 int work_type);

#endif
