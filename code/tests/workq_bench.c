#include "workqueue.h"
#include "checks.h"

int main(int argc, char **argv)
{
  adlb_code ac;
  
  // Workaround: disable debug logging to avoid calls to MPI_WTime
  xlb_debug_enabled = false;

  ac = xlb_workq_init(1, 64, 8);
  ADLB_CHECK(ac);
}
