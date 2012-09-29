
#include <builtins.swift>
#include <assert.swift>

main {
    int x;

    if (f() == 2) {
        x = 1;
    } else if (f() == 3) {
        x = 2;
    } else {
        x = 3;
    }

    assertEqual(x, 2, "x");
}

(int r) f() {
    r = 3;
}
