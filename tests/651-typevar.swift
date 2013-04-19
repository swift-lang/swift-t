// COMPILE-ONLY-TEST

<T>
(int i) f (T in1[]) "package" "0.0.0" "f";

main {
    int i = f([1, 2, 3]);
    trace(i);
}

