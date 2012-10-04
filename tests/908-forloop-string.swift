
// Check that string operations work ok in for loop

#include <builtins.swift>
#include <string.swift>

main {
  for (string s = "", int i = 0; i < 10;
       i = i + 1,
       s = sprintf("%s %i", s, i))
  {
    trace(i,s);
  }
}
