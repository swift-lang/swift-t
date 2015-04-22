// Don't support waiting on struct (for now...)
// This should fail gracefully

type test {
    string a;
    string b;
}

main {
    test x;

    wait (x) {
        trace("HELLO WORLD");
    }
    
    x.a = "test";
    x.b = "test";
}
