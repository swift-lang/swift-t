
import assert;
import string;

main {
    string x = "hello world\n";

    string S[];

    S[0] = x;
    S[1] = x;
    S[2] = strcat(x, strcat(x, x));

    // Check polymorphism works ok with trace
    trace(1, S[0], S[1], S[2], 2);

    assertEqual(S[0], "hello world\n", "");
    assertEqual(S[2], "hello world\nhello world\nhello world\n", "");
}
