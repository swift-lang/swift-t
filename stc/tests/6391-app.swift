import files;
import assert;

// Test redirection

// Loop string through file
(string o) id (string i) {
  o = read(cat(write(i)));
}

app (file out) cat (file inp) {
  "/bin/cat" @stdin=inp @stdout=out;
}

main () {
  foreach s in ["one", "two", "three", "four"] {
    string s2 = id(s);
    assertEqual(s2, s, "'" + s + "'" + " != " + "'" + s2 + "'");
  }
}

