
// Test out some more string functions

import assert;
import string;

testtoint() {
    trace(toint("123"), toint("-123"));
    assertEqual(toint("123"), 123, "");
    assertEqual(toint("-123"), -123, "");
}

testfromint() {
    int x = -456;
    string xs = fromint(x);
    trace(x, xs);
    assertEqual(xs, "-456", "");
}

testsubstr() {
    // should print ana
    string x = substring("banana", 1, 3);
    trace(x);
    assertEqual(x, "ana", "");
    // should print nana
    string y = substring("banana", 2, 12321);
    trace(y);
    assertEqual(y, "nana", "");
}

main {
    testtoint();
    testfromint();
    testsubstr();
}
