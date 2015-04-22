// THIS-TEST-SHOULD-NOT-COMPILE

/*
 * Test handling when there is no way to resolve overload.
 */

<T> (T x) f() "turbine" "0.0" "f";

overloaded(int A[]) {}
overloaded(blob x) {}

overloaded(f());
