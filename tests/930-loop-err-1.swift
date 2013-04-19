//THIS-TEST-SHOULD-NOT-COMPILE


main {
    int accum;
    // Invalid to use variable as loop var in two loops
    for (int i = 0, accum=0; i < 10; i = i + 1, accum=accum) {
        for (int j = 0, accum=accum; i < 10; i = i + 1, accum=accum + j) {
            trace(i, j, accum);
        }
    }
}
