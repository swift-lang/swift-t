/* Test that parseInt works as expected */
import assert;

assertEqual(parseInt("10"), 10, "no base");

assertEqual(parseInt("11", 7), 8, "base 7");

assertEqual(parseInt("11111", 2), 31, "base 2");

assertEqual(parseInt("ff", 16), 255, "base 16");
