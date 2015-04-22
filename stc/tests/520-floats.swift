
import assert;

main {
    float a = 3.142;
    float b = -123.0;
    trace(a*b, a+b, a-b, a/b, -a, -b);

    assertEqual(1.0 + 1.0, 2.0, "plus");
    assertEqual(2.0 * -64.0, -128.0, "mult");
    assertEqual(1.5 - 9.0, -7.5, "minus");
    assertEqual(64.0 / 4.0, 16.0, "div");
    assertEqual(-a, -3.142, "negatevar");
}
