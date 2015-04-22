// COMPILE-ONLY-TEST 

<T1, T2>
(T1 out1, T2 out2) f (T1 in1, T2 in2) "package" "0.0.0" "f";

main {
    float x;
    string y;
    x, y = f(1.0, "test");
    trace(x, y);
}

