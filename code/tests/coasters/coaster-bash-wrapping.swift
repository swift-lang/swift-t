import files;

app (file out, file err) arg_pass (string arg) {
    "coaster/bin/hostname" arg @stderr=err @stdout=out;
}

app (file out, file err) bashing (file foo) {
    "coaster/bin/bash" foo @stderr=err @stdout=out;
}


/**
 * Test coaster output file creation and passing arguments functionality
 * Test capability to run multiple jobs (this should just work)
 * Test capability to pass files as args
 */
main(){
    string msg = "-f";
    file f_out<"/homes/yadunand/bin/exm-trunk/sfw/turbine/trunk/code/tests/coasters/test5.0.out">;
    file f_err<"/homes/yadunand/bin/exm-trunk/sfw/turbine/trunk/code/tests/coasters/test5.0.err">;
    (f_out, f_err) = arg_pass(msg);


    //Note: Should use only full file paths
    file script = input_file("/homes/yadunand/bin/exm-trunk/sfw/turbine/trunk/code/tests/coasters/wrapper.sh");
    file g_out<"/homes/yadunand/bin/exm-trunk/sfw/turbine/trunk/code/tests/coasters/test5.1.out">;
    file g_err<"/homes/yadunand/bin/exm-trunk/sfw/turbine/trunk/code/tests/coasters/test5.1.err">;
    (g_out, g_err) = bashing (script);
}
