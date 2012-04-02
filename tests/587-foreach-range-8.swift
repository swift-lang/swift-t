#include <builtins.swift>


main {
    @splitdegree=8
    @sync
    foreach i in [1:100] {
        trace(i);
    }
}
