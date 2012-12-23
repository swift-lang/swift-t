#include <builtins.swift>
#include <files.swift>

main {
  foreach i in [1:3] {
    file f<"566.txt"> = writeFile("test");
  }
}
