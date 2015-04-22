// THIS-TEST-SHOULD-NOT-COMPILE

<T>
(int i) f (T in1, T in2) "package" "0.0.0" "f";

main {
    int i = f(1.0, "test");
    trace(i);
}

