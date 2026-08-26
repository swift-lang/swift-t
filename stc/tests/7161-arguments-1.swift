// Basic arguments() declaration: bind two positional command-line
// arguments to typed top-level variables.  Equivalent to
//   import sys;
//   string username = argp(1);
//   int    v        = string2int(argp(2));
// plus a check that no extra arguments were supplied.

arguments(string username, int v);

trace("hello " + username, " the value is ", v+1);
