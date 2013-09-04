

main {
  echo(["quick", "brown", "fox"]);

  echo2([["lazy"], ["dog"]]);
}

// Test app with arrays expanding to command-line args
app () echo (string args[]) {
    "/bin/echo" args; 
}

(int o) id (int i) {
    o = i;
}

// Test app with expression of reference type on command line
app () echo2 (string args[][]) {
    // args[id(0)] internally will have type of *(string[])
    "/bin/echo" "[" (args[id(0)]) (args[id(1)]) "]";
}
