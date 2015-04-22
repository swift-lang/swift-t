import assert;


// THIS-TEST-SHOULD-NOT-COMPILE
type superint int;

main {
    int x = 0;
    superint y = x; // Can't promote int to superint
}
