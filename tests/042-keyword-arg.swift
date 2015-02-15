import assert;

(string o) hello(string name="<name missing>", string greeting="Hello") {
  o = greeting + " " + name + "!";
}

assertEqual(hello(), "Hello <name missing>!", "not provided");
assertEqual(hello("x"), "Hello x!", "positional");
assertEqual(hello(name="y"), "Hello y!", "keyword");
assertEqual(hello(greeting="Hi"), "Hi <name missing>!", "second keyword only");
assertEqual(hello(greeting="Hi", name="z"), "Hi z!", "both keywords");
assertEqual(hello("x", greeting="Hi"), "Hi x!", "pos and keyword");

