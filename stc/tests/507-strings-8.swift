
// Regression test for octal escape code bug
import assert;
main {
    // Output hello world in red
    string oct = "\033[1;31m" + "hello world" + "\033[0m";
    trace(oct);

    // Test hex as well
    string hex = "\x1b[1;31m" + "hello world" + "\x1b[0m";
    assertEqual(hex, oct, "hex == oct");
}
