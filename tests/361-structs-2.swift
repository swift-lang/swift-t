// Check that the type definitions are processed ok

type bob {
    int a;
    int b;
}

main {
    // Check they can be declared/allocated
    bob x;

    // Check we can store elements
    x.a = 1;
    x.b = 2;

    // Check we can look up elements
    trace(x.a);
    trace(x.b);
}
