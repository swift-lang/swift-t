import files;

app (file out, file err) host () {
    "coaster/bin/hostname" @stderr=err @stdout=out;
}

app (file out, file err) hostf (string arg) {
    "coaster/bin/hostname" arg @stderr=err @stdout=out;
}


/**
 * Test coaster output file creation and passing arguments functionality
 */
main(){
    string msg = "-f";
    file f_out<"/homes/yadunand/bin/exm-trunk/sfw/turbine/trunk/code/tests/coasters/test-3.0.out">;
    file f_err<"/homes/yadunand/bin/exm-trunk/sfw/turbine/trunk/code/tests/coasters/test-3.0.err">;
    (f_out, f_err) = host ();

    file g_out<"/homes/yadunand/bin/exm-trunk/sfw/turbine/trunk/code/tests/coasters/test-3.1.out">;
    file g_err<"/homes/yadunand/bin/exm-trunk/sfw/turbine/trunk/code/tests/coasters/test-3.1.err">;
    (g_out, g_err) = hostf (msg);
}
