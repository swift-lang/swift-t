#include <builtins.swift>


main {
    foreach i in [1:100] {
        @i@sleep_trace(0.01, i);
    }
}

