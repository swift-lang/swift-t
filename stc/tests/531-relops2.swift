
// Test relational operators on floats
import assert;

(float r) f () {
    r = 1.0;
}

main {
    // Delayed computation
    assert(f() == 1.0, "delayed == 1.0");
    assert(!(f() == 0.0), "delayed == 0.0");
    assert(f() != 0.0, "delayed != 0.0");
    assert(!(f() != 1.0), "delayed != 1.0");
    assert(f() > -1.0, "delayed > -1.0");
    assert(f() <= 23.0, "delayed <= 23.0");
    assert(!(f() < 1.0), "delayed < 1.0");
    assert(f() >= 0.0, "delayed >= 0.0");

    // Immediate (to check constant folding
    float a = 1.0;
    assert(a == 1.0, "immediate == 1.0");
    assert(!(a == 0.0), "immediate == 0.0");
    assert(a != 0.0, "immediate != 0.0");
    assert(!(a != 1.0), "immediate != 1.0");
    assert(a > -1.0, "immediate > -1.0");
    assert(a <= 23.0, "immediate <= 23.0");
    assert(!(a < 1.0), "immediate < 1.0");
    assert(a >= 0.0, "immediate >= 0.0");
}
