

type tt {
    int x;
    int y;
}

type ttt {
    tt X;
    int y;
}

main {
    // Test copying parts of struct
    tt A;
    ttt B;
    ttt C;

    C = B;

    B.X = A;
    B.y = A.x;


    A.x = 2;
    A.y = 3;

    trace(C.X.x, C.X.y, C.y);
}
