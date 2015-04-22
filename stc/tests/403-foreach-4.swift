
import assert;

main {
    int res[];

    // test iterating over array ref
    foreach x, i in (f()[0]) {
        res[i] = x;
    }

    assertEqual(res[0], 1, "res[0]");
    assertEqual(res[1], 2, "res[1]");

}



(int X[][]) f () {
    int B[];
    B[0] = 1;
    B[1] = 2;
    X[0] = B;
}
