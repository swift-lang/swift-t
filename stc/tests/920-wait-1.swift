
import assert;

main {
    int x = f(0);
    int y = f(1);
    wait(x, y, f(3)) {
        trace(x + y);
        assertEqual(x + y, 3, "x+y");
    }
}


(int r) f (int x) {
    r = x + 1;
}
