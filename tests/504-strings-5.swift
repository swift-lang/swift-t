
// Test out some more string functions

#include <builtins.swift>
#include <assert.swift>
#include <string.swift>

main {
    // check that + performs strcat
    trace("hello" + " " + "world");
    assertEqual("one " + "two " + "three", "one two three", "");


    string x = id("two ");
    wait (x) {
        assertEqual("one " + x + "three", "one two three", "local version");
    }
}

(string o) id (string i) {
    o = i;
}
