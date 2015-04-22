import files;
import string;

main () {
    create_input() =>
    foreach i in [0:31] {
        /* Check that the combination of input_file and a mapped file
           induces a copy */
        file f<sprintf("692-out-%i.txt", i)> = input_file("692-in.txt");
    }
}

(void o) create_input() {
  file infile<"692-in.txt"> = write("692") =>
    o = make_void();
}
