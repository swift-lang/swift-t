
// THIS-TEST-SHOULD-NOT-RUN
// check that asserts fail as expected

#include <builtins.swift>
#include <swift/assert.swift>

main {
    assert(false, "false"); // should fail
}
