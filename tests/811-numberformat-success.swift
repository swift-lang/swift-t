import io;
import assert;
import sys;

main {
  check_leading_zeroes();
  check_64bit();
  check_whitespace();
}

check_leading_zeroes() {
  // Check that it isn't interpreted as octal
  // Check that leading zeroes don't show up in trace
  string x = "0090";
  int y = toint(x);
  trace("Leading zeroes:",y);
  
  // Should also work with negative numbers
  string x2 = "-007";
  int y2 = toint(x2);
  trace("Leading zeroes neg:",y2);

  string x3 = "00";
  int y3 = toint(x3);
  trace("Zero:",y3);
}

check_64bit() {
  // Check integers that don't fit in 64 bits handled right
  string bigS = "100000000000";
  int bigI = 100000000000;
  assertEqual(bigI, toint(bigS), "big positive integer");
  
  string bigSN = "-100060040200";
  int bigIN = -100060040200;
  assertEqual(bigI, toint(bigS), "big negative integer");
}

check_whitespace() {
  // Check that whitespace is ignored
  assertEqual(12345, toint(" 12345"), "whitespace1");
  assertEqual(12345, toint(" \t12345\n "), "whitespace2");
  assertEqual(12345, toint("12345\n"), "whitespace3");
}
