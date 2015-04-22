import assert;

assertEqual(toString(true), "true", "true");
assertEqual(toString(false), "false", "false");

assertEqual(toString(-12), "-12", "-12");

assertEqual(toString(1.234), "1.234", "1.234");

import string;
assertEqual(toUpper("aBcD"), "ABCD", "toUpper");
assertEqual(toLower("aBcD"), "abcd", "toLower");
