import assert;

type arrayT int[string];


main {
    arrayT A;

    A["one"] = 1;
    A["two"] = 2;

    assertEqual(A["one"], 1, "one");
    assertEqual(A["two"], 2, "two");
}
