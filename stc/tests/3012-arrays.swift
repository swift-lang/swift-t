
import assert;

// Regression test for rontainer reference bugs

main {
    int array[];

    array[2] = 5;

    int x;
    x = array[2];

    int y;
    y = 2 + array[2];
    assertEqual(y, 7, "y=7");
}
