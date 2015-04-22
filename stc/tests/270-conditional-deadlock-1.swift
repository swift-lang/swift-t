// THIS-TEST-SHOULD-NOT-COMPILE

main {

    int a;
    // Detect this deadlock!
    if (a) {
        a = 2;
    } else {
        a = 3;
    }
}
