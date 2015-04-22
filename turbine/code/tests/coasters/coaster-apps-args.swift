import files;
import string;
import io;
import sys;

@dispatch=coasters
app (file out, file err) host () {
    "/bin/hostname" @stderr=err @stdout=out;
}

@dispatch=coasters
app (file out, file err) hostf (string arg) {
    "/bin/hostname" arg @stderr=err @stdout=out;
}


/**
 * Test coaster output file creation and passing arguments functionality
 */
main(){
    string msg = "-f";

    argv_accept("d");
    string dir = argv("d");

    file f_out<strcat(dir,"/test-3.0.out")>;
    file f_err<strcat(dir,"/test-3.0.err")>;
    (f_out, f_err) = host ();

    file g_out<strcat(dir, "/test-3.1.out")>;
    file g_err<strcat(dir, "/test-3.1.err")>;
    (g_out, g_err) = hostf (msg);
}
