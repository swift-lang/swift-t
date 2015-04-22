
// Test the infinity and NaN values

import assert;

main {
    float x = inf;
    float y = -inf;

    assert(x > y, "inf > -inf");
    assert(x > 0.0, "inf > 0");
    assert(y < 0.0, "-inf < 0");
    assert(inf == inf, "inf == inf");
    assert(inf * 2.0 == inf, "inf * 2 == inf");
    assert(inf + 20000000000.0 == inf, "inf + alot == inf");

    // TODO: TCL's behaviour is to throw an error if a NaN is encountered
    // in almost all cases, so the below statements will crash the program
    //float a = NaN;
    // trace(a+1.0);
    // assert(!(NaN == 0.0), "compare NaN");
    //assert(is_nan(NaN), "is_nan");
}
