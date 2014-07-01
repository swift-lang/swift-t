import string;

// SKIP-THIS-TEST
// See issue 
// THIS-TEST-SHOULD-NOT-RUN

main () {
    foreach i in [0:31]{
        file f<sprintf("f-%i.txt", i)> = input_file("/dev/null");
    }
}

