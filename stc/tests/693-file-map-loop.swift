
app (file out) runcommand (string instr){
   "/bin/sh" "-c" instr @stdout=out;
}

import string;

main () {
    foreach i in [0:31]{
        file f<sprintf("f-%i.txt", i)> = runcommand("echo hello world");
    }
}

