
import assert;
import string;

main {
    string x = "121";  

    boolean a = isint("011");
    assertEqual(true, a, "a");

    boolean b = isint("4242");
    assertEqual(true, b, "b");

    boolean c = isint("9223372036854775807");
    assertEqual(true, c, "c");
    
    boolean c2 = isint("-9223372036854775808");
    assertEqual(true, c2, "c2");
    
    boolean d = isint(" 42");
    assertEqual(true, d, "d");
    
    boolean e = isint("42 ");
    assertEqual(true, e, "e");

    boolean e1 = isint(x);
    assertEqual(true, e1, "e1");

    // Fail cases 

    boolean f = isint("4 2");
    assertEqual(false, f, "f");

    boolean g = isint("a1");
    assertEqual(false, g, "g");

    boolean h = isint("");
    assertEqual(false, h, "h");

    // isint will not accept floating pt values.
    boolean i = isint("4.2");
    assertEqual(false, i, "i");

    boolean j = isint(".77");
    assertEqual(false, j, "j");

    
}
