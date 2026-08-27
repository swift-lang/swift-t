// A flag with no default value is required, so it appears without
// brackets in the usage synopsis.

arguments(string username : "your name here");
flags(int a               : "the addend");

trace("hello " + username, " the value is ", a);
