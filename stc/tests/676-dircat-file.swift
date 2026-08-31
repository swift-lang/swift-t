
// UNSET-VARIABLE-EXPECTED

// Tests the directory catenation operator (/) applied to files.
// A file operand contributes its filename; the result is a string.

// Both operands here are mapped files that nothing writes: catenation reads
// only the name, which a mapping supplies before anything is produced, so a
// path under an output directory can be named before the directory exists.
// The two files are therefore still unset when the program ends, which stc
// warns about and the runtime reports at -O2, hence the annotation above.
// See 677-dircat-file-2 for the same operators over files that are bound.
file d<"dir_676">;
file f<"leaf.txt">;
s = "sub";

// file/string, file/string, string/file, file/file
trace(d/"file.txt", d/s, "top"/f, d/f);
