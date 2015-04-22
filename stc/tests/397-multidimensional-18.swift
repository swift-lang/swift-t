
// Regression test for compiler bug
// The output of this test can be inspected to make sure optimizer
// working correctly: the first loop set, where bounds can be found
// without function inlining, should be unrolled and all
// unnecessary operations eliminated.
// The second loop set should have the create_nested instruction hoisted
// out of the inner loop
main {
    int A[][];
    foreach i in [1:10] {
        foreach j in [1:10] {
            A[i][j] = i;
            A[i][j+10] = j;
            
        }
    }
    
    int B[][];
    foreach i in [1:id(10)] {
        foreach j in [1:id(10)] {
            B[i][j] = i;
            B[i][j+10] = j;
            
        }
    }
}

(int o) id (int i) {
    o = i;
}
