import assert;

main {
    trace(2.3e10);
    trace(2.3e-10);
    assertEqual(2.3e6, 2300000.0, "pos exponent 1");
    assertEqual(2.3e-2, 0.023, "neg exponent 1");
    assertEqual(1e-4, 0.0001, "neg exponent 2");
    assertEqual(2e3 + 2000, 4000.0, "add");
}
