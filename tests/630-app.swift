#include <builtins.swift>

// SKIP-THIS-TEST

app (file out) f (file input, string s, int i) {
    blah @out "hello" @input (s + "232") (fromint(i));
}

main {
    file x = input_file("/dev/null");
    file y;
    y = f(x, "sfds", 1);
}
