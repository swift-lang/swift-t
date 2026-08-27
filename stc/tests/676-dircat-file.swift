
// Tests the directory catenation operator (/) applied to files.
// A file operand contributes its filename; the result is a string.

file d<"dir_676">;
file f<"leaf.txt">;
s = "sub";

// file/string, file/string, string/file, file/file
trace(d/"file.txt", d/s, "top"/f, d/f);
