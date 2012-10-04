
#include <builtins.swift>
#include <io.swift>
#include <sys.swift>

main {
  // Note that argv("N") = ""
  // int N = toint(argv("N"));
  int N = toint("");
  int V = N-1;
  printf("V: %i", V);
}
