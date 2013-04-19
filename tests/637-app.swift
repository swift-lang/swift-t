// Test app with arrays expanding to command-line args


main {
  echo(["quick", "brown", "fox"]);
}

app () echo (string args[]) {
    "/bin/echo" args; 
}
