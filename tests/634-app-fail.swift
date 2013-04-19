
// THIS-TEST-SHOULD-NOT-RUN

// This shell script returns a failure return code
// Check that error is caught
app (file out) f () {
    "./634-fail-app.sh" @out;
}

main {
    file x <"outfile"> = f();
    wait (x) {
        trace("DONE");
    }
}
