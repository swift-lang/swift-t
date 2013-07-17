
// SKIP-THIS-TEST
// this should work but we do not assume everyone has Python installed

import io;
import python;

main
{
  i = python("print(\"python works\")\n2+2");
  printf("i: %s", i);
}
