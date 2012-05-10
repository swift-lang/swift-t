
#include <builtins.swift>
#include <swift/assert.swift>
#include <swift/string.swift>

main {
    string x = "hello world\n";
    trace(x);
    assertEqual(x, "hello world\n","");
    trace("goodbye\tworld\n");
    trace(strcat("hello", "goodbye"));
    assertEqual(strcat("hello","goodbye"), "hellogoodbye","");
    trace("hello", "goodbye");
}
