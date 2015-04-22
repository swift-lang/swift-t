
// Test out log, sqrt, exp, etc

import assert;
import math;

main {
    assertEqual(abs_float(16.0), 16.0, "abs_float(16)");
    assertEqual(abs_float(-16.0), 16.0, "abs_float(-16)");
    assertEqual(abs_integer(16), 16, "abs_integer(16)");
    assertEqual(abs_integer(-16), 16, "abs_integer(-16)");
    assertEqual(sqrt(16.0), 4.0, "sqrt(16)");
    assert(abs_float(log(16.0)- 2.772589) < 0.00001 , "log(16)");
    assert(abs_float(exp(16.0)- 8886110.5205) < 0.001 , "exp(16)");
    assert(abs_float((3.0 ** 1.8)- 7.224674056) < 0.00001 , "3.0**1.8");
    assertEqual(g(2)**8, 256.0, "2**8");
    assertEqual(g(2)**-2, 0.25, "2**-2");


    // use function to defeat constant folding
    assertEqual(abs_float(f(16.0)), 16.0, "abs_float(16)");
    assertEqual(abs_float(f(-16.0)), 16.0, "abs_float(-16)");
    assertEqual(abs_integer(g(16)), 16, "abs_integer(16)");
    assertEqual(abs_integer(g(-16)), 16, "abs_integer(-16)");
    assertEqual(sqrt(f(16.0)), 4.0, "sqrt(16)");
    assert(abs_float(log(f(16.0))- 2.772589) < 0.00001 , "log(16)");
    assert(abs_float(exp(f(16.0))- 8886110.5205) < 0.001 , "exp(16)");
    assert(abs_float(f(3.0) ** 1.8 - 7.224674056) < 0.00001 , "3.0**1.8");
    assertEqual(g(2)**8, 256.0, "2**8");
    assertEqual(g(2)**-2, 0.25, "2**-2");


    float a = f(16.0);
    int b = g(16);
    int c = g(2);
    float d = f(3.0);
    // values of a and b are known at composite runtime inside if
    if (a > 0.0 && b > 0 && c > 0) {
        assertEqual(abs_float(a), 16.0, "abs_float(16)");
        assertEqual(abs_float(-a), 16.0, "abs_float(-16)");
        assertEqual(abs_integer(b), 16, "abs_integer(16)");
        assertEqual(abs_integer(-b), 16, "abs_integer(-16)");
        assertEqual(sqrt(a), 4.0, "sqrt(16)");
        assert(abs_float(log(a)- 2.772589) < 0.00001 , "log(16)");
        assert(abs_float(exp(a)- 8886110.5205) < 0.001 , "exp(16)");
        assertEqual(c**8, 256.0, "2**8");
        assertEqual(c**-2, 0.25, "2**-2");
        assert(abs_float(pow_float(d, 1.8)- 7.224674056) < 0.00001 , "3.0**1.8");
    }
}

(float r) f (float i) {
    r = i;
}
(int r) g (int i) {
    r = i;
}
