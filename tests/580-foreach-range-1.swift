
#include <builtins.swift>
#include <assert.swift>

main {
    foreach x in [1:10] {
        trace(x);
        assert(x >= 1, ">= 1");
        assert(x <= 10, "<= 10");
    }
}
