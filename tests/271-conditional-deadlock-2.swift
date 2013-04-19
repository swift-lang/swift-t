// THIS-TEST-SHOULD-NOT-COMPILE

main {

    int a;
    // Detect this deadlock!
    switch (a) {
    case 3:
        a = 2;
    default:
        a = 3;
    }
}

