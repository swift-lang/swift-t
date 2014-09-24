
// SKIP-THIS-TEST

import assert;
import files;

// Coaster app functions

@dispatch=coaster
app () echo (string arg) {
  "echo" arg
}

@dispatch=coaster
app (file out) echo2 (string args[]) {
  "echo" args @stdout=out
}

main {
  echo("HELLO COASTERS");


  string x = read(echo2(["hello", "world"]));
  assertEqual(x, "hello world\n", "echo2");
}
