//THIS-TEST-SHOULD-NOT-COMPILE

type t {
    int a;
    int b;
}

main {
    t x;
    // Compiler should detect that x.a isn't written
    x.b = 1;
    trace(x.a);
}
