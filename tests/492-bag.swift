// SKIP-THIS-TEST
// unimplemented feature: append to bag in array

import io;

main {
    bag<int> A[];

    A[0] += 1;
    A[id(0)] += 2;
    A[1] += 3;
    A[id(1)] += 4;


    // TODO: check results
    foreach bag, i in A {
     foreach elem in bag {
       printf("Elem@%i: %i", i, elem);
     }
    }
}

(int o) id (int i) {
    o = i;
}
