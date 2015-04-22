import files;
import string;
import io;
import sys;

@dispatch=coasters
app (file out, file err) bashing (file foo) {
    "/bin/bash" foo @stderr=err @stdout=out;
}


/**
 * Test coaster output file creation and passing arguments functionality
 * Test capability to run multiple jobs (this should just work)
 * Test capability to pass files as args
 */
main(){

    argv_accept("d");
    string dir = argv("d");

    file script = input_file(strcat(dir, "/wrapper.sh"));
    file f_out<strcat(dir, "test5.0.out")>;
    file f_err<strcat(dir, "test5.0.err")>;
    (f_out, f_err) = bashing (script);
}
