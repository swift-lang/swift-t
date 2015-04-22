// COMPILE-ONLY-TEST
main {
    // [1,2,3] could either be int[] or float[].
    // Check that compiler handles ambiguity
    trace([1,2,3][0]);
}
