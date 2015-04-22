import sys;


main {
    // Check that declaration of y and z is visible in main block scope
    int x = 1 =>
       int y = 1 =>
       int z = 1;
    trace(x, y, z);
}
