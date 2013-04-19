
import assert;

main {
    boolean x = 1 == 1;
    trace(x);

    assert(x, "x");
    assertEqual(!x, false, "!");

    boolean y = true;

    trace(x && y);
    assert(x && y, "x && y");

    boolean z = y;

    boolean a = x == y;
    assert(a, "a");

    boolean b = x != y;

    assert(!b, "!b");
}
