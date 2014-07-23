import files;
import io;
import string;
import sys;

@dispatch=coasters
app (file out, file err) env () {
    "/usr/bin/env X=x" @stderr=err @stdout=out;
}

@dispatch=coasters
app (file out, file err) test () {
    "/bin/hostname" @stderr=err @stdout=out;
}

/**
 * Test coaster env variable functionality
 * Sadly the coasters system does not like env variable prepended to the
 * front of the executable that it expected the cmd varible to be
 */
main(){

    argv_accept("d");
    string dir = argv("d");

    file f_out<strcat(dir,"/f4.out")>;
    file f_err<strcat(dir,"/f4.err")>;
    (f_out, f_err) = test ();
}
