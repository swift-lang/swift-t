
// Test out that functions can be used before they are defined

import assert;

main {
    assertEqual(f(), 1, "");
}

(int r) f () {
  r = 1;
}
