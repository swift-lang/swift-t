

main {
    int A[] = [35:1000];

    @sync
    @splitdegree=128
    @leafdegree=100
    foreach x, i in A {
        trace(x, i);
    }

    @sync
    @splitdegree=128
    @leafdegree=100
    foreach x in A {
        trace(x);
    }
}
