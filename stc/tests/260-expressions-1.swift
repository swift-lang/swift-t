

(int r) f () {
    r = 0;
}

main {

    // Check that return value discarding works
    f();
    int x;

    x = f();
    trace(x);
}
