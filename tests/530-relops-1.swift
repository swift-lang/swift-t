
// Test relational operators on integers
import assert;

(int r) f () {
    r = 1;
}

main {
    // Delayed computation
    assert(f() == 1, "delayed == 1");
    assert(!(f() == 0), "delayed == 0");
    assert(f() != 0, "delayed != 0");
    assert(!(f() != 1), "delayed != 1");
    assert(f() > -1, "delayed > -1");
    assert(f() <= 23, "delayed <= 23");
    assert(!(f() < 1), "delayed < 1");
    assert(f() >= 0, "delayed >= 0");

    // Immediate (to check constant folding)
    int a = 1;
    assert(a == 1, "immediate == 1");
    assert(!(a == 0), "immediate == 0");
    assert(a != 0, "immediate != 0");
    assert(!(a != 1), "immediate != 1");
    assert(a > -1, "immediate > -1");
    assert(a <= 23, "immediate <= 23");
    assert(!(a < 1), "immediate < 1");
    assert(a >= 0, "immediate >= 0");
}
