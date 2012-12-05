#include <builtins.swift>
#include <files.swift>
#include <assert.swift>

// Test redirection

app (file out) echo (string arg) {
  "/bin/echo" arg @stdout=out; 
}

main () {
  string msg = "hello,world";
  file tmp = echo(msg);
  assertEqual(readFile(tmp), msg, "contents of tmp");
}
