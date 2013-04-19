// Check that combined declaration/assignment works

(int r) f (int x) {
    r = 5 * x + 1;
}

main {
    int x = f(2);
    int y = (f(3) + f(2)) * -2 + 2;
    int z = f((f(3) - f(1)) * 22);

    trace(x, y, z);
}
