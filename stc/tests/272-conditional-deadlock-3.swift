// SKIP-THIS-TEST
// THIS-TEST-SHOULD-NOT-COMPILE

main {

    int a;
    // Detect this deadlock!
    if (a + 1) {
        a = 2;
    } else {
        a = 3;
    }
}
