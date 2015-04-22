import assert;
import sys;

main {
    // Regression test for nested arrays to check
    // that closing works properly
    // Run with -O0

    // 
    int A[][][];

    // Try to delay insert
    A[id(0)][id_sleep(0)][0] = 1;
    A[id(0)][id(0)][id_sleep(1)] = 2;
    A[id(0)][id(0)][2] = 3;
    A[id(1)][id(0)][0] = 4;
    A[id(0)][id(1)][0] = 5;
    A[id(0)][id(1)][1] = 6;

    //assertEqual(size(A[0]), 1, "size of A[0]");
    foreach x, i in A[0] {
        trace("Elem", i);
        foreach y,j in x {
            trace("A[0][" + fromint(i) + "][" + fromint(j) + "] = "
                          + fromint(y));
        }   
    }
    wait(A) {
        trace("A closed");
    }
    wait (A[0]) {
        trace("A[0] closed");
    }
    wait (A[0][0]) {
        trace("A[0][0] closed");
    }
    wait (A[0][1]) {
        trace("A[0][1] closed");
    }

    int B[][][];
    // Test nested loops
    foreach i in [0:3] {
        foreach j in [0:3] {
            foreach k in [0:3] {
                // Delay insert to force dataflow eval
                B[id_sleep(i)][id_sleep(j)][id_sleep(k)] = i + j + k;
            }
        }
    }
    wait (B) {
        trace("B closed");
    }
    
    int C[][][];
    // Test nested loops
    foreach i in [0:3] {
        foreach j in [0:3] {
            foreach k in [0:3] {
                // Delay insert to force dataflow eval
                C[i][id_sleep(j)][id_sleep(k)] = i + j + k;
            }
        }
    }
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
