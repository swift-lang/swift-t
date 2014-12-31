import string;

// See issue 
// THIS-TEST-SHOULD-NOT-COMPILE

main () {
    foreach i in [0:31]{
        file f<sprintf("f-%i.txt", i)> = input_file("/dev/null");
    }
}

