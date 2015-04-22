import assert;

(string o) hello(string name="<name missing>") {
  o = "Hello " + name + "!";
}

assertEqual(hello(), "Hello <name missing>!", "not provided");
assertEqual(hello("x"), "Hello x!", "positional");
assertEqual(hello(name="y"), "Hello y!", "keyword");
