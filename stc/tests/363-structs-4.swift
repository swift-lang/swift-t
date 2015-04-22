type bob {
    int a;
    int b;
}


// Check that struct passing works ok

(bob ret) f() {
    ret.a = 1;
    ret.b = 2;
}

(bob ret) swap(bob inp) {
    ret.a = inp.b;
    ret.b = inp.a;
}

main {
    // Check they can be declared/allocated
    bob x = f();
    bob y = swap(x);

    trace(y.a, y.b);
}
