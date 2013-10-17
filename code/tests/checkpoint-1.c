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

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#include <mpi.h>

#include <adlb.h>
#include <adlb-xpt.h>

#define MAX_INDEX_SIZE 512

#define CHECK(cond, fmt, args...)                                     \
  { if (!(cond)) {                                                    \
      fprintf(stderr, "CHECK FAILED: %s %s:%i\n", #cond,              \
              __FILE__, __LINE__);                                    \
      fprintf(stderr, fmt "\n", args);                                \
      exit(1); }}

void dump_bin(const void *data, int length)
{
  for (int i = 0; i < length; i++)
  {
    fprintf(stderr, "%02x", (int)((unsigned char*)data)[i]);
  }
}

void check_retrieve(const char *msg, const void *data, int length,
                    adlb_binary_data data2)
{
  if (data2.length != length)
  {
    fprintf(stderr, "%s: Retrieved checkpoint data length doesn't match: "
              "%i v %i\n", msg, data2.length, length);
    exit(1);
  }
  
  if (memcmp(data2.data, data, length) != 0)
  {
    fprintf(stderr, "%s: Retrieved checkpoint data doesn't match\n", msg);
    fprintf(stderr, "Original: ");
    dump_bin(data, length);
    fprintf(stderr, "\n");
    fprintf(stderr, "Retrieved: ");
    dump_bin(data2.data, length);
    fprintf(stderr, "\n");
    exit(1);
  }
}

void fill_rand_data(char *data, int length)
{
  // Add parity for verification?
  int parity = 0;
  for (int i = 0; i < length - 1; i++)
  {
    int x = rand();
    data[i] = (char)x;

    parity = abs(parity + x) % 2;
  }
  data[length - 1] = (char)parity;
}

bool check_parity(const char *data, int length)
{
  int parity = 0;
  for (int i = 0; i < length; i++)
  {
    parity = (parity + (int)data[i]) % 2;
  }
  return parity == 0;
}

void test1(MPI_Comm comm);

void test1_reload(MPI_Comm comm, const char *file);

int
main(int argc, char **argv)
{
  int phase;

  assert(argc == 2);
  if (strcmp(argv[1], "CREATE_XPT") == 0)
  {
    phase = 0;
  }
  else if (strcmp(argv[1], "RELOAD_XPT") == 0)
  {
    phase = 1;
  }
  else
  {
    printf("INVALID MODE: %s\n", argv[1]);
    exit(1);
  }

  int mpi_argc = 0;
  char** mpi_argv = NULL;

  MPI_Init(&mpi_argc, &mpi_argv);

  // Create communicator for ADLB
  MPI_Comm comm;
  MPI_Comm_dup(MPI_COMM_WORLD, &comm);

  adlb_code ac;

  int types[2] = {0, 1};
  int am_server;
  MPI_Comm worker_comm;
  ac = ADLB_Init(1, 2, types, &am_server, comm, &worker_comm);
  assert(ac == ADLB_SUCCESS);

  
  if (phase == 0)
  {
    ac = ADLB_Xpt_init("./checkpoint-1.xpt", ADLB_NO_FLUSH, MAX_INDEX_SIZE);
    assert(ac == ADLB_SUCCESS);
  }
  else
  {
    // Don't touch existing checkpoint for reload
    ac = ADLB_Xpt_init("./checkpoint-tmp.xpt", ADLB_PERIODIC_FLUSH,
                       MAX_INDEX_SIZE);
    assert(ac == ADLB_SUCCESS);
  }

  if (am_server)
  {
    ADLB_Server(1);
  }
  else
  {
    if (phase == 0)
    {
      test1(comm);
    }
    else
    {
      test1_reload(comm, "./checkpoint-1.xpt");
    }
  }

  ADLB_Finalize();
  MPI_Finalize();
  return 0;
}

#define TEST1_REPEATS 100
#define TEST1_VAL_SIZE 128

void test1(MPI_Comm comm)
{
  int my_rank;
  int rc = MPI_Comm_rank(comm, &my_rank);
  assert(rc == MPI_SUCCESS);
  int comm_size;
  rc = MPI_Comm_size(comm, &comm_size);
  assert(rc == MPI_SUCCESS);

  // TODO: test looking up non-existent entry

  for (int repeat = 0; repeat < TEST1_REPEATS; repeat++)
  {
    int size = TEST1_VAL_SIZE;
    char data[size];
    fill_rand_data(data, size);
    
    // Create unique key
    int key = my_rank + repeat * comm_size;

    adlb_code ac = ADLB_Xpt_write(&key, (int)sizeof(key), data, size,
                        ADLB_PERSIST, true);
    assert(ac == ADLB_SUCCESS);


    adlb_binary_data data2;
    ac = ADLB_Xpt_lookup(&key, (int)sizeof(key), &data2);
    assert(ac == ADLB_SUCCESS);
    
    check_retrieve("Test 1", data, size, data2); 
    ADLB_Free_binary_data(&data2);
  }
}

void test1_reload(MPI_Comm comm, const char *file)
{
  int my_rank;
  int rc = MPI_Comm_rank(comm, &my_rank);
  assert(rc == MPI_SUCCESS);
  int comm_size;
  rc = MPI_Comm_size(comm, &comm_size);
  assert(rc == MPI_SUCCESS);

  adlb_code ac;

  adlb_xpt_load_stats stats;

  // Running on all ranks will do redundant work, but should still be correct.
  ac = ADLB_Xpt_reload(file, &stats);
  CHECK(ac == ADLB_SUCCESS, "Error reloading from %s", file);

  // Check reload report is as expected (all entries present)
  CHECK(stats.ranks == comm_size, "Expected same number of ranks in "
        "checkpoint as current run: %i vs %i", stats.ranks, comm_size);

  for (int rank = 0; rank < stats.ranks; rank++)
  {
    adlb_xpt_load_rank_stats *rstats = &stats.rank_stats[rank];
    CHECK(rstats->loaded, "Rank %i should be loaded", rank);

    fprintf(stderr, "Rank: %i Valid: %i, Invalid: %i\n", rank,
            rstats->valid, rstats->invalid);
    CHECK(rstats->invalid == 0, "Rank %i has %i invalid "
          "records", rank, rstats->invalid);
    // Only check own ranks since ADLB servers won't have loaded any
    if (rank == my_rank)
    {
      fprintf(stderr, "%i %i\n", rstats->valid, TEST1_REPEATS);
      CHECK(rstats->valid == TEST1_REPEATS, "Rank %i should have %i "
           "valid records, but had %i", rank, TEST1_REPEATS, rstats->valid);
    }
  }

  // Check that all checkpoint entries loaded into memory.
  for (int repeat = 0; repeat < TEST1_REPEATS; repeat++)
  {
    // Create unique key
    int key = my_rank + repeat * comm_size;
    adlb_binary_data data;
    ac = ADLB_Xpt_lookup(&key, (int)sizeof(key), &data);
 
    CHECK(ac != ADLB_NOTHING, "entry with key %i not found\n", key);
    assert(ac == ADLB_SUCCESS);
   
    CHECK(data.length == TEST1_VAL_SIZE, "Value didn't have expected "
            "size %i, was %i\n", TEST1_VAL_SIZE, data.length);

    bool ok = check_parity((const char*)data.data, data.length);
    CHECK(ok, "Parity check for key %i failed\n", key);

    ADLB_Free_binary_data(&data);
  }
}
