// Automatic usage message: a documentation string may be attached to any
// argument with a colon, and the pseudo-entry "description" documents the
// program as a whole.  STC renders the whole usage message at compile time;
// the generated program prints it and exits when run with -h or --help.

arguments(string username : "your name here",
          int v           : "the value to increment",
          description     : "increment the value for the user");
flags(boolean emphasize   : "be enthusiastic",
      int a=0             : "the addend");

trace("hello " + username, " the value is ", v+a);
if (emphasize) {
   trace("hurrah");
}
