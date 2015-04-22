import sys;

// COMPILE-ONLY-TEST

main {
    // Check that declaration of y and z is visible in main block scope
    x = 1 =>
        y = 1 =>
        z = 1;
    trace(x, y, z);
}
