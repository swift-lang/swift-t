
// Check relationship operators on strings

#include <builtins.swift>
#include <swift/assert.swift>

main {
    assert("hello" == "hello", "hello != hello");
    assert("hello" != "goodbye", "hello == goodbye");
}
