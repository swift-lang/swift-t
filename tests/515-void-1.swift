import assert;

// test out void type
main {
    void x;
    x = make_void();
    void y = x;
    void z = y;

    wait (z) {
        trace("done!");
    }

    // Check arrays work
    void A[] = arr();
    

    wait(A[id(100)]) {
        trace("LOOKUP DONE");
    }
}

(int o) id (int i) "turbine" "0.0" [
    "set <<o>> <<i>>"
];

(void O[]) arr() {
    foreach i in [1:1000] {
        O[i] = make_void();
    }
}
