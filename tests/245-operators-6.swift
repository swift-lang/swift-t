
main {
    int r = f();
    // Exercise common subexpression elimination code
    trace(r*2);
    trace(r*2);
    // Reverse arguments to test that code path
    trace(2*r);
    trace(r*2+1);
    
    // Test that we can detect flipped arguments
    trace(r > 10);
    trace(10 < r);
}

(int r) f () {
    r = 1;
}
