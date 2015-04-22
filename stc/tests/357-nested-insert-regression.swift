import assert;
import sys;

// More minimal regression test for 355
main {
    int A[][];

    // Delay insert to force dataflow eval
    C[id_sleep(0)][id_sleep(0)] = 1;
    wait (C) {
        trace("C closed");
    }
}

(int o) id_sleep (int i) {
    wait (sleep(0.05)) {
        o = i;
    }
}

(int o) id (int i) {
    o = i;
}
