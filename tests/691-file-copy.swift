import files;
import assert;

main () {
    file a<"691-tmp.txt"> = write("contents.");

    // Check we can copy to mapped and unmapped files
    file b = a;

    assertEqual(read(b), "contents.", "b");
    file c<"691-tmp2.txt">;
    
    // unmapped to mapped
    c = copy_wrapper(a);

    // unmapped to unmapped
    file d = copy_wrapper(b);
    assertEqual(read(d), "contents.", "d");

    // mapped to mapped
    file e<"691-tmp3.txt"> = c;
    assertEqual(read(e), "contents.", "e");
}

(file o) copy_wrapper (file i) {
    // Don't know mapping status of these files
    o = i;
}
