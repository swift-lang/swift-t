#include <builtins.swift>
#include <assert.swift>

@dispatch=LEAF
(int o) double(int i) [
  "set <<o>> [ expr <<i>> * 2 ]"
];


main {
    int i = 1;
    trace(double(double(double(i))));
    
    assertEqual(double(double(double(i))), 8, "");
}
