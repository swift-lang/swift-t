
#include <builtins.swift>
#include <assert.swift>
#include <string.swift>

main {
    string x = "121";  

    int a = isnum("011");
    assertEqual(1, a, "a");

    int b = isnum("4242");
    assertEqual(1, b, "b");

    int c = isnum("999999999999999999999");
    assertEqual(1, c, "c");
    
    int d = isnum(" 42");
    assertEqual(1, d, "d");
    
    int e = isnum("42 ");
    assertEqual(1, e, "e");

    int e1 = isnum(x);
    assertEqual(1, e1, "e1");

    // Fail cases 

    int f = isnum("4 2");
    assertEqual(0, f, "f");

    int g = isnum("a1");
    assertEqual(0, g, "g");

    int h = isnum("");
    assertEqual(0, h, "h");

    // isnum will not accept floating pt values.
    int i = isnum("4.2");
    assertEqual(0, i, "i");

    int j = isnum(".77");
    assertEqual(0, j, "j");

    
}
