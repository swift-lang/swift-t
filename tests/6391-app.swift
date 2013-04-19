import files;
import assert;

// Test redirection

// Loop string through file
(string o) id (string i) {
  o = readFile(cat(writeFile(i)));
}

app (file out) cat (file input) {
  "/bin/cat" @stdin=input @stdout=out; 
}

main () {
  foreach s in ["one", "two", "three", "four"] {
    string s2 = id(s);
    assertEqual(s2, s + "\n", "'" + s + "'" + " != " + "'" + s2 + "'");
  }
}

