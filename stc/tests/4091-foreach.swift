import io;
import sys;
import string;

main {
    file X[] = [ input("4091-A"), input("4091-B") ];
    string Y[];
    wait (sleep(0.0)) {
      Y = split("one\ntwo","\n");
    }

    foreach x, i in X {
        foreach y, j in Y {
            printf("%s %s", filename(x), y);
        }
    }
}

