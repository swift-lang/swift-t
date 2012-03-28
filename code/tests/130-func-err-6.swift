//THIS-TEST-SHOULD-NOT-COMPILE
// Don't support polymorphic input arguments in composite functions
main {
    f(1);
}

f (int|float x) {
    trace(x);
}
