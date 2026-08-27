// Documentation strings are stripped before the declaration is expanded,
// so a documented program runs exactly like an undocumented one.

arguments(string username : "your name here",
          int v           : "the value to increment",
          description     : "increment the value for the user");
flags(bool emphasize      : "be enthusiastic",
      int a=0             : "the addend");

trace("hello " + username, " the value is ", v+a);
if (emphasize) {
   trace("hurrah");
}
