
import assert;

(int r) up (int x) {
    r = x + 1;
}

(int r) f () {
    r = 5;
}

main {
    int n = f();
    for (int i = 0; i < n; i = up(i)) {
        trace(i, "is less than", n);
        assert(i < 5, "i<5");
    }
}
