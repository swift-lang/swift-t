// Check that the type definitions are processed ok

type tweedledee {
    int a;
    int b;
}

type tweedledum {
    int a;
    tweedledee x;
}

main {
    // UNSET-VARIABLE-EXPECTED
    // Check they can be declared/allocated
    tweedledee x;
    tweedledee y;
    tweedledum z;
}
