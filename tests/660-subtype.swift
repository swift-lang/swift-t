#include <builtins.swift>
#include <assert.swift>



// Check we can declare the type
type superint int;

// Convert outside of type system
(superint o) make_superint(int i) [
    "set <<o>> <<i>>" 
];

main {
    superint y = make_superint(1);
    int z = y;
}
