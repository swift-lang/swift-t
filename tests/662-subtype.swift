#include <builtins.swift>
#include <files.swift>
#include <assert.swift>

type specialfile file;

app (specialfile out) echo (string s) {
    "/bin/echo" s @stdout=out
}

app (specialfile out) cp (specialfile i) {
   "/bin/cp" i out; 
}

main {
    specialfile x = echo("test");
    specialfile y = cp(x);

    file z = cp(y);
    wait (x,y,z) {
        assertEqual(readFile(x), "test\n", "x");
        assertEqual(readFile(y), "test\n", "z");
        assertEqual(readFile(z), "test\n", "z");
    }
}

