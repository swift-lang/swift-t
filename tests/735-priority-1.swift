

main {
    foreach i in [1:100] {
        @prio=(i)sleep_trace(0.01, i);
    }
}

