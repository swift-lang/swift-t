// Test app array expansion with multi-d arrays

#include <builtins.swift>
// SKIP-THIS-TEST

main {
  void done = echo([["one", "two"], ["three"], [], ["four", "five"]]);
  wait (done) {
    trace("DONE");
  }
}

app (void signal) echo (string args[][]) {
    "/usr/bin/env" "echo" args; 
}
