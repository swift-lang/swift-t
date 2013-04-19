
import assert;

main {

    @unroll=5
    foreach i in [1:99:2] {
        int j = i * 3 * i + 4;
        trace(i, j);
        trace("firstloop",i);
        assert(i >= i, "i >= 1");
        assert(i <= 99, "i <= 99");
    }


    @unroll=7
    foreach i in [5:58:3] {
        trace("secondloop",i);
        assert(i >= 5, "i >= 5");
        assert(i <= 56, "i <= 56");
    }


    // Don't let optimizer know the loop indices statically
    @unroll=4
    foreach i in [f(5):f(58):f(3)] {
        trace("thirdloop",i);
        assert(i >= 5, "i >= 5");
        assert(i <= 56, "i <= 56");
    }
}

(int r) f (int x) {
    r = x;
}
