
#include <builtins.swift>
#include <assert.swift>
#include <string.swift>

main {
    string x = "121";  

    boolean a = isnum("011");
    assertEqual(1, a, "a");

    boolean b = isnum("4242");
    assertEqual(1, b, "b");

    boolean c = isnum("999999999999999999999");
    assertEqual(1, c, "c");
    
    boolean d = isnum(" 42");
    assertEqual(1, d, "d");
    
    boolean e = isnum("42 ");
    assertEqual(1, e, "e");

    boolean e1 = isnum(x);
    assertEqual(1, e1, "e1");

    // Fail cases 

    boolean f = isnum("4 2");
    assertEqual(0, f, "f");

    boolean g = isnum("a1");
    assertEqual(0, g, "g");

    boolean h = isnum("");
    assertEqual(0, h, "h");

    // isnum will not accept floating pt values.
    boolean i = isnum("4.2");
    assertEqual(0, i, "i");

    boolean j = isnum(".77");
    assertEqual(0, j, "j");

    
}
