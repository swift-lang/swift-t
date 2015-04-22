import assert;
int A[];
@splitdegree=2
foreach i in [1:100] {
  A[i] = i;
}
assertEqual(A[1], 1, "1");
assertEqual(A[50], 50, "50");
assertEqual(A[100], 100, "100");

int B[] = f();
int C[];
@splitdegree=2
foreach i in B {
  C[i] = i;
}
assertEqual(C[1], 1, "1");
assertEqual(C[50], 50, "50");
assertEqual(C[100], 100, "100");

(int r[]) f() {
    r = [1:100];
}
