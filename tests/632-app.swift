
/* Split file into head and tail */
app (file out1, file out2) split (file input, int lines) {
    "./632-split.sh" @input lines @out1 @out2;
}

main {
    // Test multiple outputs for app
    // Test autogeneration of file names
    file infile = input_file("lines.txt");
    file head, tail<"tail.txt">;
    head, tail = split(infile, 2);
}
