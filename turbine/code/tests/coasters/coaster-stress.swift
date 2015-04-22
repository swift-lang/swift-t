import files;
import string;
import io;
import sys;


@dispatch=coasters
app (file out, file err) bashing (file foo) {
    "/bin/bash" foo @stderr=err @stdout=out;
}

/**
 * Test multiplexing
 */
main(){

    argv_accept("d");
    string dir = argv("d");

    file script = input_file(strcat(dir, "/wrapper.sh"));
    foreach index in [0:1000]
    {
        file f_out <sprintf("%s/stress/stress%i.out", dir, index)>;
        file f_err <sprintf("%s/stress/stress%i.err", dir, index)>;
        (f_out, f_err) = bashing (script);
    }
}
