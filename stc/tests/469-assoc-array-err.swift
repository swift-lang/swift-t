
// Structs aren't valid array keys
type mystruct {
    int a;
    float b;
}

// THIS-TEST-SHOULD-NOT-COMPILE
main {
    string A[mystruct];

    mystruct key;
    key.a = 1;
    key.b = 1;

    A[key] = "test";
}
