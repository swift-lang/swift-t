#include <builtins.swift>
//SKIP-THIS-TEST

type X {
    int x;
    int y;
}

main {
    X A[];
    X mem;
    mem.x = 1;
    mem.y = 2;
    A[0] = mem;


    X othermem;
    // Parser doesn't support this yet.
    othermem = A[f()];
}


(int r) f () {
  r = 0;
}
