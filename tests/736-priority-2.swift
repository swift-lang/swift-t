

main {
    foreach i in [1:100] {
        @prio=i trace_comp(i);
    }
}


() trace_comp (int x) {
wait (x) {
    trace(x);
}
}

