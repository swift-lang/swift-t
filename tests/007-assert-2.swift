
// THIS- TEST-SHOULD-NOT-RUN // Flex Jenkins error handling
// check that asserts fail as expected

import assert;

main {
    assert(false, "false"); // should fail
}
