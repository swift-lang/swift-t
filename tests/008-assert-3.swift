#include "builtins.swift"
// THIS-TEST-SHOULD-NOT-RUN
// check that asserts fail ok

main {
    assertEqual(-1, 1, "-1 != 1"); // should fail
}
