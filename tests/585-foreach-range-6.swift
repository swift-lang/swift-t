
import assert;

main {
    // Unroll a small loop and check that the whole thing gets inlined ok
    @unroll=10
    foreach i in [4:12] {
        trace(i+1);
        assert(i >= 4 && i <= 12, "i range");
    }
}
