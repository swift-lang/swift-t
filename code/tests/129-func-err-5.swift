

// THIS-TEST-SHOULD-NOT-COMPILE
main () {
    // Test typechecking with varargs
    f(1);
    f("notint");
}

() f(int args) "turbine" "0.0.2" "f";
