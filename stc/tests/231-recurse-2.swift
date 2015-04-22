
// Test out mutual recursion
import assert;

main {
    assertEqual(ping(10), 10, "");
}

(int r) ping (int x) {
    if (x) {
        r = 1 + pong(x-1);
    } else {
        r = 0;
    }
}

(int r) pong (int x) {
    if (x) {
        r = 1 + ping(x-1);
    } else {
        r = 0;
    }
}
