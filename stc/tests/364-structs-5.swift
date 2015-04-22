type bob {
    int a;
    int b;
}


() f (bob inp) {
    //  check that struct member caching with branches works 
    // right
    if (1) {
        trace(inp.a);
    } else {
        trace(inp.a);
        trace(inp.b);
    }
    trace(inp.a);
    trace(inp.b);
}

main {
    bob x;
    x.a = 1;
    x.b = 2;
    f(x);
}
