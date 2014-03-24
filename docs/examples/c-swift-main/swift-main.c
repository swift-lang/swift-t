
#include <stdio.h>

#include "swift-main.h"

int
swift_main(int argc, char* argv[])
{
  for (int i = 0; i < argc; i++)
    printf("arg[%i]: %s\n", i, argv[i]);

  return 0;
}
