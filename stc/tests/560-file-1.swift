import assert;

main {
    file infile = input_file("alice.txt");
    file outfile <"bob.txt"> = infile;

    assertEqual(filename(infile), "alice.txt", "filename infile");
    assertEqual(filename(outfile), "bob.txt", "filename outfile");
}
    
file infile = input_file("alice.txt");
file outfile <"bob2.txt"> = infile;

assertEqual(filename(infile), "alice.txt", "filename infile");
assertEqual(filename(outfile), "bob2.txt", "filename outfile");
