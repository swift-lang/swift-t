
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
    othermem = A[f()];
}


(int r) f () {
  r = 0;
}
