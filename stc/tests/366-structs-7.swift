

type tt {
    int x;
}

(tt ret) f () {
    ret.x = 1;
}

main {
    trace(f().x);
}
