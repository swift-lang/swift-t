
// Test relational operators on strings

import assert;

(string r) f () {
    r = "my amazing string";
}

main {
    // Delayed computation
    assert(f() == "my amazing string", "delayed == true");
    assert(!(f() == "a mediocre string"), "delayed == false");
    assert(!(f() != "my amazing string"), "delayed != true");
    assert(f() != "a mediocre string", "delayed != false");

    // Immediate (to check constant folding)
    string a = "my amazing string";
    assert(a == "my amazing string", "delayed == true");
    assert(!(a == "a mediocre string"), "delayed == false");
    assert(!(a != "my amazing string"), "delayed == true");
    assert(a != "a mediocre string", "delayed != false");
}
