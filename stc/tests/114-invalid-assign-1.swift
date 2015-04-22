//THIS-TEST-SHOULD-NOT-COMPILE

(int r) f (int x) {
    x = 1; // should not be able to assign to argument
}
