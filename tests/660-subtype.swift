import assert;



// Check we can declare the type
type superint int;

// Convert outside of type system
(superint o) make_superint(int i) "turbine" "0.0.1" [
    "set <<o>> <<i>>" 
];

main {
    superint y = make_superint(1);
    int z = y;
}
