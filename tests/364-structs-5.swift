type bob {
    int a;
    int b;
}


() f (bob input) {
    //  check that struct member caching with branches works 
    // right
    if (1) {
        trace(input.a);
    } else {
        trace(input.a);
        trace(input.b);
    }
    trace(input.a);
    trace(input.b);
}

main {
    bob x;
    x.a = 1;
    x.b = 2;
    f(x);
}
