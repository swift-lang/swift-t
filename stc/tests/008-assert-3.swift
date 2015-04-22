
// THIS-TEST-SHOULD-NOT-RUN
// check that asserts fail as expected

import assert;

main {
    assertEqual(-1, 1, "-1 != 1"); // should fail
}
