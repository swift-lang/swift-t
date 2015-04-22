type tweedledee {
    int a;
    int b;
}

type tweedledum {
    int a;
    tweedledee x;
}

(tweedledum y) makeme () {
    y.a = y.x.a;
    
    // Check that we can use longer paths
    y.x.a = 1;
    y.x.b = 2;
}

() traceme (tweedledum y) {
    trace(y.x.a, y.x.b);
}

() traceme2 (tweedledum x) {
    trace(x.a);
}

() traceme3 (tweedledum y) {
    trace(y.a, y.x.a, y.x.b);
}

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

z = makeme();
traceme(z);
traceme2(z);
traceme3(z);
