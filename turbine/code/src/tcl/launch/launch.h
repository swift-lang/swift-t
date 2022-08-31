
// Copyright 2013 University of Chicago and Argonne National Laboratory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License

// LAUNCH.H

// SWIG interface header for MPIX_Comm_launch() stuff

#pragma once

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
