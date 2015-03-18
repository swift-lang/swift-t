#include "checks.h"
#include "common.h"
#include "layout.h"
#include "workqueue.h"

int main(int argc, char **argv)
{
  adlb_code ac;

  // Workaround: disable debug logging to avoid calls to MPI_WTime
  xlb_debug_enabled = false;

  xlb_s.types_size = 1;
  int comm_size = 64;

  // TODO: need way to provide hostnames
  const struct xlb_hostnames *hostnames = NULL;

  ac = xlb_layout_init(comm_size, comm_size - 1, 1,
                       hostnames, &xlb_s.layout);
  assert(ac == ADLB_SUCCESS);

  ac = xlb_workq_init(xlb_s.types_size, &xlb_s.layout);
  assert(ac == ADLB_SUCCESS);

}
