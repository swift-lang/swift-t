import files;
import string;
import io;
import sys;

@dispatch=coasters
app (file out, file err) date () {
    "/bin/date" @stderr=err @stdout=out;
}

/**
 * Test coaster output file location functionality
 */
main()
{
    argv_accept("d");
    string dir = argv("d");

    file f_out<strcat(dir, "/test2.out")>;
    file f_err<strcat(dir, "/test2.err")>;
    (f_out, f_err) = date();
}
