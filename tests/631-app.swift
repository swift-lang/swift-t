#include <builtins.swift>

// SKIP-THIS-TEST
app (file out) f (file input, string s, int i) {
    "630-app-cat.sh" @input @out ("hello " + s) i;
}

main {
    file x = input_file("helloworld.txt");
    file y <"630-outfile.txt">;
    y = f(x, "sfds", 1);
    wait (y) {
        trace("DONE");
    }
}
