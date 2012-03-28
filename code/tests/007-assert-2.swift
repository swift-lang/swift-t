#include "builtins.swift"
// THIS-TEST-SHOULD-NOT-RUN
// check that asserts fail ok

main {
    assert(false, "false"); // should fail
}
