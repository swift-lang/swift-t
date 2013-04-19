// Test app with arrays expanding to command-line args


main {
  void done = echo("the", ["quick", "brown", "fox"], [input_file("jumped"),
        input_file("over"), input_file("the"), input_file("lazy"),
        input_file("dog")]);
  wait (done) {
    trace("DONE");
  }
}

app (void signal) echo (string arg, string args[], file args2[]) {
    "/usr/bin/env" "echo" arg args args2; 
}
