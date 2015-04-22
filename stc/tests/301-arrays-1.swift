
import assert;

main {
    // check declaration syntax
    int a[];
    int anotherarray [ ];
    int oarray[ ];
    int obarray [];
    int array[];

    int twodee[][];

    a[1] = 4;

    array[2] = 5;

    int x;
    x = array[2];

    array[1] = 2;
    int y;
    y = array[1] + array[2];
    assertEqual(y, 7, "y=7");
}
