
// Regression test for octal escape code bug

#include <builtins.swift>

main {
    // Output hello world in red
    trace("\033[1;31m" + "hello world" + "\033[0m");
}
