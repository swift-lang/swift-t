
import assert;
import stats;

main {
    int A[];
    for (int i = 1; i <= 10; i = i + 1) {
        int B[];
        for (int j = 1; j <= i; j = j + 1) {
            trace(i, j);
            B[j] = j;
        }
        A[i] = sum_integer(B);
        assertEqual(sum_integer(B),
                    ((i + i*i)%/2) ,
                    "i=" + fromint(i) + " sum B");
    }
    trace(sum_integer(A));
    assertEqual(sum_integer(A), 220, "sum(A)");
}
