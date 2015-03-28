
import assert;
import string;

main {
    string x = "hello world\n";
    string y = "banana nana\n";
    trace(x);
    assertEqual(x, "hello world\n","");

    // Success cases
    string s = replace("hello world", "world", "folks", 0);
    assertEqual("hello folks", s, "s");

    string s1 = replace("banana", "an", "ah", 0);
    assertEqual("bahana", s1, "s1");

    string s1a = replaceAll("banana", "an", "..", 0, 4);
    assertEqual(s1a, "b..ana", "s1a");

    string s2 = replace(y, "ana", "i", 0);
    assertEqual("bina nana\n", s2, "s2");

    string s3 = replaceAll("banana", "an", "ah");
    assertEqual("bahaha", s3, "s3");

    string s4 = replace_all(y, "ana", "i", 0);
    assertEqual("bina ni\n", s4, "s4");
    
    string s4a = replace_all(y, "ana", "i", 2);
    assertEqual("bani ni\n", s4a, "s4a");

    // Failure cases
    string s5 = replace("hello world", "", "folks", 0);
    assertEqual("hello world", s5, "s5");

    string s6 = replace("banana", "an", "ah", 5);
    assertEqual("banana", s6, "s6");

    string s7 = replace("banana", "", "ah", 5);
    assertEqual("banana", s7, "s7");

    string s8 = replace_all("banana", "an", "anana", 0);
    assertEqual("bananaananaa", s8, "s8");

}
