import assert;

// Test the auto-wrapping functionality
@pure @minmax 
(int o) max_test (int i1, int i2) "turbine" "0.0.1" [ 
    "set <<o>> [ expr max(<<i1>>, <<i2>>) ] "
];

main {
    trace(max_test(1, 2));
    assertEqual(max_test(1, 2), 2, "max_test");

    // should be optimized out - check that annotations work ok
    max_test(5, 7);
}
