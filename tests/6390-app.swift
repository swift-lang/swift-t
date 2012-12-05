#include <builtins.swift>
#include <files.swift>

// Test redirection

// SKIP-THIS-TEST

app (file out) echo (string arg) {
  "/bin/echo" arg @stdout=out; 
}

main () {
  string msg = "hello,world";
  file tmp = echo(msg);
  assertEqual(readFile(tmp), msg, "contents of tmp");
}
