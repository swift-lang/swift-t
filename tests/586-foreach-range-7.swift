
import assert;

main {
    // Unroll a small loop and check that the whole thing gets inlined ok
    @unroll=2
    foreach i in [4:12:6] {
        @unroll=50
        foreach j in [i:i+5] {
            trace(j+1);
            assert(j >= 4 && j <= 15, "j range");
        }
    }
}
