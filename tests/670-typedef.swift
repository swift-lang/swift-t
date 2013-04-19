import assert;

typedef superint int;

main {
    int x = 0;
    superint y = 2;
    trace(x, y);
    assert(y == 2, "y == 2");

    // Should be able to freely convert between types
    int z = y;
    superint a = z;
    trace(a, z);
}

