
import assert;
import random;

foreach i in [1:100] {
    // use r() to force use of futures
    float x = r();
    trace("check future random()",i,x);
    assert(x <= 1.0, "future x<=1.0");
    assert(x >= 0.0, "future x>=0.0");

    // use directly to allow local ops
    float y = random();
    trace("check local random()",i,y);
    assert(y <= 1.0, "local y<=1.0");
    assert(y >= 0.0, "local y>=0.0");


    int a = ri(i, 2*i);
    trace("check future randint()",i,2*i, a);
    assert(a < 2*i, "future a<2*i");
    assert(a >= i, "future a>=i");

    int b = randint(i, 2*i);
    trace("check future randint()",i,2*i, b);
    assert(b < 2*i, "future b<2*i");
    assert(b >= i, "future b>=i");

    // check to make sure we're not doing common subexpression
    // elimination on y and z
    float z = random();
    int z2 = randint(i, 2*i);
    trace(z);
    trace(z2);
}
// Check these aren't optimized out
random();
randint(2, 20);


(float res) r() {
    res = random();
}

(int i) ri(int lo, int hi) {
    i = randint(lo, hi);
}

