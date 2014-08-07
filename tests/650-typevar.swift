
// COMPILE-ONLY-TEST

// Leave unbound typevar S, should not cause problems
<T, S>
(T x) f (T y) "package" "0.0.0" "f";

main {
    int x = f(1);
    trace(x);
}
