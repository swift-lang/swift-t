
// THIS-TEST-SHOULD-NOT-RUN

// Check that bad executable
app (file out) f () {
    "sdfdsfdskwejiojwet";
}

main {
    file x <"outfile"> = f();
    wait (x) {
        trace("DONE");
    }
}
