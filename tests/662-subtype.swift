import files;
import assert;

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
        assertEqual(read(x), "test\n", "x");
        assertEqual(read(y), "test\n", "z");
        assertEqual(read(z), "test\n", "z");
    }
}

