
main {
    // Simple regression test for inserting into array
    string A[][];
    A[f(0)][f(0)] = "1";

    wait (A) {
        trace(A[0][0]);
    }
}

(int o) f (int i) {
    o = i;
}
