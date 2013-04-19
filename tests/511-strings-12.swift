
import assert;
import string;

main {
    string x = "hello world\n";
    string y = "banana nana\n";
    trace(x);
    assertEqual(x, "hello world\n","");

    // Success cases
    int s = string_count("hello world", "worl", 0, -1);
    assertEqual(1, s, "s");

    int s1 = string_count("banana", "an", 0, -1);
    assertEqual(2, s1, "s1");

    int s2 = string_count(y, "ana", 0, -1);
    assertEqual(2, s2, "s2");

    int s3 = string_count(y, "an", 2, -1);
    assertEqual(2, s3, "s3");

    int s4 = string_count(y, "an", 0, 3);
    assertEqual(1, s4, "s4");

    // Failure cases
    int f = string_count("hello world", "black", 0, -1);
    assertEqual(0, f, "f");

    int f1 = find(x, "ldir", 0, -1);
    assertEqual(-1, f1, "f1");
    
    int f2 = find("banana", "ana", 3, 0);
    assertEqual(-1, f2, "f2");

    int f3 = find("banana", "ana", 4, 5);
    assertEqual(-1, f3, "f3");

}
