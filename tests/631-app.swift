
app (file out) f (file inp, string s, int i) {
    "./631-app-cat.sh" @inp @out ("hello " + s) i;
}

main {
    file x = input_file("helloworld.txt");
    file y <"631-outfile.txt">;
    y = f(x, "some text", 1);
    wait (y) {
        trace("DONE");
    }
}
