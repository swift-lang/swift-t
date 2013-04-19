
// THIS-TEST-SHOULD-CAUSE-WARNING
// COMPILE-ONLY-TEST

// Leave unbound typevar S
<T, S>
(T x) f (T y) "package" "0.0.0" "f";

main {
    int x = f(1);
    trace(x);
}
