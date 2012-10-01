#include <builtins.swift>
// SdKIP-THIS-TEST

app () f (file input, string s, int i) {
    "cat" @input;
}

main {
    file x = input_file("630-helloworld.txt");
    f(x, "Bob", 1);
}
