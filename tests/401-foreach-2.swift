// SKIP-THIS-TEST
// this example will only pass once we can overlap array creation and insertion

main {
    int A[];
    A[0] = 1;
    foreach x, i in A {
       if (i < 100) {
         A[i+1] = A[i] + 1;
       }
    }
}
