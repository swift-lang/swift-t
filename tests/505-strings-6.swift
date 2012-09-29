
// Check relationship operators on strings

#include <builtins.swift>
#include <assert.swift>

main {
    assert("hello" == "hello", "hello != hello");
    assert("hello" != "goodbye", "hello == goodbye");
}
