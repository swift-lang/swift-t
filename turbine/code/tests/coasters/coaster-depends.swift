import files;
import string;
import io;
import sys;


@dispatch=coasters
app (file out, file err) bashing (file foo) {
    "/bin/bash" foo @stderr=err @stdout=out;
}


@dispatch=coasters
app (file out, file err) wc (file foo) {
   "/usr/bin/wc" foo @stderr=err @stdout=out;
}

/**
 * Test multiplexing
 */
main(){

    argv_accept("d");
    string dir = argv("d");

    file script = input_file(strcat(dir, "/wrapper.sh"));
    file array[];
    foreach index in [0:3] {
        file f_out <sprintf("%s/multiplex-test%i.out", dir, index)>;
        file f_err <sprintf("%s/multiplex-test%i.err", dir, index)>;
        (f_out, f_err) = bashing (script);
        array[index] = f_out;
    }

    foreach f,i  in array {
        file f_o <sprintf("%s/depend%i.out", dir, i)>;
        file f_e <sprintf("%s/depend%i.err", dir, i)>;
        (f_o, f_e) = wc(f);
    }

}
