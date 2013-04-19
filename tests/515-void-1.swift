// test out void type
main {
    void x;
    x = make_void();
    void y = x;
    void z = y;

    wait (z) {
        trace("done!");
    }
}
