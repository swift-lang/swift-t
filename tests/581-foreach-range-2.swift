
// Check that range can support calculated vals

import assert;

main {
    foreach x in [1:f()] {
        trace(x);
        assert(x >= 1, ">= 1");
        assert(x <= 10, "<= 10");
    }
}

(int r) f() {
    r = 10;
}
