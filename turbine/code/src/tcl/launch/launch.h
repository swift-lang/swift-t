#ifndef LAUNCH_H
#define LAUNCH_H

#include <mpi.h>

int launch(MPI_Comm comm, char* cmd, int argc, char** argv);
int launch_envs(MPI_Comm comm, char* cmd, int argc, char** argv,
                int envc, char** envs);

int launch_turbine(MPI_Comm comm, char* cmd, int argc, char** argv);

int launch_multi(MPI_Comm comm, int count, int* procs,
                 char** cmd,
                 int* argc, char*** argv,
                 int* envc, char*** envs,
                 char* color_setting);

/* WIP
MPI_Info info_create(void);

void info_set(MPI_Info info, char* key, char* value);

void info_free(MPI_Info info);
*/

#endif
