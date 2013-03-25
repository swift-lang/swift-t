
#include <builtins.swift>
#include <io.swift>

(string output)
echo_filename(file input) "funcs" "0.0"
[ "set <<output>> <<input>>" ];

// SKIP-THIS-TEST: Issue #453
// Issue is filename substitution into Tcl
// This works:  [ lindex <<input>> 0 ]

main {
  file f = input_file("alice.txt");
  string s = echo_filename(f);
  printf("filename: %s", s);
}
