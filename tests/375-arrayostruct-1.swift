// parser can't handle struct refs yet

type mystruct {
    int a;
    int b;
}

(int ret) f (int recs) {
    if (recs) {
        ret = f(recs - 1);
    } else {
        ret = 0;
    }
}

main {
    mystruct bigarray[];

    mystruct tmp1;
    tmp1.a = 1;
    tmp1.b = 2;

    bigarray[0] = tmp1;
    
    mystruct tmp2;
    tmp2.a = 1;
    // forgot to assign b - may cause warning but not error
    // UNSET-VARIABLE-EXPECTED
    bigarray[1] = tmp2;

    trace(bigarray[f(2)].a, bigarray[0].b, bigarray[f(2)+1].a);
}
