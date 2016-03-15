
import io;
import location;

import f;

#include "shutdown.h"

(void v) tasks(location root)
{
  float z = @location=root f_task(3);
  printf("z: %0.3f", z);
  v = make_void();
}

int root;
root = @par=2 f_init(10) =>
  tasks(locationFromRank(root)) =>
  @location=locationFromRank(root) f_task(SHUTDOWN);
