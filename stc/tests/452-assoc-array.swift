import assert;
import sys;

// Exercise nested arrays with mixed types
// Based on 355-nested-insert test with different types
main {
    int A[string][][boolean];

    // Try to delay insert
    A[ids(0)][id_sleep(0)][false] = 1;
    A[ids(0)][id(0)][idb_sleep(1)] = 2;
    A[ids(1)][id(0)][false] = 4;
    A[ids(0)][id(1)][false] = 5;

    //assertEqual(size(A[0]), 1, "size of A[0]");
    foreach x, i in A["0"] {
        trace("Elem", i);
        foreach y,j in x {
            trace("A[0][" + fromint(i) + "][" + booltostring(j) + "] = "
                          + fromint(y));
        }   
    }
    wait(A) {
        trace("A closed");
    }
    wait (A["0"]) {
        trace("A[0] closed");
    }
    wait (A["0"][0]) {
        trace("A[0][0] closed");
    }
    wait (A["0"][1]) {
        trace("A[0][1] closed");
    }

    int B[string][int][boolean];
    // Test nested loops
    foreach i in [0:3] {
        foreach j in [0:3] {
            foreach k in [0:1] {
                // Delay insert to force dataflow eval
                B[ids_sleep(i)][id_sleep(j)][idb_sleep(k)] = i + j + k;
            }
        }
    }
    wait (B) {
        trace("B closed");
    }
    
    int C[string][][boolean];
    // Test nested loops
    foreach i in [0:3] {
        foreach j in [0:3] {
            foreach k in [0:1] {
                // Delay insert to force dataflow eval
                C[fromint(i)][id_sleep(j)][idb_sleep(k)] = i + j + k;
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

(string o) ids_sleep (int i) {
    wait (sleep(0.05)) {
        o = fromint(i);
    }
}

(boolean o) idb_sleep (int i) {
    wait (sleep(0.05)) {
        if (i != 0) {
          o = true;
        } else {
          o = false;
        }
    }
}


(int o) id (int i) {
    o = i;
}

(boolean o) idb (int i) {
    if (i) {
        o = true;
    } else {
        o = false;
    }
}

(string o) booltostring (boolean b) {
    if (b) {
        o = "true";
    } else {
        o = "false";
    }
}

(string o) ids (int i) {
    o = fromint(i);
}
