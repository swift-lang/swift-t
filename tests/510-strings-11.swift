
import assert;
import string;

main {
    string x = "hello world\n";
    trace(x);
    assertEqual(x, "hello world\n","");

    // Success cases
    int s = find("hello world", "worl", 0, -1);
    assertEqual(6, s, "s");

    int s1 = find("banana", "an", 0, -1);
    assertEqual(1, s1, "s1");

    int s2 = find("banana", "an", 2, -1);
    assertEqual(3, s2, "s2");

    int s3 = find("banana", "ana", 3, -1);
    assertEqual(3, s3, "s3");

    int s4 = find("banana", "an", 0, 3);
    assertEqual(1, s4, "s4");

    // Failure cases
    int f = find("hello world", "black", 0, -1);
    assertEqual(-1, f, "f");

    int f1 = find(x, "ldir", 0, -1);
    assertEqual(-1, f1, "f1");
    
    int f2 = find("banana", "ana", 3, 0);
    assertEqual(-1, f2, "f2");

    int f3 = find("banana", "ana", 4, 0);
    assertEqual(-1, f3, "f3");

}
