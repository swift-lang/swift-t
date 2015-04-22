// THIS-TEST-SHOULD-NOT-COMPILE

import io;

// Check that can't define conflicting global variable
global const int trace = 1;

main {
  printf("%d", trace);
}
