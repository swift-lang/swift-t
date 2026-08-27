// main() parameters may be documented the same way as arguments().

main (string name : "who to greet",
      int n       : "how many times") {
  foreach i in [1:n] {
    trace("hello " + name);
  }
}
