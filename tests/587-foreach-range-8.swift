
#include <builtins.swift>

main {
    @nosplit
    foreach i in [1:100] {
        trace(i);
    }
}
