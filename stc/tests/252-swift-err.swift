

main {

    switch (f() + 1) {
        case 1:
            trace("1");
        case 20:
            trace("20");
        case 1:
            // Duplicate case, should not compile
            // THIS-TEST-SHOULD-NOT-COMPILE
            trace("1");
    }
}

(int r) f() {
    r = 1;
}
