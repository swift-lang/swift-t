// Basic flags() declaration: bind flagged command-line arguments to
// typed top-level variables.  Equivalent to
//   import sys;
//   boolean emphasize = argv_contains("emphasize");
//   int     a         = string2int(argv("a", "0"));
//   argv_accept("emphasize", "a");
// A boolean flag is true when the flag is given and false when it is not,
// so it never takes a value and never takes a default.

arguments(string username, int v);
flags(boolean emphasize, int a = 0);

trace("hello " + username, " the value is ", v+a);
if (emphasize) {
  trace("hurrah");
}
