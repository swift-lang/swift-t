// SKIP-THIS-TEST
// Work in progress on coasters

@dispatch=coasters
app () echo (string arg) {
  "echo" arg
}

@dispatch=coasters
app (file out) echo2 (string args[]) {
  "echo" args @stdout=out
}

main {
  echo("HELLO COASTERS");
}
