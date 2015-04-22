
import assert;
import stats;

main {
    float a[];
    int b[];
    // Launch sum first
    assertEqual(avg(a), 2.5, "avg_float");
    assertEqual(avg(b), 2.5, "avg_integer");
    assertLT(std(a) - 1.1180339887498949,
                                    0.0000001, "std_float");
    assertLT(std(b)- 1.1180339887498949,
                                    0.0000001, "std_integer");


    float m; float stdev;
    (m, stdev) = stats(a);
    assertEqual(m, avg(a), "m");
    assertEqual(stdev, std(a), "std");

    a[0] = 1.0;
    a[id(1)] = fid(2.0);
    a[5] = fid(3.0);
    a[id(242)] = 4.0;


    b[0] = id(1);
    b[id(1)] = 2;
    b[5] = id(3);
    b[id(242)] = 4;
}

(float r) fid (float x) {
    r = x;
}

(int r) id (int x) {
    r = id2(x, 10);
}

(int r) id2 (int x, int recursions) {
    if (recursions <= 0) {
        r = x;
    } else {
        r = id2(x, recursions - 1);
    }
}
