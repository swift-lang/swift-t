type tweedledee {
    int a;
    int b;
}

type tweedledum {
    int a;
    tweedledee x;
}

main {
    // Check they can be declared/allocated
    tweedledee x;
    tweedledee y;
    tweedledum z;
    
    trace(x.a);
    trace(x.b);

    trace(y.a);
    trace(y.b);

    x.a = 1234;
    x.b = 11;
    y.a = x.a + x.b;
    y.b = y.a * x.a;

    z.a = y.b;
    
    // Check that we can use longer paths
    z.x.a = 1;
    z.x.b = 2;
    trace(z.a, z.x.a, z.x.b);
}
