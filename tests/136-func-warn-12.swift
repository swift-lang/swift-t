// COMPILE-ONLY-TEST
// THIS-TEST-SHOULD-CAUSE-WARNING

(int x) f (int y) {
    x = y;
}

main {
    // Check that @ is ignored but causes warning
    int x = @f(2);
}
