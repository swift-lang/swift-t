// Test app array expansion with multi-d arrays


main {
  void done = echo([["one", "two"], ["three"], [], ["four", "five"]]);
  wait (done) {
    trace("DONE1");
  }

  void done2 = echo2([
      [
        ["ein"], ["zwei", "drei"], ["vier"]
      ],
      f3([ ["fuenf", f("sechs")] ]),
      [ f2(["sieben"]) ],
      [ [] ],
      [
        [f("acht")], ["neun"], ["zehn"]
      ]]);
  wait (done2) {
    trace("DONE2");
  }
}
app (void signal) echo (string args[][]) {
    "/usr/bin/env" "echo" args; 
}

app (void signal) echo2 (string args[][][]) {
    "/usr/bin/env" "echo" args; 
}

(string o) f (string i) {
  o = i;
}

(string o[]) f2 (string i[]) {
  o = i;
}

(string o[][]) f3 (string i[][]) {
  o = i;
}
