import assert;

main {
  trace("%i %.2f %s" % (1, 3.142, "Hello World"));

  // Integers
  assertEqual("%i %02d" % (123, 2), "123 02", "Integers");

  // Floats
  assertEqual("%9.2f" % 1.1151, "     1.12", "Non-tuple arg float");
  
  x = 0.5;
  // Tcl format defaults to rounding to 6 decimal places
  assertEqual("%f %.2f" % (x, x), "0.500000 0.50", "Exact float");

  // Strings
  assertEqual("%2s|%10s" % ("Hello", "World"), "Hello|     World",
              "Fixed width string");

  // Unicode - basic multilingual plan characters
  assertEqual("%c%2c" % (12353 /*0x3041*/, 916/*0x0394*/),
               "ぁ Δ", "Unicode basic multilingual characters");

  // Hex/octal
  assertEqual("%o %x %X" % (8, 15, 15), "10 f F", "hex/octal");

  // % sign
  assertEqual("%d%%" % 100, "100%", "% sign");
}
