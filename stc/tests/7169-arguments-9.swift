// Literal defaults on main() parameters, and a parameter read from a
// function other than main -- the expansion declares ordinary top-level
// variables, which are visible throughout the program.

import io;

greet(string who, int n) {
  printf("hello %s %i", who, n);
}

main(string username, int v = 41, float f = 2.5, boolean b = false) {
  greet(username, v+1);
  printf("f=%.1f b=%s", f, b);
}
