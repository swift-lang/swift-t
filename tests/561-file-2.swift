import assert;

main {
    // Check files in array
    file files[];
    files[0] = input_file("alice.txt");
    files[1] = input_file("joe.txt");
    files[2] = files[1];
    file outfile1 <"561-out1.txt"> = files[0];
    file outfile2 <"561-out2.txt"> = files[2];

    assertEqual(filename(outfile1), "561-out1.txt", "filename outfile1");
    assertEqual(filename(outfile2), "561-out2.txt", "filename outfile2");
}

