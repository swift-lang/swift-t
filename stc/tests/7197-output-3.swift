// THIS-TEST-SHOULD-NOT-COMPILE
// A file argument is an output, written by the program, so reading one
// that the program never writes is the ordinary "never written" error.
// A file the program reads is declared input instead.

import files;

arguments(file data);

trace(read(data));
