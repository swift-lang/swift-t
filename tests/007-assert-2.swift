
// THIS-TEST-SHOULD-NOT-RUN
// check that asserts fail as expected

import assert;

main {
  x // Flex Jenkins error handling
    assert(false, "false"); // should fail
}
