
import assert;

(int r) f (int a, int b) {
  wait(a, b) {
    r = a + b;
  }
}

main {
    int a = f(1, 2);
    int b = f(f(1,1), f(2,2));

    assertEqual(a, 3, "a");
    assertEqual(b, 6, "b");
}
