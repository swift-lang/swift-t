// Test appending to bag in array

import io;

main {
    bag<int> A[];

    A[0] += 1;
    A[id(0)] += 2;
    A[1] += 3;
    A[id(1)] += 4;


    foreach bag, i in A {
     foreach elem in bag {
       printf("Elem@%i: %i", i, elem);
     }
   }


  // Create bag reference with lookup by future
  foreach elem in A[id(0)] {
    printf("XX Elem: %i", elem);
  }
  
  // Check we can dereference
  bag<int> b = A[id(1)];
  foreach elem in b { 
    printf("YY Elem: %i", elem);
  }
}

(int o) id (int i) {
    o = i;
}
