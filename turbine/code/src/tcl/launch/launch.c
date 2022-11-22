
// Copyright 2018 University of Chicago and Argonne National Laboratory
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

// LAUNCH.C

#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// For systems without strlcpy(), e.g., Linux:
#include <strlcpy.h>
// ExM c-utils:
#include <tools.h>

#include "MPIX_Comm_launch.h"

static void get_cpu_map_slurm(MPI_Comm comm, char* output);

int
launch(MPI_Comm comm, char* cmd, int argc, char** argv)
{
  int status = 0;
  char** argvc = malloc((argc+1)*sizeof(char*));

  for(int i = 0; i < argc; i++)
    argvc[i] = argv[i];

  argvc[argc] = NULL;
  turbine_MPIX_Comm_launch(cmd, argvc, MPI_INFO_NULL, 0, comm, &status);
  free(argvc);
  if (comm != MPI_COMM_SELF)
    MPI_Comm_free(&comm);

  return status;
}

static void special_envs(MPI_Comm comm, MPI_Info info,
                         int envc, char** envs);

MPI_Info
envs2info(MPI_Comm comm, int envc, char** envs)
{
  // printf("envs2info: envc=%i\n", envc);
  if (envc == 0)
    return MPI_INFO_NULL;

  MPI_Info info;
  MPI_Info_create(&info);
  char key[16];
  char value[16];
  strcpy(key, "envs");
  sprintf(value, "%i", envc);
  MPI_Info_set(info, key, value);

  for(int i = 0; i < envc; i++)
  {
    sprintf(key, "env%i", i);
    // printf("info set: %s=%s\n", key, envs[i]);
    MPI_Info_set(info, key, envs[i]);
  }

  special_envs(comm, info, envc, envs);

  return info;
}

static bool get_envs(int envc, char** envs, const char* key, int* index, char** result);

/**
   Handle special swift-* environment variables that we use to control MPIX_Launch
 */
static void
special_envs(MPI_Comm comm, MPI_Info info, int envc, char** envs)
{
  int index;
  char* value;
  if (get_envs(envc,envs,"swift_timeout",&index,&value))
    MPI_Info_set(info,"timeout",value);
  if (get_envs(envc,envs,"swift_launcher",&index,&value))
    MPI_Info_set(info,"launcher",value);
  if (get_envs(envc,envs,"swift_launcher_options",&index,&value))
    MPI_Info_set(info,"options",value);
  if (get_envs(envc,envs,"swift_write_hosts",&index,&value))
    MPI_Info_set(info,"write_hosts",value);
  if (get_envs(envc,envs,"swift_chdir",&index,&value))
    MPI_Info_set(info,"chdir",value);
  if (get_envs(envc,envs,"swift_output",&index,&value))
    MPI_Info_set(info,"output",value);
  if (get_envs(envc,envs,"slurm_bind",&index,&value))
  {
    char map[1024];
    get_cpu_map_slurm(comm, map);
    // strcpy(map, "0,1");
    printf("map: \"%s\"\n", map);
    MPI_Info_set(info,"slurm_bind",map);
  }
}

/**
   Look for match key in envs array.
   If found, set index and result, return true.
   Else, return false.
   IN:  envc, envs, match
   OUT: index, result
   result is a read-only pointer into envs data
 */
static bool
get_envs(int envc, char** envs, const char* match, int* index, char** result)
{
  // Need copy to include '=' as common string terminator
  char match_copy[1024];
  sprintf(match_copy, "%s=", match);

  for (int i = 0; i < envc; i++)
  {
    size_t n = strlen(envs[i]);
    char* p = &envs[i][0];
    char* q = strchr(envs[i], '=');
    if (q-p == n) return false;
    int k = q-p+1; // Length of envs key plus '='
                   // (avoid prefix matching!)
    if (strncmp(envs[i], match_copy, k) == 0)
    {
      *index = i;
      *result = q+1;
      return true;
    }
  }
  return false;
}

int launch_envs(MPI_Comm comm, char* cmd,
                int argc, char** argv,
                int envc, char** envs)
{
  int status = 0;
  char** argvc = (char**)malloc((argc+1)*sizeof(char*));
  for (int i = 0; i < argc; i++)
    argvc[i] = argv[i];

  argvc[argc] = NULL;

  MPI_Info info = envs2info(comm, envc, envs);
  turbine_MPIX_Comm_launch(cmd, argvc, info, 0, comm, &status);
  if (info != MPI_INFO_NULL)
    MPI_Info_free(&info);

  free(argvc);
  if(comm != MPI_COMM_SELF)
    MPI_Comm_free(&comm);

  return status;
}

int
launch_turbine(MPI_Comm comm, char* cmd, int argc, char** argv)
{
  int status = 0;
  char** argvc = (char**)malloc((argc+1)*sizeof(char*));
  for(int i = 0; i < argc; i++)
    argvc[i] = argv[i];
  argvc[argc] = NULL;
  MPI_Info info;
  MPI_Info_create(&info);
  MPI_Info_set(info,"launcher","turbine");
  turbine_MPIX_Comm_launch(cmd, argvc, info, 0, comm, &status);
  MPI_Info_free(&info);
  free(argvc);
  if (comm != MPI_COMM_SELF)
    MPI_Comm_free(&comm);
  return status;
}

static int get_color(int rank, MPI_Comm comm, int count, int* procs,
                     char* color_settings);

int launch_multi(MPI_Comm comm, int count, int* procs,
                 char** cmd,
                 int* argc, char*** argv,
                 int* envc, char*** envs,
                 char* color_setting)
{
  int rank;
  MPI_Comm_rank(comm, &rank);
  int color = get_color(rank, comm, count, procs, color_setting);
  // printf("launch: rank=%i color=%i %s\n", rank, color, cmd[color]);
  // fflush(stdout);
  if (color < 0)
    return 1; // Return error

  MPI_Comm subcomm;
  MPI_Comm_split(comm, color, 0, &subcomm);
  int status = 0;
  int result = launch_envs(subcomm, cmd[color],
                           argc[color], argv[color],
                           envc[color], envs[color]);
  MPI_Reduce(&status, &result, 1, MPI_INT, MPI_MAX, 0, comm);
  return status;
}

static void sanity_check(MPI_Comm comm, int count, int* procs);

static int
get_color_from_setting(int rank, MPI_Comm comm, int count, int* procs,
                       char* color_setting);

static int
get_color(int rank, MPI_Comm comm, int count, int* procs,
          char* color_setting)
{
  sanity_check(comm, count, procs);

  if (strlen(color_setting) > 0)
    return get_color_from_setting(rank, comm, count, procs,
                                  color_setting);

  // Else: Use default in-order layout
  int p = 0; // running total procs
  for (int color = 0; color < count; color++)
  {
    // printf("procs[%i]=%i\n", i, procs[i]);
    p += procs[color];
    if (rank < p)
      return color;
  }
  // Unreachable (guarded by sanity_check())
  assert(false);
  return -1;
}

static const int MAX_SPEC=1024;

static bool setting_match(int rank, char* spec, bool* match);

#include "strlcpy.h"

static int
get_color_from_setting(int rank, MPI_Comm comm, int count, int* procs,
                       char* color_setting)
{
  char  spec[MAX_SPEC];
  char* p = color_setting;
  int   color = 0;
  while (true)
  {
    char* q = strpbrk(p, ",;");
    // printf("p: '%s' q-p: %li\n", p, q-p);
    strlcpy(spec, p, q-p+1);
    bool match;
    bool c = setting_match(rank, spec, &match);
    if (!c)
      // Parse error
      return -2;
    if (match)
      return color;
    if (*q == ';')
      color++;
    if (*q == '\0')
      break;
    p = q+1;
  }
  // Error: no match found!
  return -1;
}

/**
   Result is stored in parameter match
   Returns true on parse success, false on parse error
 */
static bool
setting_match(int rank, char* spec, bool* match)
{
  // printf("spec: '%s'\n", spec);
  char* d = strchr(spec, '-');
  if (d == NULL)
  {
    // Single integer:
    int r;
    int c = sscanf(spec, "%i", &r);
    if (c != 1) return false;
    // printf("r: %i\n", r);
    *match = (rank == r);
  }
  else
  {
    // Range expression:
    int r1, r2;
    int c = sscanf(spec, "%i-%i", &r1, &r2);
    if (c != 2) return false;
    *match = (rank >= r1 && rank <= r2);
  }
  return true;
}

static void
sanity_check(MPI_Comm comm, int count, int* procs)
{
  int size;
  MPI_Comm_size(comm, &size);
  int total = 0;
  for (int i = 0; i < count; i++)
    total += procs[i];
  if (total != size)
  {
    printf("procs total=%i does not equal comm size=%i\n", total, size);
    abort();
  }
}

/// SRUN CPU MAP
//  These functions set up the arguments for SLURM cpu-bind
//  so that each running parallel task has its processes bound
//  to unique core IDs

// Rank in the main ADLB communicator:
extern int adlb_comm_rank;

static void make_cpu_map_slurm(int offset, int size, int* L);
static void cpu_list2map_slurm(int* L, int n, char* output);

/**
   Create SLURM CPU ID map for given communicator string
   @return string i,j,k,... of consecutive integers
*/
static void
get_cpu_map_slurm(MPI_Comm comm, char* output)
{
  // Rank, size in task communicator:
  int rank, size;
  MPI_Comm_rank(comm, &rank);
  MPI_Comm_size(comm, &size);

  if (rank != 0)
  {
    // Only the root rank needs to do this
    output[0] = '\0';
    return;
  }

  char* s = getenv("ADLB_RANK_LEADER");
  assert(s != NULL);
  int leader;
  int n = sscanf(s, "%i", &leader);
  assert(n == 1);
  printf("rank: %i my leader: %i\n", adlb_comm_rank, leader);

  // job: A job number on node, from 0 to how many jobs can run on the node
  int job = (adlb_comm_rank - leader) / size;

  int L[size];
  make_cpu_map_slurm(job, size, L);
  print_ints(L, size);
  cpu_list2map_slurm(L, size, output);
}

/**
    Generate the list of CPU IDs starting from the given offset
    for a parallel task of the given size
    @return Output CPU IDs in L with length=size
 */
static void
make_cpu_map_slurm(int offset, int size, int* L)
{
  int first = offset * size;
  int i = first;
  int slot = 0;
  for ( ; i < first+size; i++)
    L[slot++] = i;
}

/** Convert the CPU ID list to an srun-compatible CPU map */
static void
cpu_list2map_slurm(int* L, int n, char* output)
{
  for (int i = 0; i < n; i++)
    append(output, "%i,", L[i]);
}
