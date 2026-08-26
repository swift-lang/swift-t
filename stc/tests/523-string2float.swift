/* Test that string2float accepts the forms it should */
import assert;

assertEqual(string2float("2.5"),   2.5,     "decimal");
assertEqual(string2float("-1.5"), -1.5,     "negative");
assertEqual(string2float("7"),     7.0,     "integer form");
assertEqual(string2float("1e-6"),  0.000001, "scientific");
assertEqual(tofloat("3.25"),       3.25,    "tofloat alias");
assertEqual(parseFloat("4.75"),    4.75,    "parseFloat alias");
