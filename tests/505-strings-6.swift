#include "builtins.swift"
// Check relationship operators on strings

main {
    assert("hello" == "hello", "hello != hello");
    assert("hello" != "goodbye", "hello == goodbye");
}
