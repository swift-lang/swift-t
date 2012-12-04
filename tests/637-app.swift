// Test app with arrays expanding to command-line args

#include <builtins.swift>

main {
  echo(["quick", "brown", "fox"]);
}

app () echo (string args[]) {
    "/bin/echo" args; 
}
