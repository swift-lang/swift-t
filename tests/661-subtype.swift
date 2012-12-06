#include <builtins.swift>
#include <assert.swift>


// SKIP-THIS-TEST
type superint int;

main {
    int x = 0;
    superint y = 2;
    trace(x, y);
    assert(y == 2, "y == 2");
}
