
// THIS-TEST-SHOULD-NOT-RUN
// check that asserts fail as expected

#include <builtins.swift>
#include <assert.swift>

main {
    assertEqual(-1, 1, "-1 != 1"); // should fail
}
