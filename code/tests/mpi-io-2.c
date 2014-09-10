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
  MPI-IO-2
  Make local copies of file.
*/

#include <mpi.h>

#include "src/turbine/io.h"

int
main(int argc, char** argv)
{
  char* filename = "tests/mpi-io.data";

  MPI_Init(&argc, &argv);
  bool result = turbine_io_copy_to(MPI_COMM_WORLD, filename, "/tmp");
  if (!result) return 1;
  MPI_Finalize();

  return 0;
}
