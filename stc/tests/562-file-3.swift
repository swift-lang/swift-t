

(file o) q () {
    o = input_file("alice.txt");
}

main {
    file f = q();

    // Test waiting on file
    wait (f) {
        trace(filename(f));
    }
}
