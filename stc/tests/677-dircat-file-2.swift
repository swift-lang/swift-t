
// The directory catenation operator (/) over files that are bound, as
// opposed to the unwritten mapped files of 676-dircat-file: the operand
// contributes its filename either way, so the results are the same shape.

import files;

file d = directory("677-dircat-file-2.dir");
file f = input("677-dircat-file-2.leaf");
s = "sub";

// file/string, file/string, string/file, file/file
trace(d/"file.txt", d/s, "top"/f, d/f);
