
import assert;
import string;

(int r) id (int x) {
    r = x;
}

main {
    string r = sprintf("%d %.2f %s", -123, 3.123123, "hello\nworld");
    string r2 = sprintf("%d %.2f %s", id(-123), 3.123123, "hello\nworld");
    string exp = "-123 3.12 hello\nworld";
    assertEqual(exp, r, "r");
    assertEqual(exp, r2, "r2");

}
