import string;
import files;
import io;
import sys;
@dispatch=coasters
app (file out) hello (){
    "/bin/echo" @stdout=out;
}

@dispatch=coasters
app (file out) hello_coaster (){
    "/bin/echo" @stdout=out;
}

/**
 * Tests only the basic creation of a coaster worker
 */
main()
{
    argv_accept("d");
    string dir = argv("d");

    file f_out1 <sprintf("%s/coaster-sanity%i.out", dir, 1)>;
    file f_out2 <sprintf("%s/coaster-sanity%i.out", dir, 2)>;

    f_out1 = hello();
    f_out2 = hello_coaster();
}
