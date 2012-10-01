#include <builtins.swift>

app (file out) f (file input, string s, int i) {
    "./631-app-cat.sh" @input @out ("hello " + s) i;
}

main {
    file x = input_file("helloworld.txt");
    file y <"631-outfile.txt">;
    y = f(x, "sfds", 1);
    wait (y) {
        trace("DONE");
    }
}
